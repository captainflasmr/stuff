#!/usr/bin/env bash
# 5/5 — Start the publishing federate (CuisHlaPublisher). Registers a Vehicle
# and updates Position/Speed on a timer, driving the reflect round-trip that
# the Sub-App observes. HLA federate #2 (publisher + CUIS = the 2 the license
# allows). Runs in the foreground; Ctrl-C to stop.
#
# Started last on purpose: we wait for the Sub-App's subscription to be in
# place so the very first updates are already routed.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

banner "HLA publisher  ->  rti=$FEDPRO_HOST:$FEDPRO_PORT  object=$OBJECT  every ${PUB_PERIOD_MS}ms"

# It connects directly to the FedPro server; also wait for CUIS to be up so
# there is a subscriber to receive the reflects.
wait_for_port "$FEDPRO_HOST" "$FEDPRO_PORT" "Federate Protocol Server" 120
wait_for_port "$CUIS_HOST"  "$GRPC_PORT"    "CUIS gRPC server"        180
# Give the Sub-App a moment to finish its Subscribe_Object_Attributes call.
sleep 3

cd "$JAVA_DIR"
exec $GRADLE :cuis-test:runPublisher \
  -PpubArgs="rti=$FEDPRO_HOST:$FEDPRO_PORT object=$OBJECT periodMs=$PUB_PERIOD_MS"
