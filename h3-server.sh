#!/usr/bin/env bash
set -eu -o pipefail

BIND=${1:-"localhost:4433"}

java \
    -cp target/quiche4j-0.1.0-SNAPSHOT.jar \
    io.quiche4j.examples.H3Server $BIND