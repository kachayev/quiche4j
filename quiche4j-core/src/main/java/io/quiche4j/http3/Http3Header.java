package io.quiche4j.http3;

import io.quiche4j.Native;

public class Http3Header implements Native.Header {
    
    private final String name;
    private final String value;

    public Http3Header(String name, String value) {
        this.name = name.toLowerCase();
        this.value = value;
    }

    public final String getName() {
        return this.name;
    }

    public final String getValue() {
        return this.value;
    }

}