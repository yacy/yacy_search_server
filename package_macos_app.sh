#!/usr/bin/env bash

set -euo pipefail

# -----------------------------------------------------------------------------
# YaCy macOS Packaging Workflow (standalone script)
#
# Prerequisites:
#   - macOS host with Xcode command-line tools (needed for hdiutil/iconutil/sips)
#   - Ant/Java toolchain already used for YaCy builds
#   - Python 3 (only for resolving absolute paths)
#
# Typical Flow:
#   1. Run this script (it defaults to invoking `ant clean all dist` and uses the
#      newest RELEASE/yacy_v*.tar.gz). Pass --skip-ant when reusing an existing
#      tarball.
#   2. The script assembles YaCy.app plus YaCy-<version>.dmg directly inside RELEASE/.
#   3. On first launch the .app copies its bundled DATA directory into
#      ~/Library/YaCy/DATA and always starts YaCy with `-gui ~/Library/YaCy`,
#      matching the GUI info dialog. Existing data is preserved on subsequent runs.
#
# Useful options:
#   --skip-ant              Skip invoking Ant before packaging.
#   -r/--release-tar FILE   Use a specific RELEASE/yacy_v*.tar.gz file.
#   -o/--output-dir DIR     Where to place the resulting .app/.dmg (default RELEASE/).
#   -n/--app-name NAME      Display name for the macOS bundle (default YaCy).
#   --volume-name NAME      DMG volume label.
#   --payload-name NAME     Subdirectory name under Contents/Resources inside the .app.
#   -i/--icon FILE          Override the default addon/YaCy.icns icon.
#   --keep-work             Preserve the staging folder for debugging.
#   --ant-cmd CMD           Alternate Ant executable/binary path.
#
# Customizing the launcher:
#   The generated launcher lives at YaCy.app/Contents/MacOS/<app-name>. It changes
#   into the embedded payload, ensures ~/Library/YaCy/DATA exists (copying defaults
#   on first run), then runs Java with `net.yacy.yacy -gui ~/Library/YaCy`.
#   To tweak JVM flags or the data directory, edit this script's launcher section
#   (search for JAVA_ARGS) before rebuilding, or modify the generated launcher in
#   the .app bundle directly.
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}"
RELEASE_DIR="${PROJECT_ROOT}/RELEASE"
DEFAULT_ICON="${PROJECT_ROOT}/addon/YaCy.icns"
OUTPUT_DIR="${RELEASE_DIR}"
APP_NAME="YaCy"
VOLUME_NAME="YaCy"
PAYLOAD_DIR_NAME="YaCy"
RUN_ANT_BUILD=1
ANT_CMD="ant"
ANT_TARGETS=("clean" "all" "dist")
RELEASE_TAR=""
ICON_PATH=""
KEEP_WORK=0

usage() {
  cat <<'EOF'
Usage: ./package_macos_app.sh [options]

Build the standard YaCy distribution with Ant (unless --skip-ant is provided),
wrap the resulting release into a macOS .app bundle, and create a .dmg.

Options:
  -r, --release-tar <file>   Path to an existing yacy_v*.tar.gz to wrap.
                             Defaults to the newest tarball under RELEASE/.
  -i, --icon <file>          Custom .icns file for the app icon.
  -o, --output-dir <dir>     Directory for the .app and .dmg (default RELEASE/).
  -n, --app-name <name>      Name displayed for the .app bundle (default YaCy).
      --volume-name <name>   Volume name for the DMG (default YaCy).
      --payload-name <name>  Directory name used inside Contents/Resources (default YaCy).
      --skip-ant             Do not run "ant clean all dist" automatically.
      --ant-cmd <command>    Ant executable to invoke (default ant).
      --keep-work            Keep the temporary staging directory (for debugging).
  -h, --help                 Show this help message.
EOF
}

abs_path() {
  python3 - <<'PY' "$1"
import os, sys
print(os.path.abspath(sys.argv[1]))
PY
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -r|--release-tar)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      RELEASE_TAR="$(abs_path "$2")"
      shift 2
      ;;
    -i|--icon)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      ICON_PATH="$(abs_path "$2")"
      shift 2
      ;;
    -o|--output-dir)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      OUTPUT_DIR="$(abs_path "$2")"
      shift 2
      ;;
    -n|--app-name)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      APP_NAME="$2"
      shift 2
      ;;
    --volume-name)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      VOLUME_NAME="$2"
      shift 2
      ;;
    --payload-name)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      PAYLOAD_DIR_NAME="$2"
      shift 2
      ;;
    --skip-ant)
      RUN_ANT_BUILD=0
      shift
      ;;
    --ant-cmd)
      [[ $# -lt 2 ]] && { echo "Missing value for $1" >&2; exit 1; }
      ANT_CMD="$2"
      shift 2
      ;;
    --keep-work)
      KEEP_WORK=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ${RUN_ANT_BUILD} -eq 1 ]]; then
  echo "Running ${ANT_CMD} ${ANT_TARGETS[*]}..."
  (cd "${PROJECT_ROOT}" && "${ANT_CMD}" "${ANT_TARGETS[@]}")
fi

if [[ -z "${RELEASE_TAR}" ]]; then
  set +e
  latest_tar="$(ls -t "${RELEASE_DIR}"/yacy_v*.tar.gz 2>/dev/null | head -n 1)"
  status=$?
  set -e
  if [[ ${status} -ne 0 || -z "${latest_tar}" ]]; then
    echo "No release tarball found under ${RELEASE_DIR}. Run 'ant clean all dist' first." >&2
    exit 1
  fi
  RELEASE_TAR="$(abs_path "${latest_tar}")"
fi

if [[ ! -f "${RELEASE_TAR}" ]]; then
  echo "Release tarball not found: ${RELEASE_TAR}" >&2
  exit 1
fi

if [[ -z "${ICON_PATH}" ]]; then
  if [[ -f "${DEFAULT_ICON}" ]]; then
    ICON_PATH="${DEFAULT_ICON}"
  else
    echo "Warning: default icon not found (${DEFAULT_ICON}); the app bundle will not include an icon."
    ICON_PATH=""
  fi
fi

if [[ -n "${ICON_PATH}" && ! -f "${ICON_PATH}" ]]; then
  echo "Icon file not found: ${ICON_PATH}" >&2
  exit 1
fi

if ! command -v hdiutil >/dev/null 2>&1; then
  echo "hdiutil is required to create a DMG. Please run this script on macOS." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"
WORK_DIR="$(mktemp -d "${PROJECT_ROOT}/macos_app_work.XXXXXX")"

cleanup() {
  if [[ ${KEEP_WORK} -eq 0 ]]; then
    rm -rf "${WORK_DIR}" 2>/dev/null || true
  else
    echo "Keeping work directory at ${WORK_DIR}"
  fi
}
trap cleanup EXIT

echo "Using release tarball: ${RELEASE_TAR}"
tar -xzf "${RELEASE_TAR}" -C "${WORK_DIR}"

PAYLOAD_SOURCE="$(find "${WORK_DIR}" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
if [[ -z "${PAYLOAD_SOURCE}" ]]; then
  echo "Could not find the extracted YaCy payload inside ${WORK_DIR}" >&2
  exit 1
fi

APP_BUNDLE_SOURCE="${WORK_DIR}/${APP_NAME}.app"
CONTENTS_DIR="${APP_BUNDLE_SOURCE}/Contents"
MACOS_DIR="${CONTENTS_DIR}/MacOS"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"

mkdir -p "${MACOS_DIR}" "${RESOURCES_DIR}"

PAYLOAD_TARGET="${RESOURCES_DIR}/${PAYLOAD_DIR_NAME}"
cp -R "${PAYLOAD_SOURCE}/." "${PAYLOAD_TARGET}"

if [[ -n "${ICON_PATH}" ]]; then
  ICON_NAME="$(basename "${ICON_PATH}")"
  cp "${ICON_PATH}" "${RESOURCES_DIR}/${ICON_NAME}"
else
  ICON_NAME=""
fi

RELEASE_STUB="$(basename "${RELEASE_TAR}")"
RELEASE_STUB="${RELEASE_STUB%.tar.gz}"
APP_VERSION="${RELEASE_STUB#yacy_v}"

INFO_PLIST="${CONTENTS_DIR}/Info.plist"
cat > "${INFO_PLIST}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>English</string>
  <key>CFBundleExecutable</key>
  <string>${APP_NAME}</string>
  <key>CFBundleIconFile</key>
EOF
if [[ -n "${ICON_NAME}" ]]; then
  cat >> "${INFO_PLIST}" <<EOF
  <string>${ICON_NAME}</string>
EOF
else
  cat >> "${INFO_PLIST}" <<'EOF'
  <string></string>
EOF
fi
cat >> "${INFO_PLIST}" <<EOF
  <key>CFBundleIdentifier</key>
  <string>net.yacy.search</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>${APP_NAME}</string>
  <key>CFBundleDisplayName</key>
  <string>${APP_NAME}</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>${APP_VERSION}</string>
  <key>CFBundleVersion</key>
  <string>${APP_VERSION}</string>
  <key>LSMinimumSystemVersion</key>
  <string>10.13</string>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
EOF

LAUNCHER="${MACOS_DIR}/${APP_NAME}"
cat > "${LAUNCHER}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
YACY_HOME="${APP_ROOT}/Resources/%PAYLOAD_DIR%"
cd "${YACY_HOME}"

USER_DATA_HOME="${HOME}/Library/YaCy"
TEMPLATE_DATA="${YACY_HOME}/DATA"
mkdir -p "${USER_DATA_HOME}"

if [[ ! -d "${USER_DATA_HOME}/DATA" ]]; then
  echo "Initializing YaCy data directory at ${USER_DATA_HOME}/DATA"
  if [[ -d "${TEMPLATE_DATA}" ]]; then
    if command -v rsync >/dev/null 2>&1; then
      rsync -a "${TEMPLATE_DATA}/" "${USER_DATA_HOME}/DATA/"
    else
      mkdir -p "${USER_DATA_HOME}/DATA"
      cp -R "${TEMPLATE_DATA}/." "${USER_DATA_HOME}/DATA/"
    fi
  else
    mkdir -p "${USER_DATA_HOME}/DATA"
  fi
fi

JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"
elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  if [[ -n "${JAVA_HOME}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
  fi
fi

if [[ -z "${JAVA_BIN}" ]]; then
  echo "Unable to find a Java runtime. Please install Java (JDK 11+) and try again." >&2
  exit 1
fi

JAVA_ARGS=()
JAVA_ARGS+=(-server)
JAVA_ARGS+=(-Dfile.encoding=UTF-8)
JAVA_ARGS+=(-Djava.awt.headless=false)

if uname -m | grep -q 64; then
  JAVA_ARGS+=(-Dsolr.directoryFactory=solr.MMapDirectoryFactory)
fi

CONFIG_FILE="DATA/SETTINGS/yacy.conf"
if [[ -f "${CONFIG_FILE}" ]]; then
  XMX="$(grep -E '^javastart_Xmx=' "${CONFIG_FILE}" | sed 's/^[^=]*=//' | tail -n 1)"
  if [[ -n "${XMX}" ]]; then
    JAVA_ARGS=("-${XMX}" "${JAVA_ARGS[@]}")
  else
    JAVA_ARGS=("-Xmx600m" "${JAVA_ARGS[@]}")
  fi
else
  JAVA_ARGS=("-Xmx600m" "${JAVA_ARGS[@]}")
fi

exec "${JAVA_BIN}" "${JAVA_ARGS[@]}" -classpath "lib/*" net.yacy.yacy -gui "${USER_DATA_HOME}"
EOF
perl -0pi -e "s/%PAYLOAD_DIR%/${PAYLOAD_DIR_NAME//\//\\/}/" "${LAUNCHER}"
chmod +x "${LAUNCHER}"

FINAL_APP="${OUTPUT_DIR}/${APP_NAME}.app"
rm -rf "${FINAL_APP}"
mv "${APP_BUNDLE_SOURCE}" "${FINAL_APP}"

DMG_NAME="${RELEASE_STUB}.dmg"
DMG_PATH="${OUTPUT_DIR}/${DMG_NAME}"
rm -f "${DMG_PATH}"
echo "Creating DMG at ${DMG_PATH}"
hdiutil create -volname "${VOLUME_NAME}" -srcfolder "${FINAL_APP}" -ov -format UDZO "${DMG_PATH}" >/dev/null

echo "macOS app created:"
echo "  App bundle: ${FINAL_APP}"
echo "  DMG:        ${DMG_PATH}"
