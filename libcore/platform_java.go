package libcore

var intfBox BoxPlatformInterface
var intfBITSBox BITSBoxInterface

var useProcfs bool
var isBgProcess bool

type BITSBoxInterface interface {
	UseOfficialAssets() bool
	Selector_OnProxySelected(selectorTag string, tag string)
}

type BoxPlatformInterface interface {
	AutoDetectInterfaceControl(fd int32) error
	OpenTun(singTunOptionsJson, tunPlatformOptionsJson string) (int, error)
	UseProcFS() bool
	FindConnectionOwner(ipProtocol int32, sourceAddress string, sourcePort int32, destinationAddress string, destinationPort int32) (int32, error)
	PackageNameByUid(uid int32) (string, error)
	UIDByPackageName(packageName string) (int32, error)
	WIFIState() string
	DefaultInterfaceName() string
	// NetworkInterfacesJSON enumerates the device network interfaces via
	// ConnectivityManager (framework API, not blocked by SELinux) as a JSON
	// array: [{"name","index","mtu","type","flags","dns":["..."],"metered"}].
	// gomobile only binds primitive types, hence the JSON string.
	NetworkInterfacesJSON() string
}
