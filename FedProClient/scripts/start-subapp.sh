#!/usr/bin/env bash
# 4/5 — Start the subscriber Sub-App (CuisSubAppRunner). Registers with CUIS
# over gRPC, subscribes to the object/interaction, and prints every callback
# CUIS routes to it ([DISCOVER]/[REFLECT]/[RECEIVE-INTERACTION]/[REMOVE]).
# NOT an HLA federate (a gRPC client of CUIS), so it does not count against the
# license federate limit. Runs in the foreground; Ctrl-C to stop.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

banner "Sub-App subscriber  ->  CUIS $CUIS_HOST:$GRPC_PORT  object=$OBJECT"

# Required upstream: CUIS gRPC must be listening (it only opens 50051 after a
# successful connect+join), so this also proves CUIS joined the federation.
wait_for_port "$CUIS_HOST" "$GRPC_PORT" "CUIS gRPC server" 180

cd "$JAVA_DIR"
exec $GRADLE :cuis-test:runSubApp \
  -PsubArgs="host=$CUIS_HOST port=$GRPC_PORT object=$OBJECT attrs=$ATTRS interaction=$INTERACTION"
