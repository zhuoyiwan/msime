#!/bin/bash
# 无付费开发者证书的本机安装：只签名当前输入法，不改系统 Gatekeeper 策略。
set -euo pipefail

identity_name="MSIME zhuoyiwan Local Code Signing"
source_dir="$(cd "$(dirname "$0")" && pwd)"
source_app="${MSIME_LOCAL_SOURCE_APP:-$source_dir/MSIME.app}"
destination_root="${MSIME_LOCAL_APPLICATIONS_DIR:-$HOME/Applications}"
destination="$destination_root/MSIME-zhuoyiwan.app"
keychain="${MSIME_LOCAL_KEYCHAIN:-$(security default-keychain -d user | sed 's/^[[:space:]]*"//;s/"[[:space:]]*$//')}"

if [ "$(uname -s)" != Darwin ] || [ ! -d "$source_app/Contents" ]; then
  echo "请在 Mac 上挂载 DMG，再从 DMG 内运行本脚本。"
  exit 1
fi
if [ "$source_app" = "$destination" ]; then
  echo "请从下载的 DMG 内运行脚本，不能用已安装的副本替换自身。"
  exit 1
fi

mkdir -p "$destination_root"
work="$(mktemp -d "$destination_root/.msime-local-install.XXXXXX")"
backup=""
installed=0
cleanup() {
  status=$?
  if [ "$status" -ne 0 ] && [ "$installed" -eq 1 ]; then
    rm -rf "$destination"
    if [ -n "$backup" ] && [ -d "$backup" ]; then mv "$backup" "$destination"; fi
  fi
  rm -rf "$work"
  exit "$status"
}
trap cleanup EXIT

identity="$(security find-identity -v -p codesigning "$keychain" |
  sed -n "/\"$identity_name\"/s/^[[:space:]]*[0-9]*)[[:space:]]*\([0-9A-F]\{40\}\).*/\1/p" | head -1)"
if [ -z "$identity" ]; then
  echo "正在创建本机专用签名。macOS 可能要求允许访问钥匙串和信任此证书。"
  umask 077
  cat > "$work/certificate.conf" <<'CONFIG'
[req]
distinguished_name = subject
x509_extensions = extensions
prompt = no
[subject]
CN = MSIME zhuoyiwan Local Code Signing
OU = zhuoyiwan local installation
[extensions]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
subjectKeyIdentifier = hash
CONFIG
  openssl req -new -newkey rsa:3072 -nodes -x509 -sha256 -days 3650 \
    -config "$work/certificate.conf" -keyout "$work/private.key" -out "$work/certificate.pem" 2>"$work/openssl.log"
  export MSIME_LOCAL_P12_PASSWORD
  MSIME_LOCAL_P12_PASSWORD="$(openssl rand -hex 24)"
  openssl pkcs12 -export -inkey "$work/private.key" -in "$work/certificate.pem" \
    -name "$identity_name" -out "$work/local.p12" -passout env:MSIME_LOCAL_P12_PASSWORD
  security import "$work/local.p12" -k "$keychain" -P "$MSIME_LOCAL_P12_PASSWORD" -T /usr/bin/codesign
  unset MSIME_LOCAL_P12_PASSWORD
  security add-trusted-cert -r trustRoot -p codeSign -k "$keychain" "$work/certificate.pem"
  identity="$(openssl x509 -in "$work/certificate.pem" -noout -fingerprint -sha1 | cut -d= -f2 | tr -d ':')"
fi

cat > "$work/local.entitlements" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>com.apple.security.device.audio-input</key><true/>
<key>com.apple.security.cs.disable-library-validation</key><true/>
</dict></plist>
PLIST
# 本机证书没有 Apple Team ID，允许此进程加载自己随包带的运行库；不授予全局权限。
ditto "$source_app" "$work/MSIME-zhuoyiwan.app"
staged="$work/MSIME-zhuoyiwan.app"
xattr -dr com.apple.quarantine "$staged" 2>/dev/null || true
codesign --force --deep --options runtime --entitlements "$work/local.entitlements" \
  --keychain "$keychain" --sign "$identity" "$staged"
codesign --verify --deep --strict "$staged"

if [ -d "$destination" ]; then
  backup="$work/previous.app"
  mv "$destination" "$backup"
fi
installed=1
mv "$staged" "$destination"
echo "已安装本机签名的设置应用：$destination"
if [ "${MSIME_LOCAL_VERIFY_ONLY:-0}" = 1 ]; then
  # CI 只验证签名和可执行文件，不能冒充登录桌面的输入法验收。
  "$destination/Contents/MacOS/msime-mcp" --version
else
  open "$destination"
  echo "在打开的设置窗口点「立即安装」，再在系统设置的键盘输入源中添加水杉输入法。"
  echo "首次安装如找不到输入源，请退出登录并重新登录。此包未经 Apple 公证。"
fi
