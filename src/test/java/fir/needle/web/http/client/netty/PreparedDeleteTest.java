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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import fir.needle.joint.io.ByteArea;
import fir.needle.web.SilentTestLogger;
import fir.needle.web.http.client.Delete;
import fir.needle.web.http.client.PreparedDelete;
import fir.needle.web.http.client.SingleConnectSingleDisconnectAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;

class PreparedDeleteTest {
    private static final int PORT = 8080;
    private static final String METHOD = "DELETE";
    private static final int OK = 200;

    private static final String EOL = "\r\n";
    private static final int TEST_TIMEOUT_SECONDS = 3;
    private static final int ZERO_START_INDEX = 0;
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

    private final SilentTestLogger testLogger = new SilentTestLogger();

    private final StringToByteArea byteArea = new StringToByteArea();

    private static final String BODY = "This is a test body message. It was sent by DELETE method";

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSinglePreparedDeleteOnResponseContent() {
        final String originalRequest = "DELETE /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
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
        final ResponseAsStringListener listener = new ResponseAsStringListener(parsingCompleteSignal);

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            final PreparedDelete preparedDelete = client.prepareDelete("/my/test/path", "testQueryParam=testQueryValue")
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .withHeader("myTestHeader", "myTestHeaderValue");

            preparedDelete.withBody(DEFAULT_CONTENT_TYPE, BODY.length())
                    .appendArea(byteArea.srcString(BODY), ZERO_START_INDEX, byteArea.length());

            preparedDelete.send(listener);

            parsingCompleteSignal.await();

            assertEquals(originalRequest, echoServer.receivedMessage);
            assertEquals(echoServer.sentMessage, listener.serverResponse.toString());

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSinglePreparedDeleteOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String originalRequest = "DELETE /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
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

            final PreparedDelete preparedPost = client.prepareDelete("/my/test/path", "testQueryParam=testQueryValue")
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

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    private final class ResponseAsStringListener extends SingleConnectSingleDisconnectAdapter<Delete> {
        volatile StringBuilder serverResponse = new StringBuilder();
        final CountDownLatch parsingCompleteSignal;

        private ResponseAsStringListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        public void onResponseStarted(final Delete request, final int code) {
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

    private class ResponseAsSetOfEventsListener extends SingleConnectSingleDisconnectAdapter<Delete> {
        private CountDownLatch parsingCompleteSignal;
        Delete request;

        volatile HttpResponseListenerEvents receivedEvents = new HttpResponseListenerEvents();

        ResponseAsSetOfEventsListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        protected void onDoConnected(final Delete request) {
            receivedEvents.addOnConnected(request.method(), request.path(), request.query());
            this.request = request;
        }

        @Override
        public void onResponseStarted(final Delete request, final int code) {
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
        protected void onDoDisconnected(final Delete request) {
            receivedEvents.addOnDisconnected(request.method(), request.path(), request.query());
            parsingCompleteSignal.countDown();
        }
    }
}