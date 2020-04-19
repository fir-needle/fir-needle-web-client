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
package fir.needle.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import fir.needle.joint.lang.Cancelable;
import fir.needle.joint.lang.Future;
import fir.needle.joint.lang.NoWaitFuture;
import fir.needle.joint.lang.VoidResult;
import fir.needle.web.http.client.netty.NettyHttpClient;
import fir.needle.web.websocket.client.netty.NettyWebSocketClient;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

public class NettyWebClient implements AutoCloseable {
    private static final int DEFAULT_NUMBER_OF_WORKER_THREADS = 1;

    private final Object lock = new Object();
    private final EventLoopGroup eventLoopGroup;

    private volatile boolean isClosed;

    private final List<Cancelable> scheduledTasks = new ArrayList<>();
    private final List<AutoCloseable> openedClients = new ArrayList<>();

    public NettyWebClient() {
        eventLoopGroup = new NioEventLoopGroup(DEFAULT_NUMBER_OF_WORKER_THREADS);
    }

    public NettyWebClient(final int numberOfWorkerThreads) {
        eventLoopGroup = new NioEventLoopGroup(numberOfWorkerThreads);
    }

    public void execute(final Runnable task) {
        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }

            eventLoopGroup.execute(task);
        }
    }

    public Cancelable schedule(final Runnable task, final int repeatPeriodMs) {
        final CancelableTask result = new CancelableTask(task, repeatPeriodMs);

        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }

            eventLoopGroup.execute(result);
            scheduledTasks.add(result);
        }

        return result;
    }

    public NettyHttpClient.NettyHttpClientBuilder httpClientBuilder() {
        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }

            return new HttpClientBuilder().withEventLoopGroup(eventLoopGroup);
        }
    }

    public NettyWebSocketClient.NettyWebSocketClientBuilder webSocketBuilder() {
        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }
            return new WebSocketClientBuilder().withEventLoopGroup(eventLoopGroup);
        }
    }

    @Override
    public void close() throws Exception {
        final List<AutoCloseable> copyOpenedClients;
        final List<Cancelable> copyScheduledTasks;

        synchronized (lock) {
            if (isClosed) {
                return;
            }

            isClosed = true;
            copyOpenedClients = new ArrayList<>(openedClients);
            copyScheduledTasks = new ArrayList<>(scheduledTasks);
        }

        for (final Cancelable crtScheduledTask : copyScheduledTasks) {
            crtScheduledTask.cancel();
        }

        for (final AutoCloseable crtClient : copyOpenedClients) {
            crtClient.close();
        }

        eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
    }

    private class WebSocketClientBuilder extends NettyWebSocketClient.NettyWebSocketClientBuilder {

        @Override
        public NettyWebSocketClient build(final String host, final int port) {
            final NettyWebSocketClient result = super.build(host, port);

            synchronized (lock) {
                if (isClosed) {
                    throw new IllegalStateException("Is closed");
                }

                openedClients.add(result);
            }

            return result;
        }
    }

    private class HttpClientBuilder extends NettyHttpClient.NettyHttpClientBuilder {
        public NettyHttpClient build(final String host, final int port) {
            final NettyHttpClient result = super.build(host, port);

            synchronized (lock) {
                if (isClosed) {
                    throw new IllegalStateException("Is closed");
                }

                openedClients.add(result);
            }

            return result;
        }
    }

    private final class CancelableTask implements Runnable, Cancelable {
        private final Runnable task;
        private final int repeatPeriodMs;
        private volatile boolean isCanceled;

        private CancelableTask(final Runnable task, final int repeatPeriodMs) {
            this.task = task;
            this.repeatPeriodMs = repeatPeriodMs;
        }

        @Override
        public Future<VoidResult> cancel() {
            isCanceled = true;

            return NoWaitFuture.INSTANCE;
        }

        @Override
        public void run() {
            task.run();

            eventLoopGroup.schedule(() -> {
                if (!isCanceled) {
                    run();
                }
            }, repeatPeriodMs, TimeUnit.MILLISECONDS);
        }
    }
}