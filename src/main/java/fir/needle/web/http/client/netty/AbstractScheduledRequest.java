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
import fir.needle.web.http.client.HttpRequest;
import fir.needle.web.http.client.UpdatableNoBodyRequest;
import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractScheduledRequest<R extends HttpRequest & UpdatableNoBodyRequest> extends AbstractRequest<R>
        implements NettyRequestHolder, Cancelable {

    long scheduledId;

    private AtomicBoolean isCanceled = new AtomicBoolean(false);
    private volatile boolean isCancelDone;
    private int repeatPeriodMs;

    private Channel channel;
    private Thread callingThread;

    private final Object lock = new Object();

    AbstractScheduledRequest(final RequestBuilder builder) {
        super(builder);

        this.repeatPeriodMs = builder.repeatPeriodMs;
        this.scheduledId = builder.scheduledId;
    }

    @Override
    public void onConnected() {
        if (isCanceled.get()) {
            return;
        }

        super.onConnected();
    }

    @Override
    public void onResponseStarted(final int code) {
        if (isCanceled.get()) {
            return;
        }

        super.onResponseStarted(code);
    }

    @Override
    public void onHeader(final CharSequence key, final CharSequence value) {
        if (isCanceled.get()) {
            return;
        }

        super.onHeader(key, value);
    }

    @Override
    public void onBodyStarted() {
        if (isCanceled.get()) {
            return;
        }

        super.onBodyStarted();
    }

    @Override
    public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        if (isCanceled.get()) {
            return;
        }

        super.onBodyContent(buffer, startIndex, length);
    }

    @Override
    public void onBodyFinished() {
        if (isCanceled.get()) {
            return;
        }

        super.onBodyFinished();
    }

    @Override
    public void onResponseFinished() {
        if (isCanceled.get()) {
            return;
        }

        super.onResponseFinished();
    }

    @Override
    public void onListenerError(final Throwable error) {
        if (isCanceled.get()) {
            return;
        }

        super.onListenerError(error);
    }

    @Override
    public void onDisconnected() {
        /*we need this signal to know what is going on with our connection,
        to avoid receiving onDisconnected any time connection is lost/closed use adapters for listeners
        (for example SingleConnectSingleDisconnect)*/
        super.onDisconnected();
    }

    @Override
    public void onDisconnectedByError(final String reason) {
        super.onDisconnectedByError(reason);
    }

    @Override
    public void cancel() throws InterruptedException {
        isCanceled.set(true);
        client.cancelScheduledRequest(this);

        if (isCancelDone) {
            return;
        }

        if (Thread.currentThread() != callingThread) {
            synchronized (lock) {
                while (!isCancelDone) {
                    lock.wait();
                }
            }
        }
    }

    @Override
    public NettyResponseListener listener() {
        return this;
    }

    @Override
    public long currentRequestDelayMs() {
        return repeatPeriodMs;
    }

    @Override
    public boolean isCanceled() {
        return isCanceled.get();
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void channel(final Channel channel) {
        this.channel = channel;
        this.callingThread = Thread.currentThread();
    }

    @Override
    public void setCancelIsDone() {
        isCancelDone = true;

        synchronized (lock) {
            lock.notifyAll();
        }
    }
}