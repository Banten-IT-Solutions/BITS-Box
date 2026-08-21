package libcore

import (
	"net"
	"testing"

	C "github.com/sagernet/sing-box/constant"
)

func TestParsePlatformInterfacesJSON(t *testing.T) {
	raw := `[
		{"name":"wlan0","index":25,"mtu":1500,"type":"wifi","flags":4163,"dns":["8.8.8.8"],"metered":false},
		{"name":"rmnet_data0","index":30,"mtu":1400,"type":"cellular","flags":4097,"metered":true},
		{"name":"tun0","index":40,"mtu":1500,"type":"other","flags":4161},
		{"name":"","index":0},
		{"name":"broken","index":0}
	]`
	interfaces, err := parsePlatformInterfacesJSON(raw)
	if err != nil {
		t.Fatal(err)
	}
	if len(interfaces) != 2 {
		t.Fatalf("expected 2 usable interfaces, got %d", len(interfaces))
	}
	wifi := interfaces[0]
	if wifi.Name != "wlan0" || wifi.Index != 25 || wifi.MTU != 1500 {
		t.Fatalf("unexpected wifi entry: %+v", wifi.Interface)
	}
	if wifi.Type != C.InterfaceTypeWIFI {
		t.Fatalf("expected wifi type, got %v", wifi.Type)
	}
	if wifi.Flags&net.FlagUp == 0 {
		t.Fatal("expected FlagUp from IFF_UP")
	}
	if len(wifi.DNSServers) != 1 || wifi.DNSServers[0] != "8.8.8.8" {
		t.Fatalf("unexpected dns servers: %v", wifi.DNSServers)
	}
	if wifi.Expensive {
		t.Fatal("wifi should not be expensive")
	}
	cell := interfaces[1]
	if cell.Type != C.InterfaceTypeCellular || !cell.Expensive {
		t.Fatalf("unexpected cellular entry: %+v", cell)
	}
}

func TestParsePlatformInterfacesJSONUnknownType(t *testing.T) {
	raw := `[{"name":"wlan1","index":7,"type":"bogus","flags":1}]`
	interfaces, err := parsePlatformInterfacesJSON(raw)
	if err != nil {
		t.Fatal(err)
	}
	if interfaces[0].Type != C.InterfaceTypeWIFI {
		t.Fatalf("expected name-based wifi fallback, got %v", interfaces[0].Type)
	}
}

func TestParsePlatformInterfacesJSONEmpty(t *testing.T) {
	if _, err := parsePlatformInterfacesJSON(`[]`); err == nil {
		t.Fatal("expected error for empty list")
	}
	if _, err := parsePlatformInterfacesJSON(`not json`); err == nil {
		t.Fatal("expected error for invalid JSON")
	}
}
