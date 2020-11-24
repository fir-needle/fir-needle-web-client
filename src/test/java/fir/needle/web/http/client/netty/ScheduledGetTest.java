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

import fir.needle.joint.io.ByteArea;
import fir.needle.web.SilentTestLogger;
import fir.needle.web.http.client.AbstractHttpClientException;
import fir.needle.web.http.client.HttpResponseListener;
import fir.needle.web.http.client.ScheduledGet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduledGetTest {
    private static final String METHOD = "GET";
    private static final String EOL = "\r\n";
    private static final int PORT = 8080;
    private static final int TEST_TIMEOUT_SECONDS = 300;
    private static final int OK = 200;
    private static final int REPEAT_PERIOD_MS = 200;

    private final SilentTestLogger testLogger = new SilentTestLogger();

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testScheduledGetOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String baseOriginalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL + EOL;

        final String patternForModifiedRequest = "GET /my/test/path?requestNumber=%d HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue%d" + EOL;

        final int timesToResend = 4;

        final CountDownLatch isServerUp = new CountDownLatch(1);

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(baseOriginalRequest.length());
        for (int i = 0; i < timesToResend; i++) {
            numberOfBytesToReceive.add(patternForModifiedRequest.length());
        }

        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUp);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsSetOfEventsWithRequestResendListener listener =
                new ResponseAsSetOfEventsWithRequestResendListener(parsingCompleteSignal, timesToResend);

        final ByteBuf buffer = Unpooled.buffer().writeBytes(baseOriginalRequest.getBytes());

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnBeforeRequestSent(METHOD, path, query)
                .addOnConnected(METHOD, path, query)
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                .addOnBodyStarted()
                .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                .addOnBodyFinished()
                .addOnResponseFinished();

        for (int i = 0; i < timesToResend; i++) {
            final String crtQuery = "requestNumber=" + i;
            final String crtRequest = String.format(patternForModifiedRequest, i, i);

            if (i == timesToResend - 1) {
                expectedEvents
                        .addOnBeforeRequestSent(METHOD, path, crtQuery)
                        .addOnResponseStarted(METHOD, path, crtQuery, OK)
                        .addOnDisconnected(METHOD, path, crtQuery);
                break;
            }

            buffer.clear().writeBytes((crtRequest + EOL).getBytes());
            expectedEvents
                    .addOnBeforeRequestSent(METHOD, path, crtQuery)
                    .addOnResponseStarted(METHOD, path, crtQuery, OK)
                    .addOnHeader("Content-Type", "text/html; charset=utf-8")
                    .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                    .addOnBodyStarted()
                    .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                    .addOnBodyFinished()
                    .addOnResponseFinished();
        }

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUp.await();

            client.scheduleGet(path, query, REPEAT_PERIOD_MS, listener);

            parsingCompleteSignal.await();

            assertEquals(expectedEvents, listener.receivedEvents);
        } catch (final InterruptedException e) {
            echoServer.interrupt();
        } finally {
            try {
                echoServer.join();
            } catch (final InterruptedException e) {
                echoServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testPreparedScheduledGetOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String baseOriginalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL + EOL;

        final String patternForModifiedRequest = "GET /my/test/path?requestNumber=%d HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue%d" + EOL;

        final int timesToResend = 4;

        final CountDownLatch isServerUp = new CountDownLatch(1);

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(baseOriginalRequest.length());
        for (int i = 0; i < timesToResend; i++) {
            numberOfBytesToReceive.add(patternForModifiedRequest.length());
        }

        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUp);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsSetOfEventsWithRequestResendListener listener =
                new ResponseAsSetOfEventsWithRequestResendListener(parsingCompleteSignal, timesToResend);

        final ByteBuf buffer = Unpooled.buffer().writeBytes(baseOriginalRequest.getBytes());

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnBeforeRequestSent(METHOD, path, query)
                .addOnConnected(METHOD, path, query)
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                .addOnBodyStarted()
                .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                .addOnBodyFinished()
                .addOnResponseFinished();

        for (int i = 0; i < timesToResend; i++) {
            final String crtQuery = "requestNumber=" + i;
            final String crtRequest = String.format(patternForModifiedRequest, i, i);

            if (i == timesToResend - 1) {
                expectedEvents
                        .addOnBeforeRequestSent(METHOD, path, crtQuery)
                        .addOnResponseStarted(METHOD, path, crtQuery, OK)
                        .addOnDisconnected(METHOD, path, crtQuery);
                break;
            }

            buffer.clear().writeBytes((crtRequest + EOL).getBytes());
            expectedEvents
                    .addOnBeforeRequestSent(METHOD, path, crtQuery)
                    .addOnResponseStarted(METHOD, path, crtQuery, OK)
                    .addOnHeader("Content-Type", "text/html; charset=utf-8")
                    .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                    .addOnBodyStarted()
                    .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                    .addOnBodyFinished()
                    .addOnResponseFinished();

        }

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUp.await();

            client.prepareGet(path, query)
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .schedule(REPEAT_PERIOD_MS, listener);

            parsingCompleteSignal.await();

            assertEquals(expectedEvents, listener.receivedEvents);
        } catch (final InterruptedException e) {
            echoServer.interrupt();
        } finally {
            try {
                echoServer.join();
            } catch (final InterruptedException e) {
                echoServer.interrupt();
            }
        }
    }

    private class ResponseAsSetOfEventsListener implements HttpResponseListener<ScheduledGet> {
        CountDownLatch parsingCompleteSignal;
        ScheduledGet request;

        volatile HttpResponseListenerEvents receivedEvents = new HttpResponseListenerEvents();

        ResponseAsSetOfEventsListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        public void onBeforeRequestSent(final ScheduledGet request) {
            receivedEvents.addOnBeforeRequestSent(request.method(), request.path(), request.query());
        }

        @Override
        public void onConnected(final ScheduledGet request) {
            receivedEvents.addOnConnected(request.method(), request.path(), request.query());
            this.request = request;
        }

        @Override
        public void onResponseStarted(final ScheduledGet request, final int code) {
            receivedEvents.addOnResponseStarted(request.method(), request.path(), request.query(), code);
        }

        @Override
        public void onHeader(final CharSequence key, final CharSequence value) {
            receivedEvents.addOnHeader(key, value);
        }

        @Override
        public void onBodyStarted() {
            receivedEvents.addOnBodyStarted();
        }

        @Override
        public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
            receivedEvents.addOnBodyContent(buffer, startIndex, length);
        }

        @Override
        public void onBodyFinished() {
            receivedEvents.addOnBodyFinished();
        }

        @Override
        public void onResponseFinished() {
            receivedEvents.addOnResponseFinished();
        }

        @Override
        public void onListenerError(final Throwable error) {
            //todo take a look, this could be useful
        }

        @Override
        public void onDisconnected(final ScheduledGet request) {
            receivedEvents.addOnDisconnected(request.method(), request.path(), request.query());
            parsingCompleteSignal.countDown();
        }

        @Override
        public void onDisconnectedByError(final ScheduledGet request, final AbstractHttpClientException error) {
            //todo take a look, this could be useful
        }
    }

    private class ResponseAsSetOfEventsWithRequestResendListener extends ResponseAsSetOfEventsListener {
        private int sendRequestsCtr;
        private final int resendNumber;

        ResponseAsSetOfEventsWithRequestResendListener(final CountDownLatch parsingCompleteSignal,
                final int resendNumber) {

            super(parsingCompleteSignal);
            this.resendNumber = resendNumber;
        }


        @Override
        public void onResponseStarted(final ScheduledGet request, final int code) {
            super.onResponseStarted(request, code);

            if (sendRequestsCtr >= resendNumber) {
                try {
                    request.cancel().sync();
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onResponseFinished() {
            super.onResponseFinished();

            if (sendRequestsCtr < resendNumber) {
                request.updateQuery("requestNumber=" + sendRequestsCtr);
                request.updateHeader("myTestHeader", "myTestHeaderValue" + sendRequestsCtr);
                sendRequestsCtr++;
            }
        }
    }
}
