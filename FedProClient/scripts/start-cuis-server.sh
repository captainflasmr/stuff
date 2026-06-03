#!/usr/bin/env bash
# 3/5 — Start the CUIS proxy federate. Joins the federation through the FedPro
# server (15164) and exposes the Sub-App gRPC API (50051). HLA federate #1.
# Runs in the foreground; Ctrl-C to stop.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

banner "CUIS server  ->  rti=$FEDPRO_HOST:$FEDPRO_PORT  gRPC=$GRPC_PORT"

# Required upstream: the FedPro server must be listening or connect() fails
# (this is the ConnectionFailed / UnknownHostException class of error).
wait_for_port "$FEDPRO_HOST" "$FEDPRO_PORT" "Federate Protocol Server" 120

cd "$JAVA_DIR"
exec $GRADLE :cuis-server:run \
  --args="$FEDERATION $FOM $FEDPRO_HOST:$FEDPRO_PORT $GRPC_PORT"
