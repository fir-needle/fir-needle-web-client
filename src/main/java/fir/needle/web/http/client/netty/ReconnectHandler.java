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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.TimeUnit;

class ReconnectHandler extends ChannelInboundHandlerAdapter {
    private final Runnable reconnectTask;
    private final NettyRequestHolder requestHolder;
    private final int reconnectTimeoutMs;
    private final Logger logger;

    private Throwable error;

    ReconnectHandler(final Runnable reconnectTask, final NettyRequestHolder requestHolder, final int reconnectTimeoutMs,
            final Logger logger) {

        this.reconnectTask = reconnectTask;
        this.requestHolder = requestHolder;
        this.reconnectTimeoutMs = reconnectTimeoutMs;
        this.logger = logger;
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelRegistered for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        error = null;
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".channelUnregistered for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() + " and in the thread " + Thread.currentThread());
        }

        //todo think about this logging message in case disconnect for single request
        if (requestHolder.isCanceled()) {
            if (logger.isTraceEnabled()) {
                logger.trace(
                        getClass().getSimpleName() + ".channelUnregistered the task has been canceled for " +
                                ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                " in the channel " + ctx.channel().id() +
                                " and in the thread " + Thread.currentThread());
            }

            requestHolder.setCancelIsDone();
            return;
        }

        if (ctx.channel().remoteAddress() == null || // was not able to connect
                error != null) { // channel was closed by an exception

            ctx.channel().eventLoop().schedule(reconnectTask, reconnectTimeoutMs, TimeUnit.MILLISECONDS);

            if (logger.isTraceEnabled()) {
                logger.trace(
                        getClass().getSimpleName() + ".channelUnregistered scheduled new reconnect for " +
                                ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                " in the channel " + ctx.channel().id() +
                                " and in the thread " + Thread.currentThread());
            }
        } else {
            // we were closed normally so it looks like KEEP_ALIVE is not supported by the server, let's
            // try to repeat rather than to reconnect
            //todo think what delay to use
            ctx.channel().eventLoop().schedule(reconnectTask, requestHolder.currentRequestDelayMs(),
                    TimeUnit.MILLISECONDS);

            if (logger.isTraceEnabled()) {
                logger.trace(
                        getClass().getSimpleName() + ".channelUnregistered scheduled new repeat for " +
                                ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                                " in the channel " + ctx.channel().id() +
                                " and in the thread " + Thread.currentThread());
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    getClass().getSimpleName() + ".exceptionCaught for " +
                            ctx.channel().remoteAddress() + ", " + requestHolder.relativeUrl() +
                            " in the channel " + ctx.channel().id() +
                            " and in the thread " + Thread.currentThread(), cause);
        }

        error = cause;
    }
}