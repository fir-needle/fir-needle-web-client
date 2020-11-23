package fir.needle.web.websocket.client;

public abstract class AbstractWebSocketClientException extends RuntimeException {
    public AbstractWebSocketClientException() {
        super();
    }

    public AbstractWebSocketClientException(final String message) {
        super(message);
    }

    public AbstractWebSocketClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AbstractWebSocketClientException(final Throwable cause) {
        super(cause);
    }
}
