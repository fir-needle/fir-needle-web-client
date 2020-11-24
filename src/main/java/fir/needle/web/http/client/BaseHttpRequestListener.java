package fir.needle.web.http.client;

import fir.needle.joint.io.ByteArea;

public class BaseHttpRequestListener<R extends HttpRequest> implements HttpResponseListener<R> {

    @Override
    public void onConnected(final R request) {
        //
    }

    @Override
    public void onBeforeRequestSent(final R request) {
        //
    }

    @Override
    public void onResponseStarted(final R request, final int code) {
        //
    }

    @Override
    public void onHeader(final CharSequence key, final CharSequence value) {
        //
    }

    @Override
    public void onBodyStarted() {
        //
    }

    @Override
    public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        //
    }

    @Override
    public void onBodyFinished() {
        //
    }

    @Override
    public void onResponseFinished() {
        //
    }

    @Override
    public void onListenerError(final Throwable error) {
        //
    }

    @Override
    public void onDisconnected(final R request) {
        //
    }

    @Override
    public void onDisconnectedByError(final R request, final AbstractHttpClientException exception) {
        //
    }
}
