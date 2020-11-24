package fir.needle.web.http.client;

public abstract class AbstractHttpClientException extends RuntimeException {

    public AbstractHttpClientException() {
        super();
    }

    public AbstractHttpClientException(final String message) {
        super(message);
    }

    public AbstractHttpClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AbstractHttpClientException(final Throwable cause) {
        super(cause);
    }
}
