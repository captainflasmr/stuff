#!/usr/bin/env bash
# 1/5 — Start the CRC (pRTI). First process to bring up; everything else waits
# on it. Runs in the foreground; Ctrl-C to stop.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

banner "CRC (pRTI)  ->  $CRC_HOST:$CRC_PORT"
require_cmd CRC_CMD "$CRC_CMD" "PitchPrti6/bin/prti" || exit 1

exec $CRC_CMD
