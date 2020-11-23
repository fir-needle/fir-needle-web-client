package fir.needle.web.websocket.client;

public class WebSocketHandShakeException extends AbstractWebSocketClientException {
    public WebSocketHandShakeException() {
        super();
    }

    public WebSocketHandShakeException(final String message) {
        super(message);
    }

    public WebSocketHandShakeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public WebSocketHandShakeException(final Throwable cause) {
        super(cause);
    }
}
