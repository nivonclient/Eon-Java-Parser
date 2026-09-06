package mrduck.simpleon;

public final class SimpleEonException extends RuntimeException {
    public SimpleEonException(String message) {
        super(message);
    }

    public SimpleEonException(String message, Throwable cause) {
        super(message, cause);
    }
}
