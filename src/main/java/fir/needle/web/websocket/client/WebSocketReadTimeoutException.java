package fir.needle.web.websocket.client;

public class WebSocketReadTimeoutException extends AbstractWebSocketClientException {
    public WebSocketReadTimeoutException() {
        super();
    }

    public WebSocketReadTimeoutException(final String message) {
        super(message);
    }

    public WebSocketReadTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public WebSocketReadTimeoutException(final Throwable cause) {
        super(cause);
    }
}
