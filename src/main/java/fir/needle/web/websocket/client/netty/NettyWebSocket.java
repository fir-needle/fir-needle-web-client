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

import fir.needle.joint.io.ByteArea;
import fir.needle.joint.io.CharArea;
import fir.needle.joint.io.CharSequenceToCharArea;
import fir.needle.joint.lang.Future;
import fir.needle.joint.lang.NoWaitFuture;
import fir.needle.joint.lang.VoidResult;
import fir.needle.joint.logging.Logger;
import fir.needle.web.http.client.netty.NettyInputByteBuffer;
import fir.needle.web.websocket.client.WebSocket;
import fir.needle.web.websocket.client.WebSocketHandShaker;
import fir.needle.web.websocket.client.WebSocketListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.EventExecutor;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class NettyWebSocket implements WebSocket, WebSocketHandShaker {
    private static final int DEFAULT_RCV = 0;
    private static final boolean LAST_FRAME = true;

    final HttpHeaders handshakeHeaders;
    final WebSocketListener listener;
    final Logger logger;

    private final NettyWebSocketClient client;

    private final String path;
    private final String query;
    private final String url;
    private final int attemptsToReconnectLimit;

    private final Runnable connectTask;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isCloseDone = new AtomicBoolean(false);
    private final Object lock = new Object();

    private volatile int attemptsToReconnectLeft;
    private volatile Channel channel;
    private boolean isFragmentedMessage;
    private boolean isCurrentFragmentedMessageBinary;

    NettyWebSocket(final NettyWebSocketClient client, final String path, final String query,
            final WebSocketListener listener, final HttpHeaders handshakeHeaders) throws URISyntaxException {

        this.client = client;
        this.path = path;
        this.query = query;
        this.handshakeHeaders = handshakeHeaders;
        this.listener = listener;
        this.logger = client.logger;

        this.url = (client.sslContext == null ? "ws" : "wss") + "://" + client.host + ':' + client.port + path +
                (query == null ? "" : '?' + query);

        this.attemptsToReconnectLimit = client.numberOfReconnectAttempts;
        this.attemptsToReconnectLeft = client.numberOfReconnectAttempts;
        this.connectTask = new ConnectTask(client, this, listener, logger);
    }

    @Override
    public void sendBinary(final ByteArea message, final long startIndex, final long length) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendBinary for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new BinaryWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendText(final CharArea message, final long startIndex, final long length) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendText for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new TextWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendBinary(final ByteArea message, final long startIndex, final long length,
            final boolean isFinalFragment) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendBinary for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        if (!isFragmentedMessage) {
            channel.writeAndFlush(new BinaryWebSocketFrame(isFinalFragment, DEFAULT_RCV,
                    msgToBytes(message, startIndex, length)));
            isFragmentedMessage = !isFinalFragment;

            if (!isFinalFragment) {
                isCurrentFragmentedMessageBinary = true;
            }

            return;
        }

        if (!isCurrentFragmentedMessageBinary) {
            throw new IllegalStateException(
                    "The fragments of one message must not be interleaved between the" + " fragments of another");
        }

        channel.writeAndFlush(new ContinuationWebSocketFrame(isFinalFragment, DEFAULT_RCV,
                msgToBytes(message, startIndex, length)));

        isFragmentedMessage = !isFinalFragment;
    }

    @Override
    public void sendText(final CharArea message, final long startIndex, final long length,
            final boolean isFinalFragment) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendText for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        if (!isFragmentedMessage) {
            channel.writeAndFlush(new TextWebSocketFrame(isFinalFragment, DEFAULT_RCV,
                    msgToBytes(message, startIndex, length)));
            isFragmentedMessage = !isFinalFragment;

            if (!isFinalFragment) {
                isCurrentFragmentedMessageBinary = false;
            }

            return;
        }

        if (isCurrentFragmentedMessageBinary) {
            throw new IllegalStateException(
                    "The fragments of one message must not be interleaved between the" + " fragments of another");
        }

        channel.writeAndFlush(new ContinuationWebSocketFrame(isFinalFragment, DEFAULT_RCV,
                msgToBytes(message, startIndex, length)));

        isFragmentedMessage = !isFinalFragment;
    }

    @Override
    public void sendPing() {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendPing for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PingWebSocketFrame());
    }

    @Override
    public void sendPing(final String message) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendPing for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PingWebSocketFrame(msgToBytes(message)));
    }

    @Override
    public void sendPing(final ByteArea message, final long startIndex, final long length) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendPing for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PingWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendPong() {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendPong for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PongWebSocketFrame());
    }

    @Override
    public void sendPong(final String message) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendPong for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PongWebSocketFrame(msgToBytes(message)));
    }

    @Override
    public void sendPong(final ByteArea message, final long startIndex, final long length) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendPong for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PongWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendClose(final int statusCode) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendClose for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new CloseWebSocketFrame(LAST_FRAME, statusCode));
    }

    @Override
    public void sendClose(final int statusCode, final String message) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendClose for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        final CharSequenceToCharArea stringToCharArea = new CharSequenceToCharArea(message);
        channel.writeAndFlush(new CloseWebSocketFrame(LAST_FRAME, statusCode, msgToBytes(statusCode, stringToCharArea,
                0, stringToCharArea.length())));
    }

    @Override
    public void sendClose(final int statusCode, final CharArea message, final long startIndex, final long length) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".sendClose for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new CloseWebSocketFrame(LAST_FRAME, statusCode, msgToBytes(statusCode, message,
                startIndex, length)));
    }

    @Override
    public void disconnect() {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".disconnect for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        channel.close();
    }

    @Override
    public Future<VoidResult> closeAsync() {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".closeAsync for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());
        }

        for (final EventExecutor eventExecutor : client.eventLoopGroup) {
            if (eventExecutor.inEventLoop()) {
                close(NettyWebSocketClient.DEFAULT_CLOSE_TIMEOUT_MS);
                return NoWaitFuture.INSTANCE;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".closeAsync was called not from eventLoop thread ");
        }

        client.eventLoopGroup.execute(() -> close(NettyWebSocketClient.DEFAULT_CLOSE_TIMEOUT_MS));

        return new Future<VoidResult>() {
            @Override
            public VoidResult sync() throws InterruptedException {
                synchronized (lock) {
                    while (!isCloseDone.get()) {
                        lock.wait();
                    }
                }

                if (logger.isTraceEnabled()) {
                    logger.trace(
                            getClass().getSimpleName() + ".closeAsync was finished and thread " +
                                    Thread.currentThread() + " has left wait");
                }

                return VoidResult.NO_ERROR_RESULT;
            }
        };
    }

    @Override
    public void close() throws InterruptedException {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".close for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        closeAsync().sync();
    }

    @Override
    public synchronized void close(final int closeTimeoutMs) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".close for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());
        }

        if (channel == null) {
            return;
        }

        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        channel.eventLoop().schedule(() -> {
            if (!channel.closeFuture().isSuccess()) {
                channel.close();
            }
        }, closeTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void updateHandshakeHeader(final CharSequence name, final CharSequence value) {
        handshakeHeaders.set(name, value);
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String query() {
        return query;
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public String host() {
        return client.host;
    }

    public int port() {
        return client.port;
    }

    public String url() {
        return url;
    }

    synchronized void newConnection(final Channel channel) {
        if (isClosed.get()) {
            throw new IllegalStateException("WebSocked is already closed!");
        }

        this.channel = channel;
        attemptsToReconnectLeft = client.numberOfReconnectAttempts;
    }

    Channel channel() {
        return channel;
    }

    void connect() {
        connectTask.run();
        attemptsToReconnectLeft = client.numberOfReconnectAttempts;
    }

    void reconnect(final Channel channel) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".reconnect for " + this.url() + " in the channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());
        }

        if (isClosed.get() || attemptsToReconnectLimit == NettyWebSocketClient.NO_RECONNECT) {
            if (logger.isTraceEnabled()) {
                logger.trace(getClass().getSimpleName() + ".reconnect reconnect is not scheduled as client has " +
                        "NO_RECONNECT property");
            }

            confirmCloseIsDone();
            return;
        }

        if (attemptsToReconnectLimit != NettyWebSocketClient.UNLIMITED_RECONNECT) {
            if (--attemptsToReconnectLeft <= 0) {
                confirmCloseIsDone();
                return;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".reconnect reconnect was scheduled for " + this.url() + " in the " +
                            "channel " +
                            channel.id() + " and in the thread " + Thread.currentThread());

        }

        channel.eventLoop().schedule(connectTask, client.reconnectTimeoutMs, TimeUnit.MILLISECONDS);
    }

    void confirmCloseIsDone() {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".confirmCloseIsDone verified WebSocket close" +
                    " in the thread " + Thread.currentThread());
        }

        isCloseDone.set(true);

        synchronized (lock) {
            lock.notifyAll();
        }
    }

    void confirmWsIsOpened() {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".confirmWsIsOpened verified WebSocket is opened" +
                    " in the thread " + Thread.currentThread());
        }

        isCloseDone.set(false);
    }

    private ByteBuf msgToBytes(final ByteArea message, final long startIndex, final long length) {
        if (message instanceof NettyInputByteBuffer) {
            return ((NettyInputByteBuffer) message).buffer().copy();
        }

        final ByteBuf result = Unpooled.buffer((int) length);
        for (long i = startIndex; i < length; i++) {
            result.writeByte(message.getByte(i));
        }

        return result;
    }

    private ByteBuf msgToBytes(final CharArea message, final long startIndex, final long length) {
        final ByteBuf result = Unpooled.buffer((int) length);
        for (long i = startIndex; i < length; i++) {
            result.writeByte(message.getChar(i));
        }

        return result;
    }

    private ByteBuf msgToBytes(final int statusCode, final CharArea message, final long startIndex, final long length) {
        final ByteBuf result = Unpooled.buffer((int) length + 2);

        result.writeShort(statusCode);
        for (long i = startIndex; i < startIndex + length; i++) {
            result.writeByte(message.getChar(i));
        }

        return result;
    }

    private ByteBuf msgToBytes(final String message) {
        return Unpooled.buffer(message.length()).writeBytes(message.getBytes());
    }
}
