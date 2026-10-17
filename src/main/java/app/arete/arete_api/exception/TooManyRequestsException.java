package app.arete.arete_api.exception;

public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(final String message) {
        super(message);
    }
}
