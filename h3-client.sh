#!/usr/bin/env bash
set -eu -o pipefail

URL=${1:-"http://quic.tech:8443"}

java \
    -cp target/quiche4j-0.1.0-SNAPSHOT.jar \
    io.quiche4j.examples.H3Client $URL  
