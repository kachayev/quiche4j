#!/usr/bin/env bash
set -eu -o pipefail

BIND=${1:-"localhost:4433"}

java \
    -Djava.library.path=quiche4j-jni/target/release/ \
    -cp quiche4j-examples/target/quiche4j-examples-0.2.0-SNAPSHOT.jar \
    io.quiche4j.examples.H3Server $BIND