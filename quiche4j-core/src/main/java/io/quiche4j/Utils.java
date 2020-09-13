package io.quiche4j;

public class Utils {
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public final static String asHex(byte[] bytes) {
        if(null == bytes) return "";
        char[] hex = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;               // byte widened to int, need mask 0xff
            hex[j * 2] = HEX_ARRAY[v >>> 4];       // get upper 4 bits
            hex[j * 2 + 1] = HEX_ARRAY[v & 0x0F];  // get lower 4 bits
        }
        return new String(hex);
    }    
}