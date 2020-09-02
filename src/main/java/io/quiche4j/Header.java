package io.quiche4j;

public class Header {
    
    private final String name;
    private final String value;

    public Header(String name, String value) {
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