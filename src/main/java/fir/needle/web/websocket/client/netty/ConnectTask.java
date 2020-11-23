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

import fir.needle.joint.logging.Logger;
import fir.needle.web.websocket.client.WebSocketListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

class ConnectTask implements Runnable {
    private final NettyWebSocketClient client;
    private final NettyWebSocket webSocket;
    private final URI uri;
    private final Bootstrap bootstrap;
    private final WebSocketListener listener;
    private final Logger logger;

    ConnectTask(final NettyWebSocketClient client, final NettyWebSocket webSocket,
            final WebSocketListener listener, final Logger logger)
            throws URISyntaxException {
        this.client = client;
        this.webSocket = webSocket;
        this.uri = new URI(webSocket.url());
        this.listener = listener;
        this.logger = logger;

        bootstrap = new Bootstrap()
                            .group(client.eventLoopGroup)
                            .channel(NioSocketChannel.class)
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, client.connectTimeoutMs)
                            .option(ChannelOption.ALLOCATOR, new PooledByteBufAllocator())
                            .handler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(final SocketChannel ch) {
                                    fillPipeline(ch, webSocket.listener, webSocket.handshakeHeaders);
                                }
                            });
    }

    @Override
    public void run() {
        if (webSocket.isClosed()) {
            if (logger.isTraceEnabled()) {
                logger.trace(getClass().getSimpleName() + ".run WebSocket was closed for " +
                                     webSocket.channel().remoteAddress() + ", " + webSocket.url() +
                                     " for the channel " + webSocket.channel().id() + " and in the thread " +
                                     Thread.currentThread());
            }

            webSocket.confirmCloseIsDone();
            return;
        }

        listener.onBeforeOpen(webSocket);
        webSocket.newConnection(bootstrap.connect(uri.getHost(), client.port).channel());
    }

    void fillPipeline(final SocketChannel channel, final WebSocketListener listener,
            final HttpHeaders handshakeHeaders) {
        final ChannelPipeline pipeline = channel.pipeline();

        if (client.sslContext != null) {
            pipeline.addLast(client.sslContext.newHandler(channel.alloc(), client.host, client.port));
        }

        pipeline.addLast(new HttpClientCodec());

        pipeline.addLast(new ReadTimeoutHandler(client.readTimeoutMs, TimeUnit.MILLISECONDS));

        pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);

        pipeline.addLast(new HttpObjectAggregator(8192));

        pipeline.addLast(new NotificationHandler(webSocket, WebSocketClientHandshakerFactory.newHandshaker(
                uri, client.webSocketVersion, null, true, handshakeHeaders),
                listener, client.logger));
    }
}