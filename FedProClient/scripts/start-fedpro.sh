#!/usr/bin/env bash
# 2/5 — Start the Federate Protocol Server. Listens on 15164 and is the network
# entry point this repo's FedPro client connects to. Starts SEPARATELY from the
# CRC. Runs in the foreground; Ctrl-C to stop.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

banner "Federate Protocol Server  ->  $FEDPRO_HOST:$FEDPRO_PORT"
require_cmd FEDPRO_CMD "$FEDPRO_CMD" "PitchPrti6/bin/fedproserver" || exit 1

# Best-effort: the server reaches its co-located CRC per session, so we only
# warn (not fail) if the CRC isn't up yet.
wait_for_port "$CRC_HOST" "$CRC_PORT" "CRC" 60

exec $FEDPRO_CMD
