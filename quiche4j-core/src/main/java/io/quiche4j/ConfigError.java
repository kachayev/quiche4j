package io.quiche4j;

// xxx(okachaiev): provide more information about error based on the code
public class ConfigError extends Exception {
    
    private final int errorCode;

    public ConfigError(int errorCode) {
        super();
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public String toString() {
        return "Quiche Error Code: " + this.errorCode;
    }
}
