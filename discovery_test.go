package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestParseEndpointFile(t *testing.T) {
	directory := t.TempDir()
	path := filepath.Join(directory, "pid_42.ini")
	contents := "avd.id=PleosConnect2\navd.name=PleosConnect2\ngrpc.port=8554\ngrpc.token=secret\n"
	if err := os.WriteFile(path, []byte(contents), 0o600); err != nil {
		t.Fatal(err)
	}

	endpoint, err := parseEndpointFile(path, "PleosConnect2")
	if err != nil {
		t.Fatal(err)
	}
	if endpoint.Address != "127.0.0.1:8554" || endpoint.Token != "secret" || endpoint.AVDName != "PleosConnect2" {
		t.Fatalf("unexpected endpoint: %+v", endpoint)
	}
}

func TestParseEndpointRejectsDifferentAVD(t *testing.T) {
	directory := t.TempDir()
	path := filepath.Join(directory, "pid_42.ini")
	if err := os.WriteFile(path, []byte("avd.name=Other\ngrpc.port=8554\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := parseEndpointFile(path, "PleosConnect2"); err == nil {
		t.Fatal("expected AVD mismatch error")
	}
}
