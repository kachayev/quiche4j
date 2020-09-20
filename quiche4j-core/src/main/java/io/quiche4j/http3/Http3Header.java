package io.quiche4j.http3;

/**
 * A name-value pair representing a raw HTTP header.
 */
public class Http3Header {
    
    private final String name;
    private final String value;

    /**
     * Creates a new header.
     *
     * Note that {@code name} will be converted into lower-case.
     */
    public Http3Header(String name, String value) {
        this.name = name.toLowerCase();
        this.value = value;
    }

    /**
     * Returns the header's name.
     */    
    public final String name() {
        return this.name;
    }

    /**
     * Returns the header's value.
     */    
    public final String value() {
        return this.value;
    }

}