/**
 * MIT License
 * <p>
 * Copyright (c) 2019 Nikita Vasilev
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package fir.needle.web.http.client.netty;

import fir.needle.joint.io.ByteAppendable;
import fir.needle.joint.io.ByteArea;
import fir.needle.web.http.client.HttpRequest;
import fir.needle.web.http.client.HttpResponseListener;
import fir.needle.web.http.client.RequestSender;
import fir.needle.web.http.client.UpdatableBodyRequest;
import fir.needle.web.http.client.UpdatableNoBodyRequest;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

abstract class AbstractRequest<R extends HttpRequest & UpdatableNoBodyRequest> implements HttpRequest,
        UpdatableBodyRequest<R>, NettyHttpRequest, NettyResponseListener {

    HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    HttpMethod httpMethod;

    String host;
    String path;
    String query;
    String relativeUrl;

    HttpHeaders headers;
    NettyOutputByteBuffer body;

    HttpResponseListener<R> listener;

    NettyHttpClient client;
    PreparedRequestsChain chain;

    long scheduledId;

    AbstractRequest(final RequestBuilder builder) {
        this.httpVersion = builder.httpVersion;
        this.httpMethod = builder.httpMethod;
        this.host = builder.host;
        this.path = builder.path;
        this.query = builder.query;
        this.headers = builder.headers;
        this.listener = builder.listener;
        this.client = builder.client;
        this.scheduledId = builder.scheduledId;
        this.chain = builder.chain;
        updateRelativeUrl();
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String query() {
        return query;
    }

    @Override
    public ByteAppendable updateBody(final CharSequence contentType, final int contentLength) {
        this.headers.add(HttpHeaderNames.CONTENT_TYPE, contentType);
        this.headers.add(HttpHeaderNames.CONTENT_LENGTH, contentLength);

        body = new NettyOutputByteBuffer(Unpooled.buffer(contentLength));

        return body;
    }

    @Override
    public R updatePath(final String path) {
        this.path = path;
        updateRelativeUrl();
        return (R) this;
    }

    @Override
    public R updateQuery(final String query) {
        this.query = query;
        updateRelativeUrl();
        return (R) this;
    }

    @Override
    public R updateHeader(final CharSequence name, final CharSequence value) {
        this.headers.set(name, value);
        return (R) this;
    }

    public void send() {
        chain.resendCurrentRequest();
    }

    @Override
    public String relativeUrl() {
        return relativeUrl;
    }

    public RequestSender client() {
        return client;
    }

    @Override
    public void onConnected() {
        listener.onConnected((R) this);
    }

    @Override
    public void onResponseStarted(final int code) {
        listener.onResponseStarted((R) this, code);
    }

    @Override
    public void onHeader(final CharSequence key, final CharSequence value) {
        listener.onHeader(key, value);
    }

    @Override
    public void onBodyStarted() {
        listener.onBodyStarted();
    }

    @Override
    public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        listener.onBodyContent(buffer, startIndex, length);
    }

    @Override
    public void onBodyFinished() {
        listener.onBodyFinished();
    }

    @Override
    public void onResponseFinished() {
        listener.onResponseFinished();
    }

    @Override
    public void onListenerError(final Throwable error) {
        listener.onListenerError(error);
    }

    @Override
    public void onDisconnected() {
        listener.onDisconnected((R) this);
    }

    @Override
    public void onDisconnectedByError(final String reason) {
        listener.onDisconnectedByError((R) this, reason);
    }

    private void updateRelativeUrl() {
        relativeUrl = query == null ? path : path + '?' + query;
    }
}
