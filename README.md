# Quiche4j

Java implementation of the QUIC transport protocol and HTTP/3.

The library provides thin Java API layer on top of JNI calls to [quiche](https://github.com/cloudflare/quiche). Quiche4j provides a low level API for processing QUIC packets and handling connection state. The application is responsible for providing I/O (e.g. sockets handling) as well as timers. The library itself does not make any assumptions on how I/O layer is organized, making it's pluggle both into sycn and async architectures.

The main goal of the JNI bindings is to ensure high-performance:
* zero-copy data trasfer between runtimes where possible
* minimum allocations on Java side, pluggable buffer allocators

## Usage

To run HTTP3 client example:

```
$ mvn package
$ ./h3-client.sh http://quic.tech:8443
```

To run HTTP3 server example:

```
$ mvn package
$ ./h3-server.sh
```

## Implementation Details

* `Native.java` contains definition of native calls.

* JNI calls are implmeneted in Rust (see `src/main/rust/quiche_jni/` for more details) using `rust-jni` library.

* Most Java objects maintain a handle to corresponding Rust struct to maximise compatability with all Quiche features 

## TODO

- [] Cleanup pointers (finalizers)
- [] Version negotiation, retry, connection token validation
- [] Full streams API bindings
- [] Errors handling (covert between Rust's `Error::*` and return codes and/or exceptions when necessary)
- [] Find a good way to plug custom timers
- [] Better build script to provide linking for different platforms, optimized Rust build instead of debug
- [] Organize examples code
- [] Documentation (like... a lot)

## Copyright

Copyright (C) 2020, Oleksii Kachaiev.

See [COPYING](/COPYING) for the license.

See [cloudflare/quiche/copying](https://github.com/cloudflare/quiche/blob/master/COPYING) for Quiche license.