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
import fir.needle.web.http.client.netty.NettyInputByteBuffer;
import fir.needle.web.websocket.client.WebSocket;
import fir.needle.web.websocket.client.WebSocketListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class NettyWebSocket implements WebSocket {
    private static final int DEFAULT_RCV = 0;
    private static final boolean LAST_FRAME = true;
    private static final int NORMAL_CLOSURE_STATUS_CODE = 1000;
    private static final String NORMAL_CLOSURE_MESSAGE = "Normal closure";

    final WebSocketListener listener;

    private final NettyWebSocketClient client;

    private final String path;
    private final String query;
    private final String url;
    private final int attemptsToReconnectLimit;

    private final Runnable connectTask;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private int attemptsToReconnectLeft;
    private Channel channel;
    private boolean isFragmentedMessage;
    private boolean isCurrentFragmentedMessageBinary;

    NettyWebSocket(final NettyWebSocketClient client, final String path, final String query,
            final WebSocketListener listener, final HttpHeaders handshakeHeaders) throws URISyntaxException {

        this.client = client;
        this.path = path;
        this.query = query;
        this.listener = listener;

        this.url = (client.sslContext == null ? "ws" : "wss") + "://" + client.host + ':' + client.port + path +
                (query == null ? "" : '?' + query);

        this.attemptsToReconnectLimit = client.numberOfReconnectAttempts;
        this.attemptsToReconnectLeft = client.numberOfReconnectAttempts;
        this.connectTask = new ConnectTask(client, this, handshakeHeaders);
    }

    @Override
    public void sendBinary(final ByteArea message, final long startIndex, final long length) {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new BinaryWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendText(final CharArea message, final long startIndex, final long length) {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new TextWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendBinary(final ByteArea message, final long startIndex, final long length,
            final boolean isFinalFragment) {
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
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PingWebSocketFrame());
    }

    @Override
    public void sendPing(final String message) {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PingWebSocketFrame(msgToBytes(message)));
    }

    @Override
    public void sendPing(final ByteArea message, final long startIndex, final long length) {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PingWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void sendPong() {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PongWebSocketFrame());
    }

    @Override
    public void sendPong(final String message) {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PongWebSocketFrame(msgToBytes(message)));
    }

    @Override
    public void sendPong(final ByteArea message, final long startIndex, final long length) {
        if (isClosed.get()) {
            throw new IllegalStateException("Is closed");
        }

        channel.writeAndFlush(new PongWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(message, startIndex,
                length)));
    }

    @Override
    public void close() {
        close(NORMAL_CLOSURE_STATUS_CODE, NORMAL_CLOSURE_MESSAGE, NettyWebSocketClient.DEFAULT_CLOSE_TIMEOUT_MS);
    }

    @Override
    public void close(final int statusCode, final String reasonText, final int closeTimeoutMs) {
        if (isClosed.get()) {
            return;
        }

        channel.writeAndFlush(new CloseWebSocketFrame(statusCode, reasonText));

        channel.eventLoop().schedule(() -> {
            if (channel.isOpen()) {
                channel.close();
            }
        }, closeTimeoutMs, TimeUnit.MILLISECONDS);

        isClosed.set(true);
    }

    @Override
    public void close(final int statusCode, final CharArea message, final long startIndex, final long length,
            final int closeTimeoutMs) {

        if (isClosed.get()) {
            return;
        }

        channel.writeAndFlush(new CloseWebSocketFrame(LAST_FRAME, DEFAULT_RCV, msgToBytes(statusCode, message,
                startIndex, length)));

        channel.eventLoop().schedule(() -> {
            if (channel.isOpen()) {
                channel.close();
            }
        }, closeTimeoutMs, TimeUnit.MILLISECONDS);

        isClosed.set(true);
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

    void newConnection(final Channel channel) {
        this.channel = channel;
        isClosed.set(false);
        attemptsToReconnectLeft = client.numberOfReconnectAttempts;
    }

    void connect() {
        connectTask.run();
        attemptsToReconnectLeft = client.numberOfReconnectAttempts;
    }

    //todo think about multithreading problems
    void reconnect() {
        if (isClosed.get() || attemptsToReconnectLimit == NettyWebSocketClient.NO_RECONNECT) {
            return;
        }

        if (attemptsToReconnectLimit != NettyWebSocketClient.UNLIMITED_RECONNECT) {
            if (--attemptsToReconnectLeft <= 0) {
                channel.close();
                return;
            }
        }

        channel.eventLoop().schedule(connectTask, client.reconnectTimeoutMs, TimeUnit.MILLISECONDS);
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
