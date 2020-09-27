#!/usr/bin/env bash
set -eu -o pipefail

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
BIND=${1:-"localhost:4433"}

java \
    -cp quiche4j-examples/target/quiche4j-examples-$VERSION.jar \
    io.quiche4j.examples.Http3Server $BIND