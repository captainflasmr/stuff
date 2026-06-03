#!/usr/bin/env bash
# Quick health check: which tier of the stack is currently up.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

check() {  # host port label
  if _port_open "$1" "$2"; then printf '  [ UP ]  %-26s %s:%s\n' "$3" "$1" "$2"
  else            printf '  [down]  %-26s %s:%s\n' "$3" "$1" "$2"; fi
}

banner "status"
check "$CRC_HOST"    "$CRC_PORT"    "CRC (pRTI)"
check "$FEDPRO_HOST" "$FEDPRO_PORT" "Federate Protocol Server"
check "$CUIS_HOST"   "$GRPC_PORT"   "CUIS gRPC (proxy federate)"
echo
