package fir.needle.web.http.client;

public class HttpConnectTimeoutException extends AbstractHttpClientException {

    public HttpConnectTimeoutException() {
        super();
    }

    public HttpConnectTimeoutException(final String message) {
        super(message);
    }

    public HttpConnectTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public HttpConnectTimeoutException(final Throwable cause) {
        super(cause);
    }
}
