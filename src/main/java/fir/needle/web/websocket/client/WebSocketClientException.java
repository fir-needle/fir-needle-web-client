package fir.needle.web.websocket.client;

public class WebSocketClientException extends AbstractWebSocketClientException {
    public WebSocketClientException() {
        super();
    }

    public WebSocketClientException(final String message) {
        super(message);
    }

    public WebSocketClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public WebSocketClientException(final Throwable cause) {
        super(cause);
    }
}
