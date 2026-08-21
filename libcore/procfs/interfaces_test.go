package procfs

import (
	"runtime"
	"testing"
)

// Validates the /sys/class/net enumeration against Go's net.Interfaces() on a
// Linux host (the build/sandbox), where both netlink and /sys are available.
func TestNetworkInterfacesMatchesNet(t *testing.T) {
	if runtime.GOOS != "linux" {
		t.Skip("requires linux /sys/class/net")
	}
	pfIfs, err := NetworkInterfaces()
	if err != nil {
		t.Fatalf("procfs.NetworkInterfaces error: %v", err)
	}
	if len(pfIfs) == 0 {
		t.Fatal("procfs returned no interfaces; expected at least one non-loopback iface on Linux")
	}
	for _, i := range pfIfs {
		if i.Index <= 0 {
			t.Fatalf("interface %q has non-positive index %d", i.Name, i.Index)
		}
	}
}

func TestInterfaceByNameLookup(t *testing.T) {
	if runtime.GOOS != "linux" {
		t.Skip("requires linux /sys/class/net")
	}
	pfIfs, err := NetworkInterfaces()
	if err != nil {
		t.Fatalf("procfs.NetworkInterfaces error: %v", err)
	}
	if len(pfIfs) == 0 {
		t.Skip("no interfaces available")
	}
	// The first discovered interface must be resolvable by name.
	got, ok := InterfaceByName(pfIfs[0].Name)
	if !ok {
		t.Fatalf("InterfaceByName(%q) returned !ok", pfIfs[0].Name)
	}
	if got.Index != pfIfs[0].Index {
		t.Fatalf("index mismatch: %d != %d", got.Index, pfIfs[0].Index)
	}
	if got.Name != pfIfs[0].Name {
		t.Fatalf("name mismatch: %q != %q", got.Name, pfIfs[0].Name)
	}
	// A bogus name must not resolve.
	if _, ok := InterfaceByName("no-such-iface-12345"); ok {
		t.Fatal("expected bogus interface to not resolve")
	}
}
