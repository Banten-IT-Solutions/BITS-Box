package procfs

import (
	"net"
	"os"
	"strconv"
	"strings"

	"github.com/sagernet/sing/common/control"
)

// NetworkInterfaces enumerates network interfaces from /sys/class/net without
// netlink. On Android, Go's net.Interfaces() issues RTM_GETLINK over netlink,
// which is EPERM for a normal app context; without interfaces the parallel
// dialer reports "no available network interface" and every direct/proxy dial
// fails. /sys/class/net is world-readable and exposes ifindex/flags/mtu per
// interface, which is sufficient for interface selection and binding.
func NetworkInterfaces() ([]control.Interface, error) {
	entries, err := os.ReadDir("/sys/class/net")
	if err != nil {
		return nil, err
	}
	out := make([]control.Interface, 0, len(entries))
	for _, e := range entries {
		name := e.Name()
		if name == "" || name == "lo" {
			continue
		}
		iif, ok := InterfaceByName(name)
		if !ok {
			continue
		}
		out = append(out, iif)
	}
	return out, nil
}

// InterfaceByName reads /sys/class/net/<name>/ifindex, flags and mtu for one
// interface without netlink. Returns ok=false if the interface does not exist.
func InterfaceByName(name string) (control.Interface, bool) {
	base := "/sys/class/net/" + name
	if index, ok := readUint(base + "/ifindex"); ok && index > 0 {
		return control.Interface{
			Index:        index,
			Name:         name,
			MTU:          readUintOr(base+"/mtu", 0),
			Flags:        readFlags(base + "/flags"),
			HardwareAddr: readMAC(base + "/address"),
		}, true
	}
	return control.Interface{}, false
}

func readUint(path string) (int, bool) {
	b, err := os.ReadFile(path)
	if err != nil {
		return 0, false
	}
	v, err := strconv.Atoi(strings.TrimSpace(string(b)))
	if err != nil {
		return 0, false
	}
	return v, true
}

func readUintOr(path string, def int) int {
	v, ok := readUint(path)
	if !ok {
		return def
	}
	return v
}

func readFlags(path string) net.Flags {
	b, err := os.ReadFile(path)
	if err != nil {
		return net.FlagUp
	}
	s := strings.TrimSpace(string(b))
	s = strings.TrimPrefix(s, "0x")
	s = strings.TrimPrefix(s, "0X")
	v, err := strconv.ParseUint(s, 16, 32)
	if err != nil {
		return net.FlagUp
	}
	var f net.Flags
	if v&0x1 != 0 {
		f |= net.FlagUp
	}
	if v&0x2 != 0 {
		f |= net.FlagBroadcast
	}
	if v&0x8 != 0 {
		f |= net.FlagLoopback
	}
	if v&0x10 != 0 {
		f |= net.FlagPointToPoint
	}
	if v&0x1000 != 0 {
		f |= net.FlagMulticast
	}
	return f
}

func readMAC(path string) net.HardwareAddr {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	hw, err := net.ParseMAC(strings.TrimSpace(string(b)))
	if err != nil {
		return nil
	}
	return hw
}
