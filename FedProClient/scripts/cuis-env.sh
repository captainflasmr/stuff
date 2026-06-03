#!/usr/bin/env bash
#
# Shared configuration + helpers for the CUIS run scripts.
# Sourced by every start-*.sh script; not meant to be run directly.
#
# Override anything below WITHOUT editing this file by creating a
# cuis-env.local.sh next to it (it is sourced last and is git-ignored),
# or by exporting the variable in your shell before launching.

# --- Resolve locations relative to this file (works from any checkout path) ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"      # the gradle root (.../java)

# --- pRTI launchers (NOT in this repo — set these to your install) -----------
# These two are platform-specific; see the pRTI 6.1.3 User's Guide, the
# "Federate Protocol Server" section. If the defaults are wrong the start
# scripts will tell you exactly what to set.
PRTI_HOME="${PRTI_HOME:-/opt/PitchPrti6}"
# Command that starts the CRC (pRTI). Often the pRTI GUI launcher.
CRC_CMD="${CRC_CMD:-$PRTI_HOME/bin/prti}"
# Command that starts the Federate Protocol Server (starts SEPARATELY from the
# CRC and listens on 15164). Adjust the basename to your install if needed.
FEDPRO_CMD="${FEDPRO_CMD:-$PRTI_HOME/bin/fedproserver}"
# The Federate Protocol Server must run as root. start-fedpro.sh launches it
# via this; the OTHER processes deliberately stay unprivileged (running gradle
# as root would litter the build/.gradle cache with root-owned files). Set to
# "" if you already launch that terminal as root, or to e.g. "sudo -E".
FEDPRO_SUDO="${FEDPRO_SUDO:-sudo}"

# --- Hosts / ports -----------------------------------------------------------
CRC_HOST="${CRC_HOST:-localhost}";      CRC_PORT="${CRC_PORT:-8989}"
FEDPRO_HOST="${FEDPRO_HOST:-localhost}"; FEDPRO_PORT="${FEDPRO_PORT:-15164}"
CUIS_HOST="${CUIS_HOST:-localhost}";    GRPC_PORT="${GRPC_PORT:-50051}"

# --- Federation / FOM / model names ------------------------------------------
FEDERATION="${FEDERATION:-CUIS}"
FOM="${FOM:-CuisFom.xml}"               # resolved relative to cuis-server/
OBJECT="${OBJECT:-Vehicle}"
ATTRS="${ATTRS:-Position,Speed}"
INTERACTION="${INTERACTION:-Alert}"
PUB_PERIOD_MS="${PUB_PERIOD_MS:-1000}"

# --- Gradle invocation -------------------------------------------------------
GRADLE="${GRADLE:-./gradlew --offline --console=plain}"

# --- Site overrides (git-ignored) --------------------------------------------
[ -f "$SCRIPT_DIR/cuis-env.local.sh" ] && source "$SCRIPT_DIR/cuis-env.local.sh"

# === Helpers =================================================================

# Pretty banner so each terminal clearly identifies itself.
banner() { printf '\n=== CUIS :: %s ===\n\n' "$*"; }

# Return 0 if a TCP connect to host:port succeeds. Host-aware (works for remote
# pRTI too). Uses bash's /dev/tcp; falls back to ss for the local case.
_port_open() {  # host port
  if (exec 3<>"/dev/tcp/$1/$2") 2>/dev/null; then exec 3>&- 3<&-; return 0; fi
  command -v ss >/dev/null 2>&1 &&
    ss -ltnH 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$2\$"
}

# Block until host:port accepts a connection, or warn and continue on timeout.
# wait_for_port HOST PORT DESC [TIMEOUT_SECS]
wait_for_port() {
  local host="$1" port="$2" desc="$3" timeout="${4:-90}" i
  printf 'Waiting for %s (%s:%s, up to %ss)...\n' "$desc" "$host" "$port" "$timeout"
  for ((i = 0; i < timeout; i++)); do
    if _port_open "$host" "$port"; then printf '  %s is up.\n' "$desc"; return 0; fi
    sleep 1
  done
  printf '  WARNING: %s not reachable after %ss — continuing anyway.\n' "$desc" "$timeout" >&2
  return 1
}

# Verify a configured launcher exists before trying to run it.
# require_cmd VAR_NAME COMMAND HINT
require_cmd() {
  local var="$1" cmd="$2" hint="$3"
  # Accept an executable on PATH, or any existing path (it may be root-only to
  # execute, which is fine — we launch it via sudo).
  if command -v "$cmd" >/dev/null 2>&1 || [ -e "$cmd" ]; then return 0; fi
  cat >&2 <<EOF

ERROR: $var points at '$cmd', which was not found / is not executable.

  Set it to your pRTI launcher, e.g. create $SCRIPT_DIR/cuis-env.local.sh with:
      $var="/path/to/$hint"

  (See the pRTI 6.1.3 User's Guide, "Federate Protocol Server" section.)

EOF
  return 1
}
