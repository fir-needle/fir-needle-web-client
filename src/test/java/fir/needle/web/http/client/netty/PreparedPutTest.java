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
import fir.needle.web.http.client.PreparedPut;
import fir.needle.web.http.client.Put;
import fir.needle.web.http.client.SingleConnectSingleDisconnectAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreparedPutTest {
    private static final int PORT = 8080;
    private static final String METHOD = "PUT";
    private static final int OK = 200;

    private static final int ZERO_START_INDEX = 0;
    private static final int TEST_TIMEOUT_SECONDS = 3;

    private static final String EOL = "\r\n";
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

    private final SilentTestLogger testLogger = new SilentTestLogger();

    private final StringToByteArea byteArea = new StringToByteArea();

    private static final String BODY = "This is a test body message. It was sent by PUT method";

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSinglePreparedPutOnResponseContent() {
        final String originalRequest = "PUT /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue" + EOL +
                "content-type: text/html; charset=UTF-8" + EOL +
                "content-length: " + BODY.length() + EOL + EOL +
                BODY;

        System.out.println("Length before send is " + originalRequest.length());

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(originalRequest.length());

        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUpSignal);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsStringListener listener = new ResponseAsStringListener(parsingCompleteSignal);

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            final PreparedPut preparedPut = client.preparePut("/my/test/path", "testQueryParam=testQueryValue")
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .withHeader("myTestHeader", "myTestHeaderValue");

            preparedPut.withBody(DEFAULT_CONTENT_TYPE, BODY.length())
                    .appendArea(byteArea.srcString(BODY), ZERO_START_INDEX, byteArea.length());

            preparedPut.send(listener);

            parsingCompleteSignal.await();

            assertEquals(originalRequest, echoServer.receivedMessage);
            assertEquals(echoServer.sentMessage, listener.serverResponse.toString());

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
    void testSinglePreparedPutOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String originalRequest = "PUT /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue" + EOL +
                "content-type: text/html; charset=UTF-8" + EOL +
                "content-length: " + BODY.length() + EOL + EOL +
                BODY;

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(originalRequest.length());

        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUpSignal);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsSetOfEventsListener listener = new ResponseAsSetOfEventsListener(parsingCompleteSignal);

        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(originalRequest.getBytes());

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnConnected(METHOD, path, query)
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                .addOnBodyStarted()
                .addOnBodyContent(new NettyInputByteBuffer(buffer), ZERO_START_INDEX, buffer.readableBytes())
                .addOnBodyFinished()
                .addOnResponseFinished()
                .addOnDisconnected(METHOD, path, query);

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            final PreparedPut preparedPost = client.preparePut("/my/test/path", "testQueryParam=testQueryValue")
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .withHeader("myTestHeader", "myTestHeaderValue");

            preparedPost.withBody(DEFAULT_CONTENT_TYPE, BODY.length())
                    .appendArea(byteArea.srcString(BODY), ZERO_START_INDEX, byteArea.length());

            preparedPost.send(listener);

            parsingCompleteSignal.await();

            assertEquals(originalRequest, echoServer.receivedMessage);
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
    @Disabled
    void testResendSinglePreparedPutOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String baseOriginalRequest = "PUT /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue" + EOL +
                "content-type: text/html; charset=UTF-8" + EOL +
                "content-length: " + BODY.length() + EOL +
                EOL +
                BODY;

        final String patternForModifiedRequest = "PUT /my/test/path?requestNumber=%d HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue%d" + EOL +
                "content-type: text/html; charset=UTF-8" + EOL +
                "content-length: " + BODY.length() + EOL +
                EOL +
                BODY + "%d";

        final int timesToResend = 3;

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(baseOriginalRequest.length());
        for (int i = 0; i < timesToResend; i++) {
            numberOfBytesToReceive.add(patternForModifiedRequest.length());
        }

        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUpSignal);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsSetOfEventsWithRequestResendListener listener =
                new ResponseAsSetOfEventsWithRequestResendListener(parsingCompleteSignal, timesToResend, BODY + "%d");

//        final ByteBuf buffer = Unpooled.buffer().writeBytes(baseOriginalRequest.getBytes())

        byteArea.srcString(baseOriginalRequest);

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnConnected(METHOD, path, query)
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnHeader("Content-Length", Integer.toString(byteArea.length()))
                .addOnBodyStarted()
                .addOnBodyContent(byteArea, ZERO_START_INDEX, byteArea.length())
                .addOnBodyFinished()
                .addOnResponseFinished();

        for (int i = 0; i < timesToResend; i++) {
            final String crtQuery = "requestNumber=" + i;
            final String crtRequest = String.format(patternForModifiedRequest, i, i, i);

            byteArea.srcString(crtRequest + EOL);
            expectedEvents.addOnResponseStarted(METHOD, path, crtQuery, OK)
                    .addOnHeader("Content-Type", "text/html; charset=utf-8")
                    .addOnHeader("Content-Length", Integer.toString(byteArea.length()))
                    .addOnBodyStarted()
                    .addOnBodyContent(byteArea, ZERO_START_INDEX, byteArea.length())
                    .addOnBodyFinished()
                    .addOnResponseFinished();

            if (i == timesToResend - 1) {
                expectedEvents.addOnDisconnected(METHOD, path, crtQuery);
            }
        }

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            final PreparedPut preparedPost = client.preparePut("/my/test/path", "testQueryParam=testQueryValue")
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .withHeader("myTestHeader", "myTestHeaderValue");

            preparedPost.withBody(DEFAULT_CONTENT_TYPE, BODY.length())
                    .appendArea(byteArea.srcString(BODY), ZERO_START_INDEX, byteArea.length());

            preparedPost.send(listener);

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

    private final class ResponseAsStringListener extends SingleConnectSingleDisconnectAdapter<Put> {
        volatile StringBuilder serverResponse = new StringBuilder();

        final CountDownLatch parsingCompleteSignal;

        private ResponseAsStringListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        public void onResponseStarted(final Put request, final int code) {
            serverResponse.append("HTTP/1.1 ")
                    .append(HttpResponseStatus.valueOf(code))
                    .append(EOL);
        }

        @Override
        public void onHeader(final CharSequence key, final CharSequence value) {
            serverResponse.append(key)
                    .append(": ")
                    .append(value)
                    .append(EOL);
        }

        @Override
        public void onBodyStarted() {
            serverResponse.append(EOL);
        }

        @Override
        public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
            for (long i = startIndex; i < length; i++) {
                serverResponse.append((char) buffer.getByte(i));
            }
        }

        @Override
        public void onResponseFinished() {
            parsingCompleteSignal.countDown();
        }

    }

    private class ResponseAsSetOfEventsListener extends SingleConnectSingleDisconnectAdapter<Put> {
        private CountDownLatch parsingCompleteSignal;
        Put request;

        volatile HttpResponseListenerEvents receivedEvents = new HttpResponseListenerEvents();

        ResponseAsSetOfEventsListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        protected void onDoConnected(final Put request) {
            receivedEvents.addOnConnected(request.method(), request.path(), request.query());
            this.request = request;
        }

        @Override
        public void onResponseStarted(final Put request, final int code) {
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
        protected void onDoDisconnected(final Put request) {
            receivedEvents.addOnDisconnected(request.method(), request.path(), request.query());
            parsingCompleteSignal.countDown();
        }
    }

    private class ResponseAsSetOfEventsWithRequestResendListener extends ResponseAsSetOfEventsListener {
        private int sendRequestsCtr;
        private final int resendNumber;
        private final String bodyPattern;

        ResponseAsSetOfEventsWithRequestResendListener(final CountDownLatch parsingCompleteSignal,
                final int resendNumber, final String bodyPattern) {

            super(parsingCompleteSignal);
            this.resendNumber = resendNumber;
            this.bodyPattern = bodyPattern;
        }

        @Override
        public void onResponseFinished() {
            super.onResponseFinished();

            if (sendRequestsCtr < resendNumber) {
                request.updateQuery("requestNumber=" + sendRequestsCtr);
                request.updateHeader("myTestHeader", "myTestHeaderValue" + sendRequestsCtr);

                request.updateBody(DEFAULT_CONTENT_TYPE, BODY.length())
                        .appendArea(byteArea.srcString(String.format(bodyPattern, sendRequestsCtr)),
                                ZERO_START_INDEX, byteArea.length());

                request.send();
                sendRequestsCtr++;
            }
        }
    }
}