# Quiche4j

Java implementation of the QUIC transport protocol and HTTP/3.

The library provides thin Java API layer on top of JNI calls to [quiche](https://github.com/cloudflare/quiche). `Quiche4j` provides a low level API for processing QUIC packets and handling connection state. The application is responsible for providing I/O (e.g. sockets handling) as well as timers. The library itself does not make any assumptions on how I/O layer is organized, making it's pluggle into different architectures.

The main goal of the JNI bindings is to ensure high-performance and flexibility for the application developers while maintaining full access to `quiche` library features. Specifically, the bindings layer tries to ensure zero-copy data trasfer between runtimes where possible and perform minimum allocations on Java side.

## Usage

Maven:

```xml
<dependencies>
    <dependency>
        <groupId>io.quiche4j</groupId>
        <artifactId>quiche4j-core</artifactId>
        <version>0.2.3</version>
    </dependency>
    <dependency>
        <groupId>io.quiche4j</groupId>
        <artifactId>quiche4j-jni</artifactId>
        <classifier>linux_x64_86</classifier>
        <version>0.2.3</version>
    </dependency>
</dependencies>
```

Note that `quiche4j-jni` contains native library and should be installed with proper classifier. [`os-maven-plugin`](https://github.com/trustin/os-maven-plugin) could be used to simplify classifier detection

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.6.1</version>
        </extension>
    </extensions>
</build>
<dependencies>
    <dependency>
        <groupId>io.quiche4j</groupId>
        <artifactId>quiche4j-jni</artifactId>
        <classifier>${os.detected.classifier}</classifier>
        <version>0.2.3</version>
    </dependency>
</dependencies>
```

## Building

`Quiche4j` requires `cargo` and Rust 1.39+ to build. The latest stable Rust release can be installed using [rustup](https://rustup.rs/). Once the Rust build environment is setup,

```bash
$ git clone https://github.com/kachayev/quiche4j
$ mvn clean install
```

## Run Examples

Run HTTP3 client example:

```bash
$ ./http3-client.sh https://quic.tech:8443
> sending request to https://quic.tech:8443
> handshake size: 1200
> socket.recieve 167 bytes
> conn.recv 167 bytes
...
! conn is closed recv=10 sent=12 lost=0 rtt=95 cwnd=14520 delivery_rate=1436
```

Run HTTP3 server example:

```bash
$ ./http3-server.sh :4433
! listening on localhost:4433
```

## Compile Manually

Maven project is setup to automatically compile JNI library and include the result of the compilation into the `quiche4j-jni` JAR. Even thought this method is convenient for distribution, it might lack flexibility. To compile JNI manually follow the next steps,

```bash
$ git clone https://github.com/kachayev/quiche4j
$ cargo build --release --manifest-path quiche4j-jni/Cargo.toml
$ mvn clean install
$ java \
    -Djava.library.path=quiche4j-jni/target/release/ \
    -cp quiche4j-examples/target/quiche4j-examples-*.jar \
    io.quiche4j.examples.Http3Server
```

The code would try to load native libraries from `java.library.path` first, using built-in artifact as a fallback only.

For cross-compilation options, see `cargo build` [documentation](https://doc.rust-lang.org/cargo/commands/cargo-build.html).

## API

### Connection

Before establishing a QUIC connection, you need to create a configuration object:

```java
import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;

final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
```

On the client-side the `Quiche.connect` utility function can be used to create a new connection, while `Quiche.accept` is for servers:

```java
// client
final byte[] connId = Quiche.newConnectionId();
// note, that "quic.tech" here is not used for establishing network
// connection. it's used only for peer verification (thus, optional)
final Connection conn = Quiche.connect("quic.tech", connId, config);

// server
final Connection conn = Quiche.accept(sourceConnId, originalDestinationId, config);
```

### Incoming packets

Using the connection's `recv` method the application can process incoming packets that belong to that connection from the network:

```java
final byte[] buf = new byte[1350];
while(true) {
    DatagramPacket packet = new DatagramPacket(buf, buf.length);
    try {
        // read from the socket
        socket.receive(packet);
        final byte[] buffer = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());
        // update the connection state
        final int read = conn.recv(buffer);
        if(read <= 0) break;
    } catch (SocketTimeoutException e) {
        conn.onTimeout();
        break;
    }
}
```

### Outgoing packets

Outgoing packet are generated using the connection's `send` method instead:

```java
final byte[] buf = new byte[1350];
while(true) {
    // get data that's need to be sent based on the connection state
    final int len = conn.send(buf);
    if (len <= 0) break;
    final DatagramPacket packet = new DatagramPacket(buf, len, address, port);
    // send it to the network
    socket.send(packet);
}
```

### Timers

The application is responsible for maintaining a timer to react to time-based connection events. When a timer expires, the connection's `onTimeout` method should be called, after which additional packets might need to be sent on the network:

```java
// handle timer
conn.onTimeout();

// sending corresponding packets
final byte[] buf = new byte[1350];
while(true) {
    final int len = conn.send(buf);
    if (len <= 0) break;
    final DatagramPacket packet = new DatagramPacket(buf, len, address, port);
    socket.send(packet);
}
```

### Streams Data

After some back and forth, the connection will complete its handshake and will be ready for sending or receiving application data.

Data can be sent on a stream by using the `streamSend` method:

```java
if(conn.isEstablished()) {
    // handshake completed, send some data on stream 0
    conn.streamSend(0, "hello".getBytes(), true);
}
```

The application can check whether there are any readable streams by using the connection's `readable` method, which returns an iterator over all the streams that have outstanding data to read.

The `streamRecv` method can then be used to retrieve the application data from the readable stream:

```java
if(conn.isEstablished()) {
    final byte[] buf = new byte[1350]; 
    for(long streamId: conn.readable()) {
        // stream <streamId> is readable, read until there's no more data
        while(true) {
            final int len = conn.streamRecv(streamId, buf);
            if(len <= 0) break;
        }
    }
}
```

## HTTP/3

The library provides a high level API for sending and receiving HTTP/3 requests and responses on top of the QUIC transport protocol.

### Connection

HTTP/3 connections require a QUIC transport-layer connection, see ["Connection"](#Connection) for a full description of the setup process. To use HTTP/3, the QUIC connection must be configured with a suitable ALPN Protocol ID:

```java
import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.http3.Http3Connection;

final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
    .withApplicationProtos(Http3.APPLICATION_PROTOCOL)
    .build();
```

The QUIC handshake is driven by sending and receiving QUIC packets. Once the handshake has completed, the first step in establishing an HTTP/3 connection is creating its configuration object:

```java
import io.quiche4j.http3.Http3Config;
import io.quiche4j.http3.Http3ConfigBuilder;

final Http3Config h3Config = new Http3ConfigBuilder().build();
```

HTTP/3 client and server connections are both created using the `Http3Connection.withTransport` function:

```java
import io.quiche4j.http3.Http3Connection;

final Http3Connection h3Conn = Http3Connection.withTransport(conn, h3Config);
```

### Sending Request

An HTTP/3 client can send a request by using the connection's `sendRequest` method to queue request headers; sending QUIC packets causes the requests to get sent to the peer:

```java
import io.quiche4j.http3.Http3Header;

List<Http3Header> req = new ArrayList<>();
req.add(new Http3Header(":method", "GET"));
req.add(new Http3Header(":scheme", "https"));
req.add(new Http3Header(":authority", "quic.tech"));
req.add(new Http3Header(":path", "/"));
req.add(new Http3Header("user-agent", "Quiche4j"));
h3Conn.sendRequest(req, true);
```

An HTTP/3 client can send a request with additional body data by using the connection's `sendBody` method:

```java
final long streamId = h3Conn.sendRequest(req, false);
h3Conn.sendBody(streamId, "Hello there!".getBytes(), true);
```

### Handling Responses

After receiving QUIC packets, HTTP/3 data is processed using the connection's `poll` method.

An HTTP/3 server uses `poll` to read requests and responds to them, an HTTP/3 client uses `poll` to read responses. `poll` method accepts object that implements `Http3EventListener` interface defining callbacks for different type of events 

```java
import io.quiche4j.http3.Http3EventListener;
import io.quiche4j.http3.Http3Header;

final long streamId = h3Conn.poll(new Http3EventListener() {
    public void onHeaders(long streamId, List<Http3Header> headers) {
        // got headers
    }

    public void onData(long streamId) {
        // got body
        final byte[] body = new byte[MAX_DATAGRAM_SIZE];
        final int len = h3Conn.recvBody(streamId, body);
    }

    public void onFinished(long streamId) {
        // done with this stream
        conn.close(true, 0x00, "Bye! :)".getBytes()));
    }
});

if(Quiche.ErrorCode.DONE == streamId) {
    // this means no event was emitted
    // it would take more packets to proceed with new events
}
```

Note that `poll` would either execute callbacks and returns immediately. If there's not enough data to fire any of the events, `poll` immediately returns `Quiche.ErrorCode.DONE`. The application is responsible for handling incoming packets from the network and feeding packets data into connection before executing next `poll`.

### Examples

Have a look at the [quiche4j-examples](quiche4j-examples/src/main/java/io/quiche4j/examples/) folder for more complete examples on how to use the Quiche4j API to work with HTTP/3 protocol.

### Errors Hanlding

Native JNI code propagates errors using return codes (typically the return code < 0 means either DONE or failed). For example, [`quiche::Error`](https://github.com/cloudflare/quiche/blob/204d693bb543e12a605073181ae605eacb743039/src/lib.rs#L320-L365) enum. `Quiche4j` follows the same convention instead of throwing Java exceptions to ensure good perfomance and compatibility with async runtimes (catching exception in async environemnt might be somewhat problematic). See [`Quiche.ErrorCode`](src/main/java/io/quiche4j/Quiche.java) and [`Http3.ErrorCode`](src/main/java/io/quiche4j/http3/Http3.java) for more details.

Unlike other methods, `Quiche.connect` and `Quiche.accept` throw `ConnectionFailureException` if JNI code failed before `quiche::Connection` struct had been allocated. In this case there's no pointer to carry around, thus Java code does not create `Connection` object.

## Implementation Details

* Modules [Native.java](src/main/java/io/quiche4j/Native.java) and [Http3Native.java](src/main/java/io/quiche4j/http3/Http3Native.java) contains definition of all native calls, structurally close to `quiche`'s [`src/ffi.rs`](https://github.com/cloudflare/quiche/blob/master/src/ffi.rs) and [`src/h3/ffi.rs`](https://github.com/cloudflare/quiche/blob/master/src/h3/ffi.rs) respectively.

* JNI calls are implmeneted in Rust (see [quiche4j-jni](quiche4j-jni/) for more details) using [`rust-jni`](https://docs.rs/jni/0.17.0/jni/) library. The goal was to stick to primitive types as much as possible and avoid Java objects manipulations in native code. There are still a few exceptions from this rule, e.g. operations with connection `Stats`, management of `Http3Header` lists, etc.

* Proxy Java objects maintain a handle (pointer) to the corresponding Rust struct to maximise compatability with all `quiche` features. A single instance of a `Cleaner` is statically defined in `io.quiche4j.Native` class and is used to register all deallocation callback (conventionally called `free` for each class that maintains a native pointer).

## TODO

There are still a few `xxx` comments in the code. Both for Java and for Rust. Plus, there are a few methods that are not exposed to Java layer. Notably, operations with stream priorities and HTTP/3 connection configuration (some of those would require to extend `quiche` library as well).

Other ideas to work on:

- [ ] Propagate Rust panics into Java exceptions (when necessary)
- [ ] Setup integration testing suite against different QUIC implementations out there
- [ ] Qlog support
- [ ] Experiment with in-memory serialization (Arrow?) to deal with (presumably) high overhead of manipulating objects in native code

## Copyright

Copyright (C) 2020, Oleksii Kachaiev.

See [COPYING](/COPYING) for the license.

See [cloudflare/quiche/copying](https://github.com/cloudflare/quiche/blob/master/COPYING) for Quiche license.