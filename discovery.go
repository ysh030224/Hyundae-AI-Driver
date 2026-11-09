package main

import (
	"bufio"
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"time"
)

type endpointInfo struct {
	Address string
	Token   string
	AVDName string
	Path    string
	modTime time.Time
}

func discoverEndpoint(endpointFile, avdName string) (endpointInfo, error) {
	if endpointFile != "" {
		return parseEndpointFile(endpointFile, avdName)
	}

	var files []string
	for _, directory := range discoveryDirectories() {
		matches, _ := filepath.Glob(filepath.Join(directory, "pid_*.ini"))
		files = append(files, matches...)
	}
	if len(files) == 0 {
		return endpointInfo{}, errors.New("no Android Emulator discovery file found; use --endpoint-file or --emulator-address")
	}

	var endpoints []endpointInfo
	var parseErrors []string
	for _, file := range uniqueStrings(files) {
		endpoint, err := parseEndpointFile(file, avdName)
		if err != nil {
			parseErrors = append(parseErrors, err.Error())
			continue
		}
		if !endpointReachable(endpoint.Address) {
			parseErrors = append(parseErrors,
				fmt.Sprintf("endpoint %q is not listening at %s", file, endpoint.Address))
			continue
		}
		endpoints = append(endpoints, endpoint)
	}
	if len(endpoints) == 0 {
		if len(parseErrors) > 0 {
			return endpointInfo{}, fmt.Errorf("no matching Android Emulator endpoint found: %s", strings.Join(parseErrors, "; "))
		}
		return endpointInfo{}, errors.New("no matching Android Emulator endpoint found")
	}

	sort.Slice(endpoints, func(i, j int) bool {
		return endpoints[i].modTime.After(endpoints[j].modTime)
	})
	return endpoints[0], nil
}

func endpointReachable(address string) bool {
	connection, err := net.DialTimeout("tcp", address, 250*time.Millisecond)
	if err != nil {
		return false
	}
	_ = connection.Close()
	return true
}

func parseEndpointFile(path, wantedAVD string) (endpointInfo, error) {
	file, err := os.Open(path)
	if err != nil {
		return endpointInfo{}, fmt.Errorf("open endpoint file %q: %w", path, err)
	}
	defer file.Close()

	values := make(map[string]string)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, found := strings.Cut(line, "=")
		if !found {
			continue
		}
		values[strings.TrimSpace(key)] = strings.Trim(strings.TrimSpace(value), "\"")
	}
	if err := scanner.Err(); err != nil {
		return endpointInfo{}, fmt.Errorf("read endpoint file %q: %w", path, err)
	}

	name := values["avd.name"]
	if name == "" {
		name = values["avd.id"]
	}
	if wantedAVD != "" && name != wantedAVD && values["avd.id"] != wantedAVD {
		return endpointInfo{}, fmt.Errorf("endpoint %q belongs to AVD %q, not %q", path, name, wantedAVD)
	}
	port, err := strconv.Atoi(values["grpc.port"])
	if err != nil || port < 1 || port > 65535 {
		return endpointInfo{}, fmt.Errorf("endpoint %q has invalid grpc.port", path)
	}
	stat, err := file.Stat()
	if err != nil {
		return endpointInfo{}, fmt.Errorf("stat endpoint file %q: %w", path, err)
	}
	return endpointInfo{
		Address: fmt.Sprintf("127.0.0.1:%d", port),
		Token:   values["grpc.token"],
		AVDName: name,
		Path:    path,
		modTime: stat.ModTime(),
	}, nil
}

func discoveryDirectories() []string {
	var directories []string
	if value := os.Getenv("ANDROID_EMULATOR_DISCOVERY_DIR"); value != "" {
		directories = append(directories, value)
	}
	if value := os.Getenv("XDG_RUNTIME_DIR"); value != "" {
		directories = append(directories, filepath.Join(value, "avd", "running"))
	}
	directories = append(directories, filepath.Join(os.TempDir(), "avd", "running"))

	home, _ := os.UserHomeDir()
	if home != "" && runtime.GOOS == "darwin" {
		directories = append(directories,
			filepath.Join(home, "Library", "Caches", "TemporaryItems", "avd", "running"))
	}
	if localAppData := os.Getenv("LOCALAPPDATA"); localAppData != "" && runtime.GOOS == "windows" {
		directories = append(directories,
			filepath.Join(localAppData, "Temp", "avd", "running"))
	}
	return uniqueStrings(directories)
}

func uniqueStrings(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		clean := filepath.Clean(value)
		if _, exists := seen[clean]; exists {
			continue
		}
		seen[clean] = struct{}{}
		result = append(result, clean)
	}
	return result
}
