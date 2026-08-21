package libcore

import (
	"log"
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
	// pendingDefaultInterface caches the last default-interface name reported
	// by the app before any box instance registered a monitor. Without this,
	// callbacks arriving between Libcore.initCore and box.Start (the common
	// case, since BaseService.preInit starts the network listener first) are
	// silently dropped and the box would start with no default interface.
	pendingDefaultInterface string
)

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
	pendingDefaultInterface = ""
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
	// Prefer the live platform answer, fall back to the name cached before
	// this monitor existed. A virtual (TUN) name is rejected: it would leave
	// selectInterfaces with zero candidates.
	interfaceName := intfBox.DefaultInterfaceName()
	if interfaceName == "" {
		interfaceName = pending
	}
	if interfaceName != "" && !isVirtualInterfaceName(interfaceName) {
		UpdateDefaultInterface(interfaceName)
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
					UpdateDefaultInterface(name)
					return
				}
			}
		}()
	}
	return nil
}

func (s *interfaceMonitor) Close() error {
	defaultInterfaceMonitorAccess.Lock()
	if defaultInterfaceMonitor == s {
		defaultInterfaceMonitor = nil
	}
	s.access.Lock()
	var lastName string
	if s.defaultInterface != nil {
		lastName = s.defaultInterface.Name
	}
	s.access.Unlock()
	// Keep the last known good name for the next Start in this process
	// (service restarts reuse the same Go runtime).
	pendingDefaultInterface = lastName
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
func UpdateDefaultInterface(interfaceName string) {
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
		pendingDefaultInterface = interfaceName
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
	// interfaceName is guaranteed non-empty and physical here. Resolve it
	// without netlink (RTM_GETLINK is EPERM for normal Android apps): prefer
	// the interfaceFinder cache (populated by UpdateInterfaces above via
	// procfs), then fall back to a direct /sys/class/net lookup.
	var interfaceValue control.Interface
	if monitor.platformInterface != nil && monitor.platformInterface.networkManager != nil {
		if finder := monitor.platformInterface.networkManager.InterfaceFinder(); finder != nil {
			if iif, finderErr := finder.ByName(interfaceName); finderErr == nil {
				interfaceValue = *iif
			}
		}
	}
	if interfaceValue.Name == "" {
		if iif, ok := procfs.InterfaceByName(interfaceName); ok {
			interfaceValue = iif
		} else {
			monitor.access.Unlock()
			return
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
	for _, callback := range callbacks {
		callback(updated, 0)
	}
}
