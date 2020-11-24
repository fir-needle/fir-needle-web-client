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

import fir.needle.joint.logging.Logger;
import fir.needle.web.http.client.HttpResponseListener;
import fir.needle.web.http.client.NoBodyRequestBuilder;
import fir.needle.web.http.client.PreparableRequestsFactory;
import fir.needle.web.http.client.PreparedDelete;
import fir.needle.web.http.client.PreparedPatch;
import fir.needle.web.http.client.PreparedPost;
import fir.needle.web.http.client.PreparedPut;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

class RequestBuilder<P extends NoBodyRequestBuilder> implements PreparableRequestsFactory {

    HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    HttpMethod httpMethod;

    String host;
    String path;
    String query;

    HttpHeaders headers;
    HttpResponseListener listener;

    long scheduledId;
    int repeatPeriodMs;

    NettyHttpClient client;
    PreparedRequestsChain chain;

    Logger logger;

    public P withHttpVersion(final String httpVersion) {
        this.httpVersion = HttpVersion.valueOf(httpVersion);
        return (P) this;
    }

    public P withPath(final String path) {
        this.path = path;
        return (P) this;
    }

    public P withQuery(final String query) {
        this.query = query;
        return (P) this;
    }

    public P withHeader(final CharSequence name, final CharSequence value) {
        if (headers == null) {
            headers = new DefaultHttpHeaders();
        }

        headers.add(name, value);
        return (P) this;
    }

    @Override
    public NettyPreparedGet prepareGet(final String path) {
        return prepareGet(path, null);
    }

    @Override
    public NettyPreparedGet prepareGet(final String path, final String query) {
        final NettyPreparedGet result = client.createPreparedGet(path, query);
        result.chain = chain;
        return result;
    }

    @Override
    public PreparedPost preparePost(final String path) {
        return preparePost(path, null);
    }

    @Override
    public PreparedPost preparePost(final String path, final String query) {
        final NettyPreparedPost result = client.createPreparedPost(path, query);
        result.chain = chain;
        return result;
    }

    @Override
    public PreparedPut preparePut(final String path) {
        return preparePut(path, null);
    }

    @Override
    public PreparedPut preparePut(final String path, final String query) {
        final NettyPreparedPut result = client.createPreparedPut(path, query);
        result.chain = chain;
        return result;
    }

    @Override
    public PreparedDelete prepareDelete(final String path) {
        return prepareDelete(path, null);
    }

    @Override
    public PreparedDelete prepareDelete(final String path, final String query) {
        final NettyPreparedDelete result = client.createPreparedDelete(path, query);
        result.chain = chain;
        return result;
    }

    @Override
    public PreparedPatch preparePatch(final String path) {
        return preparePatch(path, null);
    }

    @Override
    public PreparedPatch preparePatch(final String path, final String query) {
        final NettyPreparedPatch result = client.createPreparedPatch(path, query);
        result.chain = chain;
        return result;
    }

    P withClient(final NettyHttpClient client) {
        this.client = client;
        return (P) this;
    }

    P withHttpVersion(final HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
        return (P) this;
    }

    P withHttpMethod(final HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
        return (P) this;
    }

    P withHost(final String host) {
        this.host = host;
        return (P) this;
    }

    P withRepeatPeriodMs(final int repeatPeriodMs) {
        this.repeatPeriodMs = repeatPeriodMs;
        return (P) this;
    }

    P withLogger(final Logger logger) {
        this.logger = logger;
        return (P) this;
    }
}