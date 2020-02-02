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
package fir.needle.web.websocket.client.netty;

import fir.needle.joint.logging.JulLogger;
import fir.needle.joint.logging.Logger;
import fir.needle.web.websocket.client.WebSocketListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class NettyWebSocketClient implements AutoCloseable {
    public static final int DEFAULT_CLOSE_TIMEOUT_MS = 5000;
    public static final int NO_RECONNECT = -1;
    public static final int UNLIMITED_RECONNECT = 0;

    final WebSocketVersion webSocketVersion;
    final String host;
    final int port;
    final SslContext sslContext;
    final HttpHeaders handshakeHeaders;

    final int connectTimeoutMs;
    final int readTimeoutMs;
    final int reconnectTimeoutMs;
    final int numberOfReconnectAttempts;

    final boolean isInternalEventLoopGroup;
    final EventLoopGroup eventLoopGroup;
    final Logger logger;

    private final Object lock = new Object();
    private final List<AutoCloseable> openedWebSockets = new ArrayList<>();
    private boolean isClosed;

    private NettyWebSocketClient(final NettyWebSocketClientBuilder builder) {
        this.webSocketVersion = builder.webSocketVersion;

        this.host = builder.host;
        this.port = builder.port;
        this.sslContext = builder.sslContext;
        this.handshakeHeaders = builder.handshakeHeaders;

        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.reconnectTimeoutMs = builder.reconnectTimeoutMs;
        this.numberOfReconnectAttempts = builder.numberOfReconnectAttempts;
        this.isInternalEventLoopGroup = builder.isInternalEventLoopGroup;
        this.eventLoopGroup = builder.eventLoopGroup;
        this.logger = builder.logger;
    }

    public static NettyWebSocketClientBuilder builder() {
        return new NettyWebSocketClientBuilder();
    }

    public AutoCloseable openConnection(final String path, final String query, final WebSocketListener listener) {
        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Is closed");
            }

            final NettyWebSocket newWebSocket;
            try {
                newWebSocket = new NettyWebSocket(this, path, query, listener, handshakeHeaders);
                openedWebSockets.add(newWebSocket);
                newWebSocket.connect();

                return newWebSocket;
            } catch (final URISyntaxException e) {
                throw new IllegalArgumentException("Illegal url", e);
            }
        }
    }

    public AutoCloseable openConnection(final String path, final WebSocketListener listener) {
        return openConnection(path, null, listener);
    }

    @Override
    public void close() throws InterruptedException {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".close has started for " + host + ':' + port + " in the thread " +
                            Thread.currentThread());
        }

        final List<AutoCloseable> copy;

        synchronized (lock) {
            if (isClosed) {
                return;
            }

            isClosed = true;
            copy = new ArrayList<>(openedWebSockets);
        }

        for (final AutoCloseable crtWebSocket : copy) {
            try {
                crtWebSocket.close();
            } catch (final Exception e) {
                logger.error("Error while closing opened WebSockets", e);
            }
        }

        if (isInternalEventLoopGroup) {
            eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        }

        if (logger.isInfoEnabled()) {
            logger.info("Client for " + host + ':' + port + " was closed in the thread " + Thread.currentThread());
        }
    }

    public static class NettyWebSocketClientBuilder {
        private WebSocketVersion webSocketVersion;
        private int numberOfWorkerThreads = 1;

        private String host;
        private int port;
        private boolean isSslEnabled;
        private SslContext sslContext;
        private HttpHeaders handshakeHeaders = new DefaultHttpHeaders();

        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        private int reconnectTimeoutMs = 15000;
        private int numberOfReconnectAttempts = NettyWebSocketClient.UNLIMITED_RECONNECT;

        private final Map<ChannelOption<Boolean>, Boolean> booleanOptions = new HashMap<>();
        private final Map<ChannelOption<Integer>, Integer> integerOptions = new HashMap<>();

        private boolean isInternalEventLoopGroup;
        private EventLoopGroup eventLoopGroup;
        private Logger logger;

        public NettyWebSocketClientBuilder() {

        }

        public NettyWebSocketClientBuilder withWebSocketVersion00() {
            webSocketVersion = WebSocketVersion.V00;
            return this;
        }

        public NettyWebSocketClientBuilder withWebSocketVersion07() {
            webSocketVersion = WebSocketVersion.V07;
            return this;
        }

        public NettyWebSocketClientBuilder withWebSocketVersion08() {
            webSocketVersion = WebSocketVersion.V08;
            return this;
        }

        public NettyWebSocketClientBuilder withNumberOfWorkerThreads(final int numberOfWorkerThreads) {
            this.numberOfWorkerThreads = numberOfWorkerThreads;
            return this;
        }

        //todo add ability to configure ssl (keystore etc.)
        public NettyWebSocketClientBuilder withSsl() {
            isSslEnabled = true;
            return this;
        }

        public NettyWebSocketClientBuilder withHandshakeHttpHeader(final CharSequence name, final CharSequence value) {
            handshakeHeaders.add(name, value);
            return this;
        }

        public NettyWebSocketClientBuilder withConnectTimeout(final int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public NettyWebSocketClientBuilder withReadTimeout(final int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public NettyWebSocketClientBuilder withReconnectTimeout(final int reconnectTimeoutMs) {
            this.reconnectTimeoutMs = reconnectTimeoutMs;
            return this;
        }

        public NettyWebSocketClientBuilder withNumberOfReconnectAttempts(final int numberOfReconnectAttempts) {
            this.numberOfReconnectAttempts = numberOfReconnectAttempts;
            return this;
        }

        public NettyWebSocketClientBuilder withNoReconnect() {
            numberOfReconnectAttempts = -1;
            return this;
        }

        public NettyWebSocketClientBuilder withOption(final ChannelOption<Boolean> option, final Boolean value) {
            booleanOptions.put(option, value);
            return this;
        }

        public NettyWebSocketClientBuilder withOption(final ChannelOption<Integer> option, final Integer value) {
            integerOptions.put(option, value);
            return this;
        }

        public NettyWebSocketClientBuilder withEventLoopGroup(final EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public NettyWebSocketClientBuilder withLogger(final Logger logger) {
            this.logger = logger;
            return this;
        }

        public NettyWebSocketClient build(final String host, final int port) {
            if (webSocketVersion == null) {
                webSocketVersion = WebSocketVersion.V13;
            }

            this.host = host;
            this.port = port;

            try {
                this.sslContext = isSslEnabled ? SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build() : null;
            } catch (final SSLException e) {
                throw new UncheckedIOException(e);
            }

            if (eventLoopGroup == null) {
                isInternalEventLoopGroup = true;
                eventLoopGroup = new NioEventLoopGroup(numberOfWorkerThreads);
            }

            if (logger == null) {
                logger = new JulLogger(java.util.logging.Logger.getLogger(NettyWebSocketClient.class.getSimpleName()));
            }

            return new NettyWebSocketClient(this);
        }
    }
}