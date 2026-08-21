#!/bin/bash

set -e

DIR=app/src/main/assets/sing-box
mkdir -p $DIR
cd $DIR

# hanya update geoip/geosite, jaga yacd tetap dari repo

get_latest_release() {
  curl --silent "https://api.github.com/repos/$1/releases/latest" | # Get latest release from GitHub api
    grep '"tag_name":' |                                            # Get tag line
    sed -E 's/.*"([^"]+)".*/\1/'                                    # Pluck JSON value
}

download_assets() {
  local OWNER="$1"

  # Minimal variant: HANYA .srs files (3 files ~90 KB).
  # .db.xz untuk full variant di-download runtime saat user pilih di Manage Asset.

  # GeoIP: hanya geoip-id.srs untuk rule_set geoip:id
  VERSION_GEOIP=`get_latest_release "$OWNER/BITS-GeoIP"`
  echo VERSION_GEOIP=$VERSION_GEOIP
  curl -fLSso geoip-id.srs https://github.com/$OWNER/BITS-GeoIP/releases/download/$VERSION_GEOIP/geoip-id.srs
  echo -n $VERSION_GEOIP > geoip-id.version.txt

  # GeoSite: rule-ads & rule-indo untuk rule_set default
  VERSION_GEOSITE=`get_latest_release "$OWNER/BITS-GeoSite"`
  echo VERSION_GEOSITE=$VERSION_GEOSITE
  curl -fLSso geosite-rule-ads.srs https://github.com/$OWNER/BITS-GeoSite/releases/download/$VERSION_GEOSITE/geosite-rule-ads.srs
  echo -n $VERSION_GEOSITE > geosite-rule-ads.version.txt
  curl -fLSso geosite-rule-indo.srs https://github.com/$OWNER/BITS-GeoSite/releases/download/$VERSION_GEOSITE/geosite-rule-indo.srs
  echo -n $VERSION_GEOSITE > geosite-rule-indo.version.txt
}

download_assets "bitscoid"
