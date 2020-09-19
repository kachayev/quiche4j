package io.quiche4j.http3;

public class Http3Config {

    private final long ptr;

    protected Http3Config(long ptr) {
        this.ptr = ptr;
    }

    protected final long getPointer() {
        return this.ptr;
    }

}