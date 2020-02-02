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

import fir.needle.joint.lang.Cancelable;
import fir.needle.joint.logging.JulLogger;
import fir.needle.joint.logging.Logger;
import fir.needle.web.http.client.Get;
import fir.needle.web.http.client.HttpClient;
import fir.needle.web.http.client.HttpResponseListener;
import fir.needle.web.http.client.PreparedDelete;
import fir.needle.web.http.client.PreparedGet;
import fir.needle.web.http.client.PreparedPatch;
import fir.needle.web.http.client.PreparedPost;
import fir.needle.web.http.client.PreparedPut;
import fir.needle.web.http.client.ScheduledGet;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public final class NettyHttpClient implements HttpClient {
    private final String host;
    private final int port;
    private final SslContext sslContext;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int reconnectTimeoutMs;
    final int reconnectAttemptsNumber;

    private final boolean isInternalEventLoopGroup;
    private final EventLoopGroup eventLoopGroup;

    private final List<Cancelable> scheduledTasks = new ArrayList<>();
    private final List<Long> scheduleIds = new CopyOnWriteArrayList<>();
    private final AtomicLong scheduleId = new AtomicLong();

    private final Map<ChannelOption<Boolean>, Boolean> booleanOptions;
    private final Map<ChannelOption<Integer>, Integer> integerOptions;

    private final Logger logger;
    private final Object lock = new Object();

    private boolean isClosed;

    private NettyHttpClient(final NettyHttpClientBuilder builder) {
        this.host = builder.host;
        this.port = builder.port;

        try {
            this.sslContext = builder.isSslEnabled ? SslContextBuilder.forClient()
                    .trustManager((File) null).build() : null;
        } catch (final SSLException e) {
            throw new UncheckedIOException(e);
        }

        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.reconnectTimeoutMs = builder.reconnectTimeoutMs;
        this.reconnectAttemptsNumber = builder.numberOfReconnectAttempts;

        this.isInternalEventLoopGroup = builder.isInternalEventLoopGroup;
        this.eventLoopGroup = builder.eventLoopGroup;

        this.booleanOptions = Collections.unmodifiableMap(builder.booleanOptions);
        this.integerOptions = Collections.unmodifiableMap(builder.integerOptions);

        this.logger = builder.logger;

        if (logger.isInfoEnabled()) {
            logger.info("Client for " + getRequestUrl(null) + " was built!");
        }
    }

    public static NettyHttpClientBuilder builder() {
        return new NettyHttpClientBuilder();
    }

    @Override
    public void get(final String path, final HttpResponseListener<Get> listener) {
        get(path, null, listener);
    }

    @Override
    public void get(final String path, final String query, final HttpResponseListener<Get> listener) {
        if (logger.isTraceEnabled()) {
            logger.trace("Trying to initiate connect to " + getRequestUrl(path) + " by the thread " +
                    Thread.currentThread());
        }

        createPreparedGet(path, query)
                .withHeader("host", host)
                .withHeader("connection", "keep-alive")
                .withHeader("accept-encoding", "gzip")
                .send(listener);
    }

    @Override
    public Cancelable scheduleGet(final String path, final int repeatPeriodMs,
            final HttpResponseListener<ScheduledGet> listener) {

        return scheduleGet(path, null, repeatPeriodMs, listener);
    }

    @Override
    public Cancelable scheduleGet(final String path, final String query, final int repeatPeriodMs,
            final HttpResponseListener<ScheduledGet> listener) {

        return createPreparedGet(path, query)
                .withHeader("host", host)
                .withHeader("connection", "keep-alive")
                .withHeader("accept-encoding", "gzip")
                .schedule(repeatPeriodMs, listener);
    }

    @Override
    public PreparedGet prepareGet(final String path) {
        return prepareGet(path, null);
    }

    @Override
    public PreparedGet prepareGet(final String path, final String query) {
        return createPreparedGet(path, query);
    }

    @Override
    public PreparedPost preparePost(final String path) {
        return preparePost(path, null);
    }

    @Override
    public PreparedPost preparePost(final String path, final String query) {
        return createPreparedPost(path, query);
    }

    @Override
    public PreparedPut preparePut(final String path) {
        return preparePut(path, null);
    }

    @Override
    public PreparedPut preparePut(final String path, final String query) {
        return createPreparedPut(path, query);
    }

    @Override
    public PreparedDelete prepareDelete(final String path) {
        return prepareDelete(path, null);
    }

    @Override
    public PreparedDelete prepareDelete(final String path, final String query) {
        return createPreparedDelete(path, query);
    }

    @Override
    public PreparedPatch preparePatch(final String path) {
        return preparePatch(path, null);
    }

    @Override
    public PreparedPatch preparePatch(final String path, final String query) {
        return createPreparedPatch(path, query);
    }

    @Override
    public void close() throws InterruptedException {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".close has started for " + getRequestUrl(null) + " in thread" +
                            Thread.currentThread());
        }

        final List<Cancelable> copy;

        synchronized (lock) {
            if (isClosed) {
                return;
            }

            isClosed = true;
            copy = new ArrayList<>(scheduledTasks);
        }

        for (final Cancelable scheduledRequest : copy) {
            try {
                scheduledRequest.cancel();
            } catch (final Exception e) {
                logger.error("Error while canceling scheduled request", e);
            }
        }

        if (isInternalEventLoopGroup) {
            eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        }

        if (logger.isInfoEnabled()) {
            logger.info("Client for " + getRequestUrl(null) + " was closed!");
        }
    }

    NettyPreparedGet createPreparedGet(final String path, final String query) {
        return new NettyPreparedGet()
                .withClient(this)
                .withHost(host)
                .withHttpVersion(HttpVersion.HTTP_1_1)
                .withHttpMethod(HttpMethod.GET)
                .withPath(path)
                .withQuery(query);
    }

    NettyPreparedPost createPreparedPost(final String path, final String query) {
        return new NettyPreparedPost()
                .withClient(this)
                .withHost(host)
                .withHttpVersion(HttpVersion.HTTP_1_1)
                .withHttpMethod(HttpMethod.POST)
                .withPath(path)
                .withQuery(query);
    }

    NettyPreparedPut createPreparedPut(final String path, final String query) {
        return new NettyPreparedPut()
                .withClient(this)
                .withHost(host)
                .withHttpVersion(HttpVersion.HTTP_1_1)
                .withHttpMethod(HttpMethod.PUT)
                .withPath(path)
                .withQuery(query);
    }

    NettyPreparedDelete createPreparedDelete(final String path, final String query) {
        return new NettyPreparedDelete()
                .withClient(this)
                .withHost(host)
                .withHttpVersion(HttpVersion.HTTP_1_1)
                .withHttpMethod(HttpMethod.DELETE)
                .withPath(path)
                .withQuery(query);
    }

    NettyPreparedPatch createPreparedPatch(final String path, final String query) {
        return new NettyPreparedPatch()
                .withClient(this)
                .withHost(host)
                .withHttpVersion(HttpVersion.HTTP_1_1)
                .withHttpMethod(HttpMethod.PATCH)
                .withPath(path)
                .withQuery(query);
    }

    void sendPreparedChain(final PreparedRequestsChain requestsChain) {
        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Trying to initiate connect to " + getRequestUrl(requestsChain.relativeUrl()) +
                                " by the thread " + Thread.currentThread());
            }

            eventLoopGroup.execute(new ConnectTask(requestsChain));
            scheduledTasks.add(requestsChain);
        }
    }

    void sendScheduledRequest(final AbstractScheduledRequest request) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".scheduleGet has started for " + request.relativeUrl() +
                    " in the thread " + Thread.currentThread());
        }

        final long crtScheduledId = scheduleId.incrementAndGet();

        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Trying to initiate connect to " + request.relativeUrl() + " by the thread " +
                        Thread.currentThread());
            }

            request.scheduledId = crtScheduledId;
            eventLoopGroup.execute(new ConnectTask(request));

            scheduledTasks.add(request);
            scheduleIds.add(crtScheduledId);
        }
    }

    void cancelScheduledRequest(final AbstractScheduledRequest request) {
        synchronized (lock) {
            if (!scheduleIds.remove(request.scheduledId)) {
                return;
            }

            scheduledTasks.remove(request);
        }
    }

    private String getRequestUrl(final String relativeUrl) {
        final String hostPort = host + ":" + port;

        if (relativeUrl == null) {
            return hostPort;
        }

        return hostPort + relativeUrl;
    }

    public static class NettyHttpClientBuilder {
        private boolean isInternalEventLoopGroup;
        private EventLoopGroup eventLoopGroup;

        private int numberOfWorkerThreads = 1;

        private String host;
        private int port;
        private boolean isSslEnabled;

        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        private int reconnectTimeoutMs = 15000;
        private int numberOfReconnectAttempts = 3;

        private final Map<ChannelOption<Boolean>, Boolean> booleanOptions = new HashMap<>();
        private final Map<ChannelOption<Integer>, Integer> integerOptions = new HashMap<>();

        private Logger logger;

        public NettyHttpClientBuilder() {
            //
        }

        public NettyHttpClientBuilder withEventLoopGroup(final EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public NettyHttpClientBuilder withNumberOfWorkerThreads(final int numberOfWorkerThreads) {
            this.numberOfWorkerThreads = numberOfWorkerThreads;
            return this;
        }

        public NettyHttpClientBuilder withSsl() {
            isSslEnabled = true;
            return this;
        }

        public NettyHttpClientBuilder withConnectTimeout(final int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public NettyHttpClientBuilder withReadTimeout(final int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public NettyHttpClientBuilder withReconnectTimeout(final int reconnectTimeoutMs) {
            this.reconnectTimeoutMs = reconnectTimeoutMs;
            return this;
        }

        public NettyHttpClientBuilder withNumberOfReconnectAttempts(final int numberOfReconnectAttempts) {
            this.numberOfReconnectAttempts = numberOfReconnectAttempts;
            return this;
        }

        public NettyHttpClientBuilder withNoReconnect() {
            numberOfReconnectAttempts = 0;
            return this;
        }

        public NettyHttpClientBuilder withOption(final ChannelOption<Boolean> option, final Boolean value) {
            booleanOptions.put(option, value);
            return this;
        }

        public NettyHttpClientBuilder withOption(final ChannelOption<Integer> option, final Integer value) {
            integerOptions.put(option, value);
            return this;
        }

        public NettyHttpClientBuilder withKeepAlive() {
            booleanOptions.put(ChannelOption.SO_KEEPALIVE, true);
            return this;
        }

        public NettyHttpClientBuilder withTcpNoDelay() {
            booleanOptions.put(ChannelOption.TCP_NODELAY, true);
            return this;
        }

        public NettyHttpClientBuilder withLogger(final Logger logger) {
            this.logger = logger;
            return this;
        }

        public NettyHttpClient build(final String host, final int port) {
            this.host = host;
            this.port = port;

            if (eventLoopGroup == null) {
                isInternalEventLoopGroup = true;
                eventLoopGroup = new NioEventLoopGroup(numberOfWorkerThreads);
            }

            if (logger == null) {
                logger = new JulLogger(java.util.logging.Logger.getLogger(NettyHttpClient.class.getSimpleName()));
            }

            return new NettyHttpClient(this);
        }
    }

    private class ConnectTask implements Runnable {
        final NettyRequestHolder requestHolder;

        private final Bootstrap bootstrap;

        ConnectTask(final NettyRequestHolder requestHolder) {
            this.requestHolder = requestHolder;

            bootstrap = new Bootstrap()
                    .channel(NioSocketChannel.class)
                    .group(eventLoopGroup)
                    .remoteAddress(host, port)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(final SocketChannel ch) {
                            fillPipeline(ch);
                        }
                    });

            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
            booleanOptions.forEach(bootstrap::option);
            integerOptions.forEach(bootstrap::option);
        }

        @Override
        public void run() {
            if (requestHolder.isCanceled()) {
                if (logger.isTraceEnabled()) {
                    logger.trace(
                            getClass().getSimpleName() + ".run request was canceled for " +
                                    requestHolder.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                    " in the channel " + requestHolder.channel().id() + " and in the thread " +
                                    Thread.currentThread());
                }
                return;
            }

            requestHolder.channel(bootstrap.connect().channel());
        }

        void fillPipeline(final SocketChannel channel) {
            final ChannelPipeline pipeline = channel.pipeline();

            if (sslContext != null) {
                pipeline.addLast(sslContext.newHandler(channel.alloc(), host, port));
            }

            pipeline.addLast(new HttpClientCodec());

            pipeline.addLast(new HttpContentDecompressor());

            pipeline.addLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));

            pipeline.addLast(new HttpNotificationHandler(requestHolder, logger, host, port));

            pipeline.addLast(new SendRequestHandler(requestHolder, logger));

            pipeline.addLast(new ReconnectHandler(this, requestHolder, reconnectTimeoutMs, logger));
        }
    }
}