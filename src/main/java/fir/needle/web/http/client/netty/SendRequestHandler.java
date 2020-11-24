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

import java.util.concurrent.TimeUnit;

import fir.needle.joint.logging.Logger;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;

class SendRequestHandler extends SimpleChannelInboundHandler<Boolean> {
    private final NettyRequestHolder requestHolder;
    private final Logger logger;
    private final NettyResponseListener listener;

    SendRequestHandler(final NettyRequestHolder requestHolder, final Logger logger) {
        this.requestHolder = requestHolder;
        this.logger = logger;
        this.listener = requestHolder.listener();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Boolean lastResponseChunkReceived) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelRead0 with is last = " + lastResponseChunkReceived + " for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        if (!lastResponseChunkReceived) {
            return;
        }

        if (requestHolder.isCanceled()) {
            if (logger.isTraceEnabled()) {
                logger.trace(
                        getClass().getSimpleName() + ".channelRead0 the task has been canceled for " +
                                ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                " in the channel " + ctx.channel().id() +
                                " and in the thread " + Thread.currentThread());
            }

            ctx.close();
            return;
        }

        if (requestHolder.get() == null) {
            return;
        }

        ctx.channel().eventLoop().schedule(() -> {
            if (requestHolder.isCanceled()) {
                if (logger.isTraceEnabled()) {
                    logger.trace(
                            getClass().getSimpleName() + ".channelRead0 the task has been canceled for " +
                                    ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                    "in the channel " + ctx.channel().id() +
                                    " and in the thread " + Thread.currentThread());
                }

                ctx.close();
                return;
            }

            try {
                listener.onBeforeRequestSend();
            } catch (final Exception | AssertionError e) {

                logger.error("Error while onBeforeRequestSend notification", e);

                try {
                    listener.onListenerError(e);
                } catch (final Exception | AssertionError er) {
                    logger.error("Error while onListenerError notification", er);
                }
            }

            final HttpRequest requestToSend = requestHolder.get();
            if (logger.isTraceEnabled()) {
                logger.trace(
                        getClass().getSimpleName() + ".channelRead0 sending request to " +
                                ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                " in the channel " + ctx.channel().id() + " and in the thread " +
                                Thread.currentThread() + ":\n" + requestToSend +
                                "\n\n" + new String(((DefaultFullHttpRequest) requestToSend).content().array()));
            }

            if (ctx.channel().isActive()) {
                final ChannelFuture channelFuture = ctx.channel().writeAndFlush(requestToSend);

                if (logger.isTraceEnabled()) {
                    channelFuture.addListener((f) ->
                            logger.trace(
                                    "_____________The result of write and flush in the channel " + ctx.channel().id() +
                                            " and in the thread " + Thread.currentThread() + " is " + f.isSuccess() +
                                            "_____________" +
                                            (f.cause() == null ? "" : ('\n' + f.cause().toString()))));
                }
            }
        }, requestHolder.currentRequestDelayMs(), TimeUnit.MILLISECONDS);

        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelRead0 scheduled new request " + requestHolder.relativeUrl() +
                            " for " + ctx.channel().remoteAddress() + " in the channel " + ctx.channel().id() +
                            " and in the thread " + Thread.currentThread());
        }
    }
}