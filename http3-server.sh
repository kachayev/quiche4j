#!/usr/bin/env bash
set -eu -o pipefail

BIND=${1:-"localhost:4433"}

java \
    -cp quiche4j-examples/target/quiche4j-examples-*.jar \
    io.quiche4j.examples.Http3Server $BIND