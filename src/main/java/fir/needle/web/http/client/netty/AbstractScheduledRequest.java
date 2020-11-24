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

import java.util.concurrent.atomic.AtomicBoolean;

import fir.needle.joint.io.ByteArea;
import fir.needle.joint.lang.Cancelable;
import fir.needle.joint.lang.Future;
import fir.needle.joint.lang.NoWaitFuture;
import fir.needle.joint.lang.VoidResult;
import fir.needle.joint.logging.Logger;
import fir.needle.web.http.client.AbstractHttpClientException;
import fir.needle.web.http.client.HttpRequest;
import fir.needle.web.http.client.UpdatableNoBodyRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;

abstract class AbstractScheduledRequest<R extends HttpRequest & UpdatableNoBodyRequest> extends AbstractRequest<R>
        implements NettyRequestHolder, Cancelable {

    private final EventLoopGroup eventLoopGroup;
    private final Object lock = new Object();

    long scheduledId;

    private final AtomicBoolean isCanceled = new AtomicBoolean(false);
    private volatile boolean isCancelDone;
    private int repeatPeriodMs;
    private Logger logger;

    private volatile Channel channel;

    AbstractScheduledRequest(final RequestBuilder builder) {
        super(builder);

        this.repeatPeriodMs = builder.repeatPeriodMs;
        this.scheduledId = builder.scheduledId;
        this.logger = builder.logger;
        this.eventLoopGroup = builder.client.eventLoopGroup;
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
    public void onDisconnectedByError(final AbstractHttpClientException exception) {
        super.onDisconnectedByError(exception);
    }

    @Override
    public Future<VoidResult> cancel() {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".cancel was called from the thread " + Thread.currentThread());
        }

        final Runnable cancelRequest = new Runnable() {
            @Override
            public void run() {
                if (logger.isTraceEnabled()) {
                    logger.trace(getClass().getSimpleName() + ".cancel canceling scheduled request" +
                            " in the thread " + Thread.currentThread());
                }
                isCanceled.set(true);
                client.cancelScheduledRequest(AbstractScheduledRequest.this);
            }
        };

        for (final EventExecutor eventExecutor : eventLoopGroup) {
            if (eventExecutor.inEventLoop()) {
                cancelRequest.run();
                return NoWaitFuture.INSTANCE;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".cancel was called not from eventLoop thread ");
        }

        eventLoopGroup.execute(cancelRequest);

        return new Future<VoidResult>() {
            @Override
            public VoidResult sync() throws InterruptedException {
                synchronized (lock) {
                    while (!isCancelDone) {
                        lock.wait();
                    }
                }

                if (logger.isTraceEnabled()) {
                    logger.trace(
                            getClass().getSimpleName() + ".cancel was finished and thread " + Thread.currentThread() +
                                    " has left wait");
                }

                return VoidResult.NO_ERROR_RESULT;
            }
        };
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
    public void connect(final Bootstrap bootstrap) {
        if (isCanceled.get()) {
            setCancelIsDone();
            return;
        }

        this.isCancelDone = false;
        this.channel = bootstrap.connect().channel();

        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channel finish setting channel into scheduled request " +
                    " in the thread " + Thread.currentThread());
        }
    }

    @Override
    public void setCancelIsDone() {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".setCancelIsDone verified scheduled request cancel " +
                    " in the thread " + Thread.currentThread());
        }

        isCancelDone = true;

        synchronized (lock) {
            lock.notifyAll();
        }
    }
}