package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	pb "ai.pleos/connect-web-console/host-display-bridge/internal/emulatorpb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/emptypb"
)

type fakeEmulator struct {
	configurations *pb.DisplayConfigurations
	lastSet        *pb.DisplayConfigurations
}

func (f *fakeEmulator) GetDisplayConfigurations(context.Context, *emptypb.Empty, ...grpc.CallOption) (*pb.DisplayConfigurations, error) {
	return proto.Clone(f.configurations).(*pb.DisplayConfigurations), nil
}

func (f *fakeEmulator) SetDisplayConfigurations(_ context.Context, configurations *pb.DisplayConfigurations, _ ...grpc.CallOption) (*pb.DisplayConfigurations, error) {
	limit := f.configurations.GetUserConfigurable()
	for _, display := range configurations.GetDisplays() {
		if display.GetDisplay() < 1 || display.GetDisplay() > limit {
			return nil, status.Errorf(codes.InvalidArgument,
				"display %d is outside user-configurable range 1..%d", display.GetDisplay(), limit)
		}
	}
	f.lastSet = proto.Clone(configurations).(*pb.DisplayConfigurations)
	next := proto.Clone(f.configurations).(*pb.DisplayConfigurations)
	next.Displays = next.Displays[:0]
	for _, display := range f.configurations.GetDisplays() {
		if display.GetDisplay() < 1 || display.GetDisplay() > limit {
			next.Displays = append(next.Displays, display)
		}
	}
	next.Displays = append(next.Displays, configurations.GetDisplays()...)
	f.configurations = next
	return proto.Clone(f.configurations).(*pb.DisplayConfigurations), nil
}

func TestCreatePreservesExistingDisplay(t *testing.T) {
	fake := &fakeEmulator{configurations: &pb.DisplayConfigurations{UserConfigurable: 3, Displays: []*pb.DisplayConfiguration{
		{Display: 0, Width: 2560, Height: 1440, Dpi: 160},
		{Display: 1, Width: 528, Height: 792, Dpi: 160, Flags: 1352},
		{Display: 6, Width: 1920, Height: 720, Dpi: 160},
	}}}
	handler := (&bridge{client: fake, slot: 2}).handler()
	body := bytes.NewBufferString(`{"width":2560,"height":1440,"dpi":160,"orientation":"portrait"}`)
	request := httptest.NewRequest(http.MethodPut, "/api/window-policy/display/create", body)
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	if len(fake.configurations.Displays) != 4 {
		t.Fatalf("display count = %d, want 4", len(fake.configurations.Displays))
	}
	display := findDisplay(fake.configurations, 2)
	if display == nil || display.Width != 1440 || display.Height != 2560 || display.Flags != defaultDisplayFlags {
		t.Fatalf("unexpected DISPLAY2: %+v", display)
	}
	assertDisplayIDs(t, fake.lastSet, 1, 2)
}

func TestRemovePreservesOtherDisplays(t *testing.T) {
	fake := &fakeEmulator{configurations: &pb.DisplayConfigurations{UserConfigurable: 3, Displays: []*pb.DisplayConfiguration{
		{Display: 0}, {Display: 1}, {Display: 2}, {Display: 6},
	}}}
	handler := (&bridge{client: fake, slot: 2}).handler()
	request := httptest.NewRequest(http.MethodPut, "/api/window-policy/display/remove", bytes.NewBufferString(`{}`))
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	if findDisplay(fake.configurations, 2) != nil || len(fake.configurations.Displays) != 3 {
		t.Fatalf("unexpected configurations: %+v", fake.configurations)
	}
	assertDisplayIDs(t, fake.lastSet, 1)
}

func assertDisplayIDs(t *testing.T, configurations *pb.DisplayConfigurations, want ...uint32) {
	t.Helper()
	if configurations == nil {
		t.Fatal("SetDisplayConfigurations was not called")
	}
	got := configurations.GetDisplays()
	if len(got) != len(want) {
		t.Fatalf("Set display count = %d, want %d: %+v", len(got), len(want), got)
	}
	for index, display := range got {
		if display.GetDisplay() != want[index] {
			t.Fatalf("Set display[%d] = %d, want %d", index, display.GetDisplay(), want[index])
		}
	}
}

func TestRejectsNonLoopbackOrigin(t *testing.T) {
	fake := &fakeEmulator{configurations: &pb.DisplayConfigurations{}}
	handler := (&bridge{client: fake, slot: 2}).handler()
	request := httptest.NewRequest(http.MethodGet, "/api/window-policy/display", nil)
	request.Header.Set("Origin", "https://example.com")
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)
	if response.Code != http.StatusForbidden {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusForbidden)
	}
	var body map[string]any
	if err := json.NewDecoder(response.Body).Decode(&body); err != nil || body["error"] != "origin_not_allowed" {
		t.Fatalf("unexpected body: %v, err: %v", body, err)
	}
}

func TestValidationMatchesEmulatorLimits(t *testing.T) {
	tests := []struct {
		name    string
		request displayRequest
		wantErr bool
	}{
		{name: "landscape", request: displayRequest{Width: 2560, Height: 1440, DPI: 160}},
		{name: "portrait", request: displayRequest{Width: 2560, Height: 1440, DPI: 160, Orientation: "portrait"}},
		{name: "too large", request: displayRequest{Width: 4097, Height: 2160, DPI: 160}, wantErr: true},
		{name: "too narrow", request: displayRequest{Width: 4096, Height: 1000, DPI: 160}, wantErr: true},
		{name: "edge below 320dp", request: displayRequest{Width: 600, Height: 600, DPI: 640}, wantErr: true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := normalizeAndValidateRequest(&test.request)
			if (err != nil) != test.wantErr {
				t.Fatalf("error = %v, wantErr = %v", err, test.wantErr)
			}
		})
	}
}
