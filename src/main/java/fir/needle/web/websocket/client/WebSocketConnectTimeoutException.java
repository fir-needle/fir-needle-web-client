package fir.needle.web.websocket.client;

public class WebSocketConnectTimeoutException extends AbstractWebSocketClientException {
    public WebSocketConnectTimeoutException() {
        super();
    }

    public WebSocketConnectTimeoutException(final String message) {
        super(message);
    }

    public WebSocketConnectTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public WebSocketConnectTimeoutException(final Throwable cause) {
        super(cause);
    }
}
