package libcore

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
)

func bitsboxAndroidInboundRegistry() adapter.InboundRegistry { return include.InboundRegistry() }

func bitsboxAndroidOutboundRegistry() adapter.OutboundRegistry { return include.OutboundRegistry() }

func bitsboxAndroidEndpointRegistry() adapter.EndpointRegistry { return include.EndpointRegistry() }

func bitsboxAndroidDNSTransportRegistry(localTransport LocalDNSTransport) adapter.DNSTransportRegistry {
	registry := include.DNSTransportRegistry()
	if localTransport != nil {
		dns.RegisterTransport(registry, "local", func(ctx context.Context, logger log.ContextLogger, tag string, options option.LocalDNSServerOptions) (adapter.DNSTransport, error) {
			return newPlatformTransport(localTransport, tag, options), nil
		})
	}
	return registry
}

func bitsboxAndroidServiceRegistry() adapter.ServiceRegistry { return include.ServiceRegistry() }
