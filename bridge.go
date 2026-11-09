package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	pb "ai.pleos/connect-web-console/host-display-bridge/internal/emulatorpb"
	"google.golang.org/grpc"
	"google.golang.org/protobuf/types/known/emptypb"
)

const (
	defaultDisplaySlot  = uint32(2)
	defaultDisplayFlags = uint32(1737)
	requestTimeout      = 10 * time.Second
)

type emulatorClient interface {
	GetDisplayConfigurations(context.Context, *emptypb.Empty, ...grpc.CallOption) (*pb.DisplayConfigurations, error)
	SetDisplayConfigurations(context.Context, *pb.DisplayConfigurations, ...grpc.CallOption) (*pb.DisplayConfigurations, error)
}

type bridge struct {
	client emulatorClient
	slot   uint32
}

type displayRequest struct {
	Width       uint32 `json:"width"`
	Height      uint32 `json:"height"`
	DPI         uint32 `json:"dpi"`
	Flags       uint32 `json:"flags,omitempty"`
	Orientation string `json:"orientation,omitempty"`
}

type displayResponse struct {
	OK          bool   `json:"ok"`
	Available   bool   `json:"available"`
	Created     bool   `json:"created"`
	DisplaySlot uint32 `json:"display_slot"`
	DisplayName string `json:"display_name"`
	Width       uint32 `json:"width"`
	Height      uint32 `json:"height"`
	DPI         uint32 `json:"dpi"`
	Flags       uint32 `json:"flags,omitempty"`
	Orientation string `json:"orientation"`
	Message     string `json:"message,omitempty"`
	Error       string `json:"error,omitempty"`
}

func (b *bridge) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", b.health)
	mux.HandleFunc("GET /api/window-policy/display", b.getDisplay)
	mux.HandleFunc("PUT /api/window-policy/display/create", b.createDisplay)
	mux.HandleFunc("PUT /api/window-policy/display/remove", b.removeDisplay)
	return loopbackCORS(mux)
}

func (b *bridge) health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), requestTimeout)
	defer cancel()
	_, err := b.client.GetDisplayConfigurations(ctx, &emptypb.Empty{})
	if err != nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{
			"ok": false, "error": "emulator_unavailable", "message": err.Error(),
		})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (b *bridge) getDisplay(w http.ResponseWriter, r *http.Request) {
	configurations, err := b.getConfigurations(r.Context())
	if err != nil {
		writeUnavailable(w, b.slot, err)
		return
	}
	if configurations.GetUserConfigurable() != 0 && b.slot > configurations.GetUserConfigurable() {
		writeError(w, http.StatusConflict, b.slot, "display_not_configurable",
			fmt.Sprintf("display slot %d exceeds Emulator user-configurable limit %d",
				b.slot, configurations.GetUserConfigurable()))
		return
	}
	writeJSON(w, http.StatusOK, responseForDisplay(b.slot, findDisplay(configurations, b.slot), true, ""))
}

func (b *bridge) createDisplay(w http.ResponseWriter, r *http.Request) {
	var request displayRequest
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64*1024))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&request); err != nil {
		writeError(w, http.StatusBadRequest, b.slot, "invalid_request", err.Error())
		return
	}
	if err := normalizeAndValidateRequest(&request); err != nil {
		writeError(w, http.StatusBadRequest, b.slot, "invalid_request", err.Error())
		return
	}

	configurations, err := b.getConfigurations(r.Context())
	if err != nil {
		writeUnavailable(w, b.slot, err)
		return
	}
	if configurations.GetUserConfigurable() != 0 && b.slot > configurations.GetUserConfigurable() {
		writeError(w, http.StatusConflict, b.slot, "display_not_configurable",
			fmt.Sprintf("display slot %d exceeds Emulator user-configurable limit %d",
				b.slot, configurations.GetUserConfigurable()))
		return
	}
	if existing := findDisplay(configurations, b.slot); existing != nil {
		writeJSON(w, http.StatusConflict,
			responseForDisplay(b.slot, existing, false, "display_already_exists"))
		return
	}

	requestConfigurations := configurableDisplayConfigurations(configurations)
	requestConfigurations.Displays = append(requestConfigurations.Displays, &pb.DisplayConfiguration{
		Display: b.slot,
		Width:   request.Width,
		Height:  request.Height,
		Dpi:     request.DPI,
		Flags:   request.Flags,
	})
	updated, err := b.setConfigurations(r.Context(), requestConfigurations)
	if err != nil {
		writeError(w, http.StatusBadGateway, b.slot, "create_failed", err.Error())
		return
	}
	created := findDisplay(updated, b.slot)
	if created == nil {
		writeError(w, http.StatusBadGateway, b.slot, "create_failed", "emulator did not return DISPLAY2")
		return
	}
	writeJSON(w, http.StatusOK, responseForDisplay(b.slot, created, true, "DISPLAY2 created"))
}

func (b *bridge) removeDisplay(w http.ResponseWriter, r *http.Request) {
	configurations, err := b.getConfigurations(r.Context())
	if err != nil {
		writeUnavailable(w, b.slot, err)
		return
	}
	if findDisplay(configurations, b.slot) == nil {
		writeJSON(w, http.StatusConflict,
			responseForDisplay(b.slot, nil, false, "display_does_not_exist"))
		return
	}

	requestConfigurations := configurableDisplayConfigurations(configurations)
	displays := requestConfigurations.Displays[:0]
	for _, display := range requestConfigurations.Displays {
		if display.GetDisplay() != b.slot {
			displays = append(displays, display)
		}
	}
	requestConfigurations.Displays = displays
	updated, err := b.setConfigurations(r.Context(), requestConfigurations)
	if err != nil {
		writeError(w, http.StatusBadGateway, b.slot, "remove_failed", err.Error())
		return
	}
	if findDisplay(updated, b.slot) != nil {
		writeError(w, http.StatusBadGateway, b.slot, "remove_failed", "emulator still reports DISPLAY2")
		return
	}
	writeJSON(w, http.StatusOK, responseForDisplay(b.slot, nil, true, "DISPLAY2 removed"))
}

func (b *bridge) getConfigurations(parent context.Context) (*pb.DisplayConfigurations, error) {
	ctx, cancel := context.WithTimeout(parent, requestTimeout)
	defer cancel()
	return b.client.GetDisplayConfigurations(ctx, &emptypb.Empty{})
}

func (b *bridge) setConfigurations(parent context.Context, configurations *pb.DisplayConfigurations) (*pb.DisplayConfigurations, error) {
	ctx, cancel := context.WithTimeout(parent, requestTimeout)
	defer cancel()
	return b.client.SetDisplayConfigurations(ctx, configurations)
}

func normalizeAndValidateRequest(request *displayRequest) error {
	if request.Width == 0 || request.Height == 0 {
		return errors.New("width and height must be positive")
	}
	if request.DPI < 120 || request.DPI > 640 {
		return errors.New("dpi must be between 120 and 640")
	}
	shortEdge := min(request.Width, request.Height)
	longEdge := max(request.Width, request.Height)
	switch strings.ToLower(request.Orientation) {
	case "", "landscape":
		request.Width, request.Height = longEdge, shortEdge
	case "portrait":
		request.Width, request.Height = shortEdge, longEdge
	default:
		return errors.New("orientation must be portrait or landscape")
	}
	minimumEdge := uint32(320) * request.DPI / 160
	if request.Width < minimumEdge || request.Height < minimumEdge {
		return fmt.Errorf("width and height must be at least %d pixels at %d dpi", minimumEdge, request.DPI)
	}
	if !((request.Width <= 4096 && request.Height <= 2160) ||
		(request.Width <= 2160 && request.Height <= 4096)) {
		return errors.New("resolution must not exceed 4096x2160 in landscape or 2160x4096 in portrait")
	}
	if request.Width*21 < request.Height*9 || request.Width*9 > request.Height*21 {
		return errors.New("aspect ratio must remain between 9:21 and 21:9")
	}
	if request.Flags == 0 {
		request.Flags = defaultDisplayFlags
	}
	return nil
}

func findDisplay(configurations *pb.DisplayConfigurations, slot uint32) *pb.DisplayConfiguration {
	for _, display := range configurations.GetDisplays() {
		if display.GetDisplay() == slot {
			return display
		}
	}
	return nil
}

// setDisplayConfigurations only accepts user-configurable display IDs. The
// response from getDisplayConfigurations also contains immutable displays such
// as the primary display (0) and fixed automotive displays (6+), so those must
// never be echoed back in a Set request.
func configurableDisplayConfigurations(configurations *pb.DisplayConfigurations) *pb.DisplayConfigurations {
	limit := configurations.GetUserConfigurable()
	request := &pb.DisplayConfigurations{
		UserConfigurable: limit,
		MaxDisplays:      configurations.GetMaxDisplays(),
	}
	for _, display := range configurations.GetDisplays() {
		if display.GetDisplay() >= 1 && display.GetDisplay() <= limit {
			request.Displays = append(request.Displays, display)
		}
	}
	return request
}

func responseForDisplay(slot uint32, display *pb.DisplayConfiguration, ok bool, message string) displayResponse {
	response := displayResponse{
		OK: ok, Available: true, DisplaySlot: slot, DisplayName: "DISPLAY" + strconv.FormatUint(uint64(slot), 10),
		Message: message,
	}
	if display != nil {
		response.Created = true
		response.Width = display.GetWidth()
		response.Height = display.GetHeight()
		response.DPI = display.GetDpi()
		response.Flags = display.GetFlags()
		if response.Height >= response.Width {
			response.Orientation = "portrait"
		} else {
			response.Orientation = "landscape"
		}
	}
	return response
}

func writeUnavailable(w http.ResponseWriter, slot uint32, err error) {
	response := responseForDisplay(slot, nil, false, err.Error())
	response.Available = false
	response.Error = "emulator_unavailable"
	writeJSON(w, http.StatusServiceUnavailable, response)
}

func writeError(w http.ResponseWriter, status int, slot uint32, code, message string) {
	response := responseForDisplay(slot, nil, false, message)
	response.Error = code
	writeJSON(w, status, response)
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func loopbackCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" {
			parsed, err := url.Parse(origin)
			if err != nil || !isLoopbackHost(parsed.Hostname()) {
				writeJSON(w, http.StatusForbidden, map[string]any{
					"ok": false, "error": "origin_not_allowed",
				})
				return
			}
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Methods", "GET, PUT, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		}
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func isLoopbackHost(host string) bool {
	if strings.EqualFold(host, "localhost") {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

func validateListenAddress(address string) error {
	host, _, err := net.SplitHostPort(address)
	if err != nil {
		return fmt.Errorf("invalid --listen address: %w", err)
	}
	if !isLoopbackHost(host) {
		return fmt.Errorf("--listen must use a loopback host, got %q", host)
	}
	return nil
}
