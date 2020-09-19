#!/usr/bin/env bash
set -eu -o pipefail

BIND=${1:-"localhost:4433"}

java \
    -cp quiche4j-netty/target/quiche4j-netty-*-SNAPSHOT.jar \
    io.quiche4j.netty.Http3Server $BIND