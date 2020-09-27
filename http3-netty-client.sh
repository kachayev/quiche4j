#!/usr/bin/env bash
set -eu -o pipefail

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
URL=${1:-"https://quic.tech:8443"}

java \
    -cp quiche4j-examples/target/quiche4j-examples-$VERSION.jar \
    io.quiche4j.examples.Http3NettyClient $URL