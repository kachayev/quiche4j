package io.quiche4j.http3;

public class Http3Header {
    
    private final String name;
    private final String value;

    public Http3Header(String name, String value) {
        this.name = name.toLowerCase();
        this.value = value;
    }

    public final String name() {
        return this.name;
    }

    public final String value() {
        return this.value;
    }

}