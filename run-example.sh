#!/bin/bash -e
EXAMPLE_MAP=(
  'http3-client:io.quiche4j.examples.H3Client'
  'http3-server:io.quiche4j.examples.H3Server'
)

EXAMPLE=''
EXAMPLE_CLASS=''
EXAMPLE_ARGS='-D_'
I=0

while [[ $# -gt 0 ]]; do
  ARG="$1"
  shift
  if [[ "$ARG" =~ (^-.+) ]]; then
    EXAMPLE_ARGS="$EXAMPLE_ARGS $ARG"
  else
    EXAMPLE="$ARG"
    for E in "${EXAMPLE_MAP[@]}"; do
      KEY="${E%%:*}"
      VAL="${E##*:}"
      if [[ "$EXAMPLE" == "$KEY" ]]; then
        EXAMPLE_CLASS="$VAL"
        break
      fi
    done
    break
  fi
done

if [[ -z "$EXAMPLE" ]] || [[ -z "$EXAMPLE_CLASS" ]] || [[ $# -ne 0 ]]; then
  echo "  Usage: $0 [-D<name>[=<value>] ...] <example-name>" >&2
  echo "Example: $0 -Dport=8443 -Dssl http-server" >&2
  echo "         $0 -Dhost=127.0.0.1 -Dport=8009 echo-client" >&2
  echo "         $0 -DlogLevel=debug -Dhost=127.0.0.1 -Dport=8009 echo-client" >&2
  echo >&2
  echo "Available examples:" >&2
  echo >&2
  I=0
  for E in "${EXAMPLE_MAP[@]}"; do
    if [[ $I -eq 0 ]]; then
      echo -n '  '
    fi

    printf '%-24s' "${E%%:*}"
    ((I++)) || true

    if [[ $I -eq 2 ]]; then
      I=0
      echo
    fi
  done >&2
  if [[ $I -ne 0 ]]; then
    echo >&2
  fi
  echo >&2
  exit 1
fi

cd "`dirname "$0"`"/quiche4j-examples
echo "[INFO] Running: $EXAMPLE ($EXAMPLE_CLASS $EXAMPLE_ARGS)"
exec mvn exec:java \
  -Djava.library.path="../quiche4j-jni/target/release/" \
  -Dexec.mainClass="$EXAMPLE_CLASS"