package fir.needle.web.http.client;

public class HttpReadTimeoutException extends AbstractHttpClientException {

    public HttpReadTimeoutException() {
        super();
    }

    public HttpReadTimeoutException(final String message) {
        super(message);
    }

    public HttpReadTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public HttpReadTimeoutException(final Throwable cause) {
        super(cause);
    }
}
