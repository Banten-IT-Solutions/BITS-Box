package libcore

import (
	"encoding/json"
	"fmt"
	"libcore/procfs"
	"log"
	"net"
	"net/netip"
	"strings"
	"syscall"

	"github.com/Banten-IT-Solutions/bits-box-core/bitsbox_log"
	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	sblog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	N "github.com/sagernet/sing/common/network"
)

var boxPlatformInterfaceInstance adapter.PlatformInterface = &boxPlatformInterfaceWrapper{}

type boxPlatformInterfaceWrapper struct {
	networkManager adapter.NetworkManager
}

func (w *boxPlatformInterfaceWrapper) ReadWIFIState() adapter.WIFIState {
	state := strings.Split(intfBox.WIFIState(), ",")
	return adapter.WIFIState{
		SSID:  state[0],
		BSSID: state[1],
	}
}

func (w *boxPlatformInterfaceWrapper) Initialize(n adapter.NetworkManager) error {
	w.networkManager = n
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) AutoDetectInterfaceControl(fd int) error {
	// call protect_path
	if !isBgProcess {
		_ = sendFdToProtect(fd, "protect_path")
		return nil
	}
	// bg process call VPNService
	return intfBox.AutoDetectInterfaceControl(int32(fd))
}

func (w *boxPlatformInterfaceWrapper) UsePlatformInterface() bool { return true }

func (w *boxPlatformInterfaceWrapper) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if len(options.IncludeUID) > 0 || len(options.ExcludeUID) > 0 {
		return nil, E.New("android: unsupported uid options")
	}
	if len(options.IncludeAndroidUser) > 0 {
		return nil, E.New("android: unsupported android_user option")
	}
	a, _ := json.Marshal(options)
	b, _ := json.Marshal(platformOptions)
	tunFd, err := intfBox.OpenTun(string(a), string(b))
	if err != nil {
		return nil, fmt.Errorf("intfBox.OpenTun: %v", err)
	}
	// Resolve the actual TUN device name and register it as "my interface" so
	// the parallel-interface dialer can exclude it from direct dialing
	// (otherwise traffic would loop back into the TUN). Must happen before
	// tun.New so the internal copy of options carries the real name.
	tunName, err := getTunnelName(int32(tunFd))
	if err != nil {
		return nil, fmt.Errorf("query tun name: %v", err)
	}
	options.Name = tunName
	if options.InterfaceMonitor != nil {
		options.InterfaceMonitor.RegisterMyInterface(tunName)
	}
	// Do you want to close it?
	tunFd, err = syscall.Dup(tunFd)
	if err != nil {
		return nil, fmt.Errorf("syscall.Dup: %v", err)
	}
	//
	options.FileDescriptor = int(tunFd)
	return tun.New(*options)
}

func (w *boxPlatformInterfaceWrapper) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) CreateDefaultInterfaceMonitor(l logger.Logger) tun.DefaultInterfaceMonitor {
	return &interfaceMonitor{
		platformInterface: w,
	}
}

func (w *boxPlatformInterfaceWrapper) UsePlatformNetworkInterfaces() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	netIfs, err := net.Interfaces()
	if err == nil {
		interfaces := make([]adapter.NetworkInterface, 0, len(netIfs))
		for _, netIf := range netIfs {
			iif, err := control.InterfaceFromNet(netIf)
			if err != nil {
				continue
			}
			interfaces = append(interfaces, adapter.NetworkInterface{
				Interface: iif,
				Type:      networkInterfaceType(netIf.Name),
			})
		}
		return interfaces, nil
	}
	// Go's net.Interfaces() issued RTM_GETLINK over netlink, which is EPERM
	// for a normal Android app context. That leaves sing-box with no
	// interface to dial from ("no available network interface"). Fall back to
	// /sys/class/net, which is world-readable and needs no netlink.
	pfIfs, err := procfs.NetworkInterfaces()
	if err != nil {
		return nil, err
	}
	interfaces := make([]adapter.NetworkInterface, 0, len(pfIfs))
	for _, iif := range pfIfs {
		interfaces = append(interfaces, adapter.NetworkInterface{
			Interface: iif,
			Type:      networkInterfaceType(iif.Name),
		})
	}
	return interfaces, nil
}

func networkInterfaceType(name string) C.InterfaceType {
	switch {
	case strings.HasPrefix(name, "wlan"), strings.HasPrefix(name, "wifi"),
		strings.HasPrefix(name, "p2p"), strings.HasPrefix(name, "ap0"):
		return C.InterfaceTypeWIFI
	case strings.HasPrefix(name, "rmnet"), strings.HasPrefix(name, "ccmni"),
		strings.HasPrefix(name, "sw0"), strings.HasPrefix(name, "wwan"),
		strings.HasPrefix(name, "usb"):
		return C.InterfaceTypeCellular
	case strings.HasPrefix(name, "eth"), strings.HasPrefix(name, "enp"),
		strings.HasPrefix(name, "enx"), strings.HasPrefix(name, "bond"):
		return C.InterfaceTypeEthernet
	default:
		return C.InterfaceTypeOther
	}
}

func (w *boxPlatformInterfaceWrapper) NetworkExtensionIncludeAllNetworks() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) SendNotification(notification *adapter.Notification) error {
	return nil
}

func (s *boxPlatformInterfaceWrapper) SystemCertificates() []string {
	return nil
}

func (w *boxPlatformInterfaceWrapper) RequestPermissionForWIFIState() error   { return nil }
func (w *boxPlatformInterfaceWrapper) UsePlatformConnectionOwnerFinder() bool { return true }
func (w *boxPlatformInterfaceWrapper) UsePlatformWIFIMonitor() bool           { return false }
func (w *boxPlatformInterfaceWrapper) UsePlatformNotification() bool          { return false }
func (w *boxPlatformInterfaceWrapper) MyInterfaceAddress() []netip.Addr       { return nil }

// Android not using

func (w *boxPlatformInterfaceWrapper) UnderNetworkExtension() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) ClearDNSCache() {
}

// process.Searcher

func (w *boxPlatformInterfaceWrapper) FindConnectionOwner(request *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	var uid int32
	if useProcfs {
		network := N.NetworkTCP
		if request.IpProtocol == syscall.IPPROTO_UDP {
			network = N.NetworkUDP
		}
		uid = procfs.ResolveSocketByProcSearch(network, netip.MustParseAddrPort(fmt.Sprintf("%s:%d", request.SourceAddress, request.SourcePort)), netip.MustParseAddrPort(fmt.Sprintf("%s:%d", request.DestinationAddress, request.DestinationPort)))
		if uid == -1 {
			return nil, E.New("procfs: not found")
		}
	} else {
		if request.IpProtocol != syscall.IPPROTO_TCP && request.IpProtocol != syscall.IPPROTO_UDP {
			return nil, E.New("unknown network protocol: ", request.IpProtocol)
		}
		var err error
		uid, err = intfBox.FindConnectionOwner(request.IpProtocol, request.SourceAddress, request.SourcePort, request.DestinationAddress, request.DestinationPort)
		if err != nil {
			return nil, err
		}
	}
	packageName, _ := intfBox.PackageNameByUid(uid)
	return &adapter.ConnectionOwner{UserId: uid, AndroidPackageNames: []string{packageName}}, nil
}

// io.Writer

var disableSingBoxLog = false

func (w *boxPlatformInterfaceWrapper) Write(p []byte) (n int, err error) {
	// use bitsbox_log
	if !disableSingBoxLog {
		log.Print(string(p))
	}
	return len(p), nil
}

// 日志

type boxPlatformLogWriterWrapper struct {
}

var boxPlatformLogWriter sblog.PlatformWriter = &boxPlatformLogWriterWrapper{}

func (w *boxPlatformLogWriterWrapper) DisableColors() bool { return true }

func (w *boxPlatformLogWriterWrapper) WriteMessage(level uint8, message string) {
	if !strings.HasSuffix(message, "\n") {
		message += "\n"
	}
	bitsbox_log.LogWriter.Write([]byte(message))
}
