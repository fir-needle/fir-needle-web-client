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

import fir.needle.joint.io.ByteArea;
import fir.needle.joint.lang.Cancelable;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class PreparedRequestsChain implements NettyRequestHolder, NettyResponseListener, Cancelable {
    private static final long ZERO_DELAY = 0;
    private final List<AbstractRequest> requestsChain = new ArrayList<>();
    private final int reconnectAttemptsNumber;

    private int crtRequestIndex = -1;
    private int reconnectCounter;
    private AbstractRequest crtRequest;
    private boolean isResend;
    private boolean wasResponseFinished;
    private AtomicBoolean isCanceled = new AtomicBoolean(false);
    private Channel channel;

    PreparedRequestsChain(final int reconnectAttemptsNumber) {
        this.reconnectAttemptsNumber = reconnectAttemptsNumber;
    }

    @Override
    public HttpRequest get() {
        if (crtRequestIndex >= requestsChain.size() - 1 && !isResend) {
            return null;
        }

        if (!isResend) {
            crtRequestIndex++;
        }

        crtRequest = requestsChain.get(crtRequestIndex);

        return (HttpRequest) crtRequest.get();
    }

    @Override
    public String relativeUrl() {
        return crtRequest.relativeUrl();
    }

    @Override
    public String method() {
        return crtRequest.method();
    }

    @Override
    public String path() {
        return crtRequest.path();
    }

    @Override
    public String query() {
        return crtRequest.query();
    }

    @Override
    public NettyResponseListener listener() {
        return this;
    }

    @Override
    public long currentRequestDelayMs() {
        return ZERO_DELAY;
    }

    @Override
    public boolean isCanceled() {
        return !isResend && crtRequestIndex >= requestsChain.size() - 1 && wasResponseFinished ||
                reconnectCounter > reconnectAttemptsNumber || isCanceled.get();
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void channel(final Channel channel) {
        this.channel = channel;
    }

    @Override
    public void setCancelIsDone() {
        //
    }

    boolean isEmpty() {
        return requestsChain.isEmpty();
    }

    PreparedRequestsChain add(final AbstractRequest request) {
        if (crtRequest == null) {
            crtRequest = request;
        }

        requestsChain.add(request);

        return this;
    }

    @Override
    public void onConnected() {
        reconnectCounter = 0;
        crtRequest.onConnected();
    }

    @Override
    public void onResponseStarted(final int code) {
        wasResponseFinished = false;
        crtRequest.onResponseStarted(code);
    }

    @Override
    public void onHeader(final CharSequence key, final CharSequence value) {
        crtRequest.onHeader(key, value);
    }

    @Override
    public void onBodyStarted() {
        crtRequest.onBodyStarted();
    }

    @Override
    public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        crtRequest.onBodyContent(buffer, startIndex, length);
    }

    @Override
    public void onBodyFinished() {
        crtRequest.onBodyFinished();
    }

    @Override
    public void onResponseFinished() {
        wasResponseFinished = true;
        isResend = false;
        crtRequest.onResponseFinished();
    }

    @Override
    public void onListenerError(final Throwable error) {
        crtRequest.listener.onListenerError(error);
    }

    @Override
    public void onDisconnected() {
        if (!wasResponseFinished) {
            isResend = true;
            reconnectCounter++;
        }

        crtRequest.onDisconnected();
    }

    @Override
    public void onDisconnectedByError(final String reason) {

    }

    void resendCurrentRequest() {
        isResend = true;
    }

    @Override
    public void cancel() {
        isCanceled.set(true);
    }
}
