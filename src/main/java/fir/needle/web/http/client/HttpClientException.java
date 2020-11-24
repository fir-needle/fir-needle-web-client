package fir.needle.web.http.client;

public class HttpClientException extends AbstractHttpClientException {

    public HttpClientException() {
        super();
    }

    public HttpClientException(final String message) {
        super(message);
    }

    public HttpClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public HttpClientException(final Throwable cause) {
        super(cause);
    }
}
