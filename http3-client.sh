#!/usr/bin/env bash
set -eu -o pipefail

URL=${1:-"http://quic.tech:8443"}

java \
    -cp quiche4j-examples/target/quiche4j-examples-*.jar \
    io.quiche4j.examples.Http3Client $URL  
