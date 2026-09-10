#!/bin/bash
# Builds the debug APK.
#
# Gradle runs on the JVM, which ignores the HTTP_PROXY/HTTPS_PROXY environment
# variables this shell uses. Google's Maven repository lives on dl.google.com,
# which is unreachable directly from this machine, so the proxy is passed
# explicitly as JVM system properties. Nothing machine-specific is written into
# the project's gradle.properties, so the project stays portable.
set -eu
cd "$(dirname "$0")/.."

GRADLE="$PWD/.gradle-dist/gradle-8.9/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  echo "gradle not unpacked -- run tools/setup-sdk.sh first" >&2
  exit 1
fi

PROXY_HOST="${PROXY_HOST:-127.0.0.1}"
PROXY_PORT="${PROXY_PORT:-10808}"

# Measured on this connection:
#   Maven Central  direct 673 KB/s  |  via proxy  11 KB/s
#   Google Maven   direct 404       |  via proxy 213 KB/s
# So Google's repository goes through the proxy and everything else bypasses it.
# The JDK's default ProxySelector applies http.nonProxyHosts to HTTPS as well.
NO_PROXY_HOSTS='localhost|127.0.0.1|jitpack.io|repo1.maven.org|repo.maven.apache.org|*.maven.org|*.apache.org|plugins.gradle.org|*.gradle.org|mirrors.cloud.tencent.com|*.tencent.com|repo.huaweicloud.com|*.huaweicloud.com|maven.aliyun.com|*.aliyun.com'

PROXY_ARGS=""
if [ "${NO_PROXY_BUILD:-0}" != "1" ]; then
  PROXY_ARGS="-Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT"
  PROXY_ARGS="$PROXY_ARGS -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
  PROXY_ARGS="$PROXY_ARGS -Dhttp.nonProxyHosts=$NO_PROXY_HOSTS"
fi

exec "$GRADLE" $PROXY_ARGS "${@:-assembleDebug}"
