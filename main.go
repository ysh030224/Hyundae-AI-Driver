package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"time"

	pb "ai.pleos/connect-web-console/host-display-bridge/internal/emulatorpb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type bearerToken string

func (token bearerToken) GetRequestMetadata(context.Context, ...string) (map[string]string, error) {
	return map[string]string{"authorization": "Bearer " + string(token)}, nil
}

func (bearerToken) RequireTransportSecurity() bool { return false }

func main() {
	var (
		listenAddress  = flag.String("listen", "127.0.0.1:8096", "local HTTP listen address")
		emulatorTarget = flag.String("emulator-address", "", "Emulator gRPC address, for example 127.0.0.1:8554")
		endpointFile   = flag.String("endpoint-file", os.Getenv("ANDROID_EMULATOR_ENDPOINT_FILE"), "Emulator pid_*.ini discovery file")
		avdName        = flag.String("avd", os.Getenv("AVD_NAME"), "AVD name used to select a running emulator")
		displaySlot    = flag.Uint("display", uint(defaultDisplaySlot), "Emulator secondary display slot")
	)
	flag.Parse()

	if err := validateListenAddress(*listenAddress); err != nil {
		log.Fatal(err)
	}
	if *displaySlot == 0 {
		log.Fatal("--display must be greater than zero")
	}

	target := strings.TrimSpace(*emulatorTarget)
	token := os.Getenv("EMULATOR_GRPC_TOKEN")
	discoveryPath := ""
	discoveredAVD := *avdName
	if target == "" {
		endpoint, err := discoverEndpoint(*endpointFile, *avdName)
		if err != nil {
			log.Fatal(err)
		}
		target = endpoint.Address
		discoveryPath = endpoint.Path
		discoveredAVD = endpoint.AVDName
		if token == "" {
			token = endpoint.Token
		}
	}

	dialOptions := []grpc.DialOption{grpc.WithTransportCredentials(insecure.NewCredentials())}
	if token != "" {
		dialOptions = append(dialOptions, grpc.WithPerRPCCredentials(bearerToken(token)))
	}
	connection, err := grpc.NewClient(target, dialOptions...)
	if err != nil {
		log.Fatalf("connect to Emulator gRPC at %s: %v", target, err)
	}
	defer connection.Close()

	handler := (&bridge{
		client: pb.NewEmulatorControllerClient(connection),
		slot:   uint32(*displaySlot),
	}).handler()
	server := &http.Server{
		Addr:              *listenAddress,
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("ConnectWebConsole host display bridge listening on http://%s", *listenAddress)
	log.Printf("Emulator gRPC target: %s (AVD: %s)", target, emptyAsUnknown(discoveredAVD))
	if discoveryPath != "" {
		log.Printf("Emulator discovery file: %s", discoveryPath)
	}
	if token == "" {
		log.Print("Emulator token not found; connection requires an insecure local gRPC endpoint")
	}

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt)
	go func() {
		<-stop
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Shutdown(ctx)
	}()

	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}

func emptyAsUnknown(value string) string {
	if value == "" {
		return "unknown"
	}
	return value
}

func init() {
	flag.CommandLine.SetOutput(os.Stderr)
	flag.Usage = func() {
		_, _ = fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s [options]\n\n", os.Args[0])
		flag.PrintDefaults()
	}
}
