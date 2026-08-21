package libcore

import (
	"log"
	"net"
	"strings"
	"sync"
	"time"

	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/x/list"
	"libcore/procfs"
)

var (
	defaultInterfaceMonitor       *interfaceMonitor
	defaultInterfaceMonitorAccess sync.Mutex
	// pendingDefaultInterface caches the last default-interface report
	// (name and ifindex) from the app before any box instance registered a
	// monitor. Without this, callbacks arriving between Libcore.initCore and
	// box.Start (the common case, since BaseService.preInit starts the
	// network listener first) are silently dropped and the box would start
	// with no default interface.
	pendingDefaultInterface pendingInterface
)

type pendingInterface struct {
	name  string
	index int32
}

// isVirtualInterfaceName reports whether name looks like a virtual/VPN
// interface. Our own TUN must never become the default interface: sing-box's
// selectInterfaces excludes myInterfaces (the TUN) from candidates and then
// keeps only interfaces matching the default interface index — a TUN default
// therefore leaves zero candidates and every dial fails with
// "no available network interface".
func isVirtualInterfaceName(name string) bool {
	if name == "" {
		return false
	}
	for _, prefix := range []string{"tun", "tproxy", "utun"} {
		if strings.HasPrefix(name, prefix) {
			return true
		}
	}
	return false
}

type interfaceMonitor struct {
	access            sync.Mutex
	defaultInterface  *control.Interface
	callbacks         list.List[tun.DefaultInterfaceUpdateCallback]
	myInterfaces      []string
	platformInterface *boxPlatformInterfaceWrapper
}

func (s *interfaceMonitor) Start() error {
	defaultInterfaceMonitorAccess.Lock()
	defaultInterfaceMonitor = s
	pending := pendingDefaultInterface
	pendingDefaultInterface = pendingInterface{}
	defaultInterfaceMonitorAccess.Unlock()
	// Populate the platform interface list as early as possible so the
	// parallel-interface dialer has usable interfaces even before the app
	// reports the default network.
	if w := s.platformInterface; w != nil && w.networkManager != nil {
		if err := w.networkManager.UpdateInterfaces(); err != nil {
			// Non-fatal: retried on the next UpdateDefaultInterface call.
			log.Println("interface_monitor Start: UpdateInterfaces error: ", err)
		}
	} else {
		log.Println("interface_monitor Start: platformInterface or networkManager is nil")
	}
	available := s.platformInterface.networkManager.NetworkInterfaces()
	names := make([]string, 0, len(available))
	for _, it := range available {
		names = append(names, it.Name)
	}
	log.Println("interface_monitor Start: available network interfaces: ", strings.Join(names, ", "))
	// myInterfaces excludes the TUN device from dialing candidates.
	log.Println("interface_monitor Start: myInterfaces (excluded): ", s.myInterfaces)
	// Prefer the live platform answer, fall back to the report cached before
	// this monitor existed. A virtual (TUN) name is rejected: it would leave
	// selectInterfaces with zero candidates.
	interfaceName := intfBox.DefaultInterfaceName()
	interfaceIndex := int32(-1)
	if interfaceName == "" {
		interfaceName = pending.name
		interfaceIndex = pending.index
	} else {
		interfaceIndex = resolveInterfaceIndex(s.platformInterface, interfaceName, -1)
	}
	if interfaceName != "" && !isVirtualInterfaceName(interfaceName) {
		UpdateDefaultInterface(interfaceName, interfaceIndex)
	} else {
		// The physical network callback has not fired yet (or only reported
		// our own TUN). Poll briefly instead of leaving the box without a
		// default interface, which makes every dial fail until the next
		// network change.
		go func() {
			for i := 0; i < 20; i++ {
				time.Sleep(500 * time.Millisecond)
				name := intfBox.DefaultInterfaceName()
				if name != "" && !isVirtualInterfaceName(name) {
					UpdateDefaultInterface(name, resolveInterfaceIndex(s.platformInterface, name, -1))
					return
				}
			}
		}()
	}
	return nil
}

// resolveInterfaceIndex maps an interface name to its ifindex using the
// platform interface list (ConnectivityManager JSON / procfs cache). Returns
// fallback when the name cannot be resolved; callers must still accept the
// update with an unknown index instead of dropping it.
func resolveInterfaceIndex(w *boxPlatformInterfaceWrapper, interfaceName string, fallback int32) int32 {
	if w != nil && w.networkManager != nil {
		if finder := w.networkManager.InterfaceFinder(); finder != nil {
			if iif, err := finder.ByName(interfaceName); err == nil && iif.Index > 0 {
				return int32(iif.Index)
			}
		}
	}
	return fallback
}

func (s *interfaceMonitor) Close() error {
	defaultInterfaceMonitorAccess.Lock()
	if defaultInterfaceMonitor == s {
		defaultInterfaceMonitor = nil
	}
	s.access.Lock()
	var lastName string
	var lastIndex int32
	if s.defaultInterface != nil {
		lastName = s.defaultInterface.Name
		lastIndex = int32(s.defaultInterface.Index)
	}
	s.access.Unlock()
	// Keep the last known good report for the next Start in this process
	// (service restarts reuse the same Go runtime).
	pendingDefaultInterface = pendingInterface{name: lastName, index: lastIndex}
	defaultInterfaceMonitorAccess.Unlock()
	return nil
}

func (s *interfaceMonitor) DefaultInterface() *control.Interface {
	s.access.Lock()
	defer s.access.Unlock()
	return s.defaultInterface
}

func (s *interfaceMonitor) OverrideAndroidVPN() bool {
	return false
}

func (s *interfaceMonitor) AndroidVPNEnabled() bool {
	return false
}

func (s *interfaceMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	s.access.Lock()
	defer s.access.Unlock()
	return s.callbacks.PushBack(callback)
}

func (s *interfaceMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	s.access.Lock()
	defer s.access.Unlock()
	s.callbacks.Remove(element)
}

func (s *interfaceMonitor) RegisterMyInterface(interfaceName string) {
	s.access.Lock()
	defer s.access.Unlock()
	s.myInterfaces = append(s.myInterfaces, interfaceName)
}

func (s *interfaceMonitor) MyInterfaces() []string {
	s.access.Lock()
	defer s.access.Unlock()
	return append([]string(nil), s.myInterfaces...)
}

// isMyInterface reports whether name is one of the interfaces owned by this
// box (the TUN device created by OpenInterface).
func (s *interfaceMonitor) isMyInterface(name string) bool {
	s.access.Lock()
	defer s.access.Unlock()
	for _, myName := range s.myInterfaces {
		if myName == name {
			return true
		}
	}
	return false
}

// UpdateDefaultInterface receives Android ConnectivityManager updates.
// interfaceIndex is the ifindex resolved on the Kotlin side
// (java.net.NetworkInterface); it may be -1 when resolution failed.
func UpdateDefaultInterface(interfaceName string, interfaceIndex int32) {
	// Never accept a virtual/VPN interface (especially our own TUN) as the
	// default: see isVirtualInterfaceName. Empty names are also ignored here
	// so a transient "network lost" report cannot clear a working default.
	if interfaceName == "" || isVirtualInterfaceName(interfaceName) {
		return
	}
	defaultInterfaceMonitorAccess.Lock()
	monitor := defaultInterfaceMonitor
	if monitor == nil {
		// Box not started yet: cache the report so interfaceMonitor.Start
		// can apply it instead of silently dropping it.
		pendingDefaultInterface = pendingInterface{name: interfaceName, index: interfaceIndex}
		defaultInterfaceMonitorAccess.Unlock()
		return
	}
	defaultInterfaceMonitorAccess.Unlock()
	if monitor.isMyInterface(interfaceName) {
		return
	}
	// Refresh the platform interface list before updating the default
	// interface, so the parallel-interface dialer (network_strategy) always
	// has the current set of usable interfaces to select from.
	if w := monitor.platformInterface; w != nil && w.networkManager != nil {
		if err := w.networkManager.UpdateInterfaces(); err != nil {
			// Non-fatal: keep the stale list if refresh fails.
		}
	}
	monitor.access.Lock()
	// interfaceName is guaranteed non-empty and physical here. Resolve the
	// full interface without netlink (RTM_GETLINK is EPERM for normal
	// Android apps): prefer the interfaceFinder cache (populated by
	// UpdateInterfaces above via the ConnectivityManager JSON or procfs),
	// then a direct /sys/class/net lookup. Both are best-effort: when they
	// fail, the report from the app (name + index) is authoritative and the
	// update must NOT be dropped — dropping a valid default interface leaves
	// the dialer without candidates ("no available network interface").
	var interfaceValue control.Interface
	if monitor.platformInterface != nil && monitor.platformInterface.networkManager != nil {
		if finder := monitor.platformInterface.networkManager.InterfaceFinder(); finder != nil {
			if iif, finderErr := finder.ByName(interfaceName); finderErr == nil {
				interfaceValue = *iif
			} else if interfaceIndex > 0 {
				if iif, finderErr = finder.ByIndex(int(interfaceIndex)); finderErr == nil {
					interfaceValue = *iif
				}
			}
		}
	}
	if interfaceValue.Name == "" {
		if iif, ok := procfs.InterfaceByName(interfaceName); ok {
			interfaceValue = iif
		} else if interfaceIndex > 0 {
			// No kernel access available (strict SELinux): build the
			// interface directly from the app report.
			interfaceValue = control.Interface{
				Index: int(interfaceIndex),
				Name:  interfaceName,
				Flags: net.FlagUp,
			}
		} else {
			// Name only, index unknown. Still set the default so the dialer
			// has a candidate; Index stays 0.
			interfaceValue = control.Interface{
				Name:  interfaceName,
				Flags: net.FlagUp,
			}
		}
	}
	if monitor.defaultInterface != nil &&
		monitor.defaultInterface.Name == interfaceValue.Name && monitor.defaultInterface.Index == interfaceValue.Index {
		monitor.access.Unlock()
		return
	}
	updated := &interfaceValue
	monitor.defaultInterface = updated
	callbacks := monitor.callbacks.Array()
	monitor.access.Unlock()
	log.Println("interface_monitor: default interface updated: ", updated.Name, " (", updated.Index, ")")
	for _, callback := range callbacks {
		callback(updated, 0)
	}
}
