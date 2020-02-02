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

import fir.needle.joint.io.ByteToCharArea;
import fir.needle.joint.io.CharArea;
import fir.needle.joint.io.CharSequenceToCharArea;
import fir.needle.joint.logging.Logger;
import fir.needle.web.http.client.netty.NettyInputByteBuffer;
import fir.needle.web.websocket.client.WebSocketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;


class NotificationHandler extends SimpleChannelInboundHandler<Object> {
    private static final int ABNORMAL_CLOSURE = 1006;
    private static final int ZERO_START_INDEX = 0;
    private static final int STATUS_CODE_BYTES = 2;
    private static final int EMPTY_STATUS_CODE = -1;

    private final NettyInputByteBuffer byteArea = new NettyInputByteBuffer();
    private final CharArea charArea = new ByteToCharArea(byteArea);

    private final WebSocketClientHandshaker handShaker;
    private final WebSocketListener listener;
    private final Logger logger;

    private final CharSequenceToCharArea messageAdapter = new CharSequenceToCharArea();
    private NettyWebSocket webSocket;
    private boolean wasLastMsgBinary;
    private boolean isAbnormalClosure;
    private int readableBytes;

    NotificationHandler(final NettyWebSocket webSocket, final WebSocketClientHandshaker handShaker,
            final WebSocketListener listener, final Logger logger) {

        this.webSocket = webSocket;
        this.handShaker = handShaker;
        this.listener = listener;
        this.logger = logger;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelRead0 for " + webSocket.url() + " in the newConnection " +
                            ctx.channel().id() + " and in the thread " + Thread.currentThread());

        }

        if (!handShaker.isHandshakeComplete()) {
            finishHandshake(ctx, msg);
            return;
        }

        final WebSocketFrame frame = (WebSocketFrame) msg;
        byteArea.setBuffer(frame.content());
        readableBytes = frame.content().readableBytes();

        if (!webSocket.isClosed() && frame instanceof PingWebSocketFrame) {
            processPingWebSocketFrame(ctx);
        } else if (!webSocket.isClosed() && frame instanceof PongWebSocketFrame) {
            processPongWebSocketFrame(ctx);
        } else if (!webSocket.isClosed() && frame instanceof BinaryWebSocketFrame) {
            processBinaryWebSocketFrame(ctx, (BinaryWebSocketFrame) frame);
        } else if (!webSocket.isClosed() && frame instanceof TextWebSocketFrame) {
            processTextWebSocketFrame(ctx, (TextWebSocketFrame) frame);
        } else if (!webSocket.isClosed() && frame instanceof ContinuationWebSocketFrame) {
            processContinuationWebSocketFrame(ctx, (ContinuationWebSocketFrame) frame);
        } else if (frame instanceof CloseWebSocketFrame) {
            processCloseWebSocketFrame(ctx, (CloseWebSocketFrame) frame);
        }
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRegistered for " + webSocket.url() +
                    " in the newConnection " +
                    ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelActive connection for " + webSocket.url() +
                    " has been established in the newConnection " + ctx.channel().id() + " and in the thread " +
                    Thread.currentThread() + ". Trying to initiate handshake");
        }

        handShaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelInactive disconnected from host for " + webSocket.url() +
                            " in the newConnection " + ctx.channel().id() + " and in the thread " +
                            Thread.currentThread());
        }
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelUnregistered for " + webSocket.url() +
                            " in the newConnection " +
                            ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        if (isAbnormalClosure) {
            messageAdapter.content("Closed due the connection problems or exception");
            try {
                listener.onClosed(webSocket, messageAdapter, ZERO_START_INDEX, messageAdapter.length(),
                        ABNORMAL_CLOSURE);
            } catch (final Exception | AssertionError e) {
                logger.error("Error while onClosed notification", e);
            }

            webSocket.reconnect();
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".exceptionCaught for " + webSocket.url() + " in the newConnection " +
                            ctx.channel().id() + " and in the thread " + Thread.currentThread(), cause);
        }

        isAbnormalClosure = true;
    }

    private void finishHandshake(final ChannelHandlerContext ctx, final Object msg) {
        try {
            handShaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);

            if (logger.isTraceEnabled()) {
                logger.trace(getClass().getSimpleName() + ".finishHandshake the handshake for " + webSocket.url() +
                        " has been completed in the newConnection " + ctx.channel().id() + " and in the thread " +
                        Thread.currentThread());
            }

            try {
                listener.onOpened(webSocket);
            } catch (final Exception | AssertionError e) {
                logger.trace("Error while onOpened notification", e);

                try {
                    listener.onListenerError(e);
                } catch (final Exception | AssertionError er) {
                    logger.error("Error while onListenerError notification", er);
                }
            }
        } catch (final WebSocketHandshakeException e) {
            logger.error(
                    getClass().getSimpleName() + ".channelRead0 for " + webSocket.url() + " in the newConnection " +
                            ctx.channel().id() + " and in the thread " + Thread.currentThread(), e);

            if (logger.isTraceEnabled()) {
                logger.trace(getClass().getSimpleName() + ".channelRead0 The handshake for " + webSocket.url() +
                        " has been failed in the newConnection " + ctx.channel().id() + " and in the thread " +
                        Thread.currentThread());
            }

            ctx.close();
        }
    }

    private void processPingWebSocketFrame(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRead0 PingWebSocketFrame has been received for " +
                    webSocket.url() + " in the newConnection " + ctx.channel().id() + " and in the thread " +
                    Thread.currentThread());
        }

        try {
            listener.onPing(byteArea, ZERO_START_INDEX, readableBytes);
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onPing notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }
    }

    private void processPongWebSocketFrame(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRead0 PongWebSocketFrame has been received for " +
                    webSocket.url() + " in the newConnection " + ctx.channel().id() + " and in the thread " +
                    Thread.currentThread());
        }

        try {
            listener.onPong(byteArea, ZERO_START_INDEX, readableBytes);
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onPong notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }
    }

    private void processBinaryWebSocketFrame(final ChannelHandlerContext ctx, final BinaryWebSocketFrame frame) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRead0 BinaryWebSocketFrame has been received for " +
                    webSocket.url() + " in the newConnection " + ctx.channel().id() + " and in the thread " +
                    Thread.currentThread());
        }

        wasLastMsgBinary = true;

        try {
            listener.onBinaryFrame(byteArea, ZERO_START_INDEX, readableBytes, frame.isFinalFragment());
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onBinaryFrame notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }

    }

    private void processTextWebSocketFrame(final ChannelHandlerContext ctx, final TextWebSocketFrame frame) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRead0 TextWebSocketFrame has been received for " +
                    webSocket.url() + " in the newConnection " + ctx.channel().id() + " and in the thread " +
                    Thread.currentThread());
        }

        wasLastMsgBinary = false;

        try {
            listener.onTextFrame(charArea, ZERO_START_INDEX, readableBytes, frame.isFinalFragment());
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onTextFrame notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }
    }

    private void processContinuationWebSocketFrame(final ChannelHandlerContext ctx,
            final ContinuationWebSocketFrame frame) {

        if (wasLastMsgBinary) {
            if (logger.isTraceEnabled()) {
                logger.trace(getClass().getSimpleName() + ".channelRead0 ContinuationWebSocketFrame with binary " +
                        "data has been received for " + webSocket.url() + " in the newConnection " +
                        ctx.channel().id() +
                        " and in the thread " + Thread.currentThread());
            }

            try {
                listener.onBinaryFrame(byteArea, ZERO_START_INDEX, readableBytes, frame.isFinalFragment());
            } catch (final Exception | AssertionError e) {
                logger.trace("Error while onContinue notification", e);

                try {
                    listener.onListenerError(e);
                } catch (final Exception | AssertionError er) {
                    logger.error("Error while onListenerError notification", er);
                }
            }

            return;
        }

        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRead0 ContinuationWebSocketFrame with text " +
                    "data has been received for " + webSocket.url() + " in the newConnection " + ctx.channel().id() +
                    " and in the thread " + Thread.currentThread());
        }

        try {
            listener.onTextFrame(charArea, ZERO_START_INDEX, readableBytes, frame.isFinalFragment());
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onContinue notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }
    }

    private void processCloseWebSocketFrame(final ChannelHandlerContext ctx, final CloseWebSocketFrame frame) {
        if (logger.isTraceEnabled()) {
            logger.trace(getClass().getSimpleName() + ".channelRead0 CloseWebSocketFrame has been received for " +
                    webSocket.url() + " in the newConnection " + ctx.channel().id() + " and in the thread " +
                    Thread.currentThread());
        }

        final int statusCode = (frame).statusCode();

        try {
            webSocket.close();

            if (statusCode != EMPTY_STATUS_CODE) {
                listener.onClosed(webSocket, charArea, ZERO_START_INDEX + STATUS_CODE_BYTES,
                        readableBytes - STATUS_CODE_BYTES, statusCode);
            } else {
                listener.onClosed(webSocket, charArea, ZERO_START_INDEX, readableBytes, EMPTY_STATUS_CODE);
            }

        } catch (final Exception | AssertionError e) {
            logger.error("Error while onClosed notification", e);
        } finally {
            ctx.close();
        }
    }
}