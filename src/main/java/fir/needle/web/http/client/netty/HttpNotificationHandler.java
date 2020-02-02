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

import fir.needle.joint.logging.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.ReadTimeoutException;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

class HttpNotificationHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final NettyRequestHolder requestHolder;
    private final Logger logger;
    private final String host;
    private final int port;

    private NettyResponseListener listener;
    private boolean isBodyStarted;
    private NettyInputByteBuffer inputByteBuffer;

    private boolean wasConnectionEstablished;
    private Throwable error;

    HttpNotificationHandler(final NettyRequestHolder requestHolder, final Logger logger, final String host,
            final int port) {
        this.requestHolder = requestHolder;
        this.logger = logger;
        this.listener = requestHolder.listener();
        this.host = host;
        this.port = port;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
        listener = requestHolder.listener();

        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelRead0 for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
            return;
        }

        if (msg instanceof HttpResponse) {
            processHttpResponse(ctx, msg);
        }

        if (msg instanceof HttpContent) {
            processHttpContent(ctx, msg);
        }

        if (msg instanceof LastHttpContent) {
            processLastHttpContent(ctx);
        } else {
            ctx.fireChannelRead(false);
        }
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelRegistered for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelActive for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        ctx.channel().writeAndFlush(requestHolder.get());

        wasConnectionEstablished = true;

        try {
            listener.onConnected();
        } catch (final Exception | AssertionError e) {
            logger.error("Error while onConnected notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelInactive for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelUnregistered for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        if (wasConnectionEstablished) {
            if (error == null) {
                try {
                    listener.onDisconnected();
                } catch (final Exception | AssertionError e) {
                    logger.error("Error while onDisconnected notification", e);
                }
            } else {
                if (error instanceof ReadTimeoutException) {
                    listener.onDisconnectedByError("Connection closed by timeout due to the absence of incoming " +
                            "messages");
                } else {
                    listener.onDisconnectedByError("Connection closed due to exception: " + error.getMessage());
                }
            }
        } else {
            try {
                listener.onDisconnectedByError("Failed to establish connection to " + host + ":" + port);
            } catch (final Exception | AssertionError e) {
                logger.error("Error while onDisconnectedBy");
            }
        }

        wasConnectionEstablished = false;

        ctx.fireChannelUnregistered();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".exceptionCaught for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " +
                            Thread.currentThread(), cause);
        }

        error = cause;
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }

    private void processHeaders(final HttpResponse response) {
        if (response.headers().isEmpty()) {
            return;
        }

        final List<Map.Entry<String, String>> headers = response.headers().entries();

        for (int i = 0; i < headers.size(); i++) {
            final Map.Entry<String, String> crtHeader = headers.get(i);

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Response has been received with header: " + crtHeader.getKey() + ": " + crtHeader.getValue());
            }

            try {
                listener.onHeader(crtHeader.getKey(), crtHeader.getValue());
            } catch (final Exception | AssertionError e) {
                logger.trace("Error while onHeader notification", e);

                try {
                    listener.onListenerError(e);
                } catch (final Exception | AssertionError er) {
                    logger.error("Error while onListenerError notification", er);
                }
            }

            if (requestHolder.isCanceled()) {
                return;
            }
        }
    }

    private NettyInputByteBuffer getInputByteBuffer(final ByteBuf buffer) {
        if (inputByteBuffer == null) {
            inputByteBuffer = new NettyInputByteBuffer();
        }

        inputByteBuffer.setBuffer(buffer);

        return inputByteBuffer;
    }

    private void processHttpResponse(final ChannelHandlerContext ctx, final HttpObject msg) {
        final HttpResponse response = (HttpResponse) msg;

        if (logger.isTraceEnabled()) {
            logger.trace(
                    "Response has been received with status-line: " + response.protocolVersion() + " " +
                            response.status());
        }

        try {
            listener.onResponseStarted(response.status().code());
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onResponseStarted notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
            return;
        }

        processHeaders(response);
    }

    private void processHttpContent(final ChannelHandlerContext ctx, final HttpObject msg) {
        if (!isBodyStarted) {
            try {
                listener.onBodyStarted();
            } catch (final Exception | AssertionError e) {
                logger.trace("Error while onBodyStarted notification", e);

                try {
                    listener.onListenerError(e);
                } catch (final Exception | AssertionError er) {
                    logger.error("Error while onListenerError notification", er);
                }
            }
            isBodyStarted = true;
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
            return;
        }

        final ByteBuf content = ((HttpContent) msg).content();

        if (logger.isTraceEnabled()) {
            logger.trace(
                    "Response has been received with body part:\n" + content.getCharSequence(0,
                            content.readableBytes(), Charset.defaultCharset()));
        }

        try {
            listener.onBodyContent(getInputByteBuffer(content), 0, content.readableBytes());
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onBodyContent notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
        }
    }

    private void processLastHttpContent(final ChannelHandlerContext ctx) {
        if (isBodyStarted) {
            try {
                listener.onBodyFinished();
            } catch (final Exception | AssertionError e) {
                logger.trace("Error while onBodyFinished notification", e);

                try {
                    listener.onListenerError(e);
                } catch (final Exception | AssertionError er) {
                    logger.error("Error while onListenerError notification", er);
                }
            }
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
            return;
        }

        try {
            listener.onResponseFinished();
        } catch (final Exception | AssertionError e) {
            logger.trace("Error while onResponseFinished notification", e);

            try {
                listener.onListenerError(e);
            } catch (final Exception | AssertionError er) {
                logger.error("Error while onListenerError notification", er);
            }
        }

        if (requestHolder.isCanceled()) {
            ctx.close();
            return;
        }

        isBodyStarted = false;

        ctx.fireChannelRead(true);
    }
}