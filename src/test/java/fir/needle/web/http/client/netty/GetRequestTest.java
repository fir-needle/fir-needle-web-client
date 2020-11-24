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

import fir.needle.web.http.client.AbstractHttpClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import fir.needle.joint.io.ByteArea;
import fir.needle.web.SilentTestLogger;
import fir.needle.web.http.client.Get;
import fir.needle.web.http.client.SingleConnectSingleDisconnectAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;

class GetRequestTest {
    private static final String METHOD = "GET";
    private static final String EOL = "\r\n";
    private static final int PORT = 8080;
    private static final int TEST_TIMEOUT_SECONDS = 3;
    private static final int OK = 200;

    private final SilentTestLogger testLogger = new SilentTestLogger();

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSingleShortGetOnResponseContent() {
        final String originalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                EOL;

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

            client.get("/my/test/path", "testQueryParam=testQueryValue", listener);

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
    void testSinglePreparedGetOnResponseContent() {
        final String originalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue" + EOL +
                EOL;

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

            client.prepareGet("/my/test/path", "testQueryParam=testQueryValue")
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .withHeader("myTestHeader", "myTestHeaderValue")
                    .send(listener);

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
    void testSingleShortGetOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String originalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                EOL;

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(originalRequest.length());

        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUpSignal);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsSetOfEventsListener listener = new ResponseAsSetOfEventsListener(parsingCompleteSignal);

        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(originalRequest.getBytes());

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnBeforeRequestSent(METHOD, path, query)
                .addOnConnected(METHOD, path, query)
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                .addOnBodyStarted()
                .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                .addOnBodyFinished()
                .addOnResponseFinished()
                .addOnDisconnected(METHOD, path, query);

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            client.get("/my/test/path", "testQueryParam=testQueryValue", listener);

            parsingCompleteSignal.await();

            assertEquals(originalRequest, echoServer.receivedMessage);
            assertEquals(expectedEvents, listener.receivedEvents);

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSingleShortGetOnListenerErrors() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String originalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                EOL;

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(originalRequest.length());

        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUpSignal);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ExceptionInEachCallbackListener listener = new ExceptionInEachCallbackListener(parsingCompleteSignal);

        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(originalRequest.getBytes());

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnBeforeRequestSent(METHOD, path, query)
                .addOnConnected(METHOD, path, query)
                .addOnListenerError(new IllegalStateException("Exception in onConnected"))
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnListenerError(new IllegalStateException("Exception in onResponseStarted"))
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnListenerError(new IllegalStateException("Exception in onHeader"))
                .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                .addOnListenerError(new IllegalStateException("Exception in onHeader"))
                .addOnBodyStarted()
                .addOnListenerError(new IllegalStateException("Exception in onBodyStarted"))
                .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                .addOnListenerError(new IllegalStateException("Exception in onBodyContent"))
                .addOnBodyFinished()
                .addOnListenerError(new IllegalStateException("Exception in onBodyFinished"))
                .addOnResponseFinished()
                .addOnListenerError(new IllegalStateException("Exception in onResponseFinished"))
                .addOnDisconnected(METHOD, path, query);

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            client.get("/my/test/path", "testQueryParam=testQueryValue", listener);

            parsingCompleteSignal.await();

            assertEquals(originalRequest, echoServer.receivedMessage);
            assertEquals(expectedEvents, listener.receivedEvents);

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSinglePreparedGetOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String originalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue" + EOL +
                EOL;

        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(originalRequest.length());

        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final EchoServer echoServer = new EchoServer(PORT, numberOfBytesToReceive, isServerUpSignal);

        final CountDownLatch parsingCompleteSignal = new CountDownLatch(1);
        final ResponseAsSetOfEventsListener listener = new ResponseAsSetOfEventsListener(parsingCompleteSignal);

        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(originalRequest.getBytes());

        final HttpResponseListenerEvents expectedEvents = new HttpResponseListenerEvents()
                .addOnBeforeRequestSent(METHOD, path, query)
                .addOnConnected(METHOD, path, query)
                .addOnResponseStarted(METHOD, path, query, OK)
                .addOnHeader("Content-Type", "text/html; charset=utf-8")
                .addOnHeader("Content-Length", Integer.toString(buffer.readableBytes()))
                .addOnBodyStarted()
                .addOnBodyContent(new NettyInputByteBuffer(buffer), 0, buffer.readableBytes())
                .addOnBodyFinished()
                .addOnResponseFinished()
                .addOnDisconnected(METHOD, path, query);

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            client.prepareGet("/my/test/path", "testQueryParam=testQueryValue")
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .withHeader("myTestHeader", "myTestHeaderValue")
                    .send(listener);

            parsingCompleteSignal.await();

            assertEquals(originalRequest, echoServer.receivedMessage);
            assertEquals(expectedEvents, listener.receivedEvents);

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testResendSingleGetOnResponseEvents() {
        final String path = "/my/test/path";
        final String query = "testQueryParam=testQueryValue";
        final String baseOriginalRequest = "GET /my/test/path?testQueryParam=testQueryValue HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                EOL;

        final String patternForModifiedRequest = "GET /my/test/path?requestNumber=%d HTTP/1.1" + EOL +
                "host: localhost" + EOL +
                "connection: keep-alive" + EOL +
                "accept-encoding: gzip" + EOL +
                "myTestHeader: myTestHeaderValue%d" + EOL;

        final int timesToResend = 3;
        final List<Integer> numberOfBytesToReceive = new ArrayList<>();
        numberOfBytesToReceive.add(baseOriginalRequest.length());
        for (int i = 0; i < timesToResend; i++) {
            numberOfBytesToReceive.add(patternForModifiedRequest.length());
        }

        final CountDownLatch isServerUp = new CountDownLatch(1);
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

            if (i == timesToResend - 1) {
                expectedEvents.addOnDisconnected(METHOD, path, crtQuery);
            }
        }

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUp.await();

            client.get(path, query, listener);

            parsingCompleteSignal.await();

            assertEquals(expectedEvents, listener.receivedEvents);

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testResendSinglePreparedGetOnResponseEvents() {
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

            if (i == timesToResend - 1) {
                expectedEvents.addOnDisconnected(METHOD, path, crtQuery);
            }
        }

        try (NettyHttpClient client = NettyHttpClient.builder()
                .withLogger(testLogger)
                .build("localhost", 8080)) {

            echoServer.start();
            isServerUpSignal.await();

            client.prepareGet(path, query)
                    .withHeader("host", "localhost")
                    .withHeader("connection", "keep-alive")
                    .withHeader("accept-encoding", "gzip")
                    .send(listener);

            parsingCompleteSignal.await();

            assertEquals(expectedEvents, listener.receivedEvents);

            echoServer.join();
        } catch (final InterruptedException e) {
            echoServer.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    private final class ResponseAsStringListener extends SingleConnectSingleDisconnectAdapter<Get> {
        volatile StringBuilder serverResponse = new StringBuilder();
        final CountDownLatch parsingCompleteSignal;

        private ResponseAsStringListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        public void onResponseStarted(final Get request, final int code) {
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

    private class ResponseAsSetOfEventsListener extends SingleConnectSingleDisconnectAdapter<Get> {
        private CountDownLatch parsingCompleteSignal;
        Get request;

        volatile HttpResponseListenerEvents receivedEvents = new HttpResponseListenerEvents();

        ResponseAsSetOfEventsListener(final CountDownLatch parsingCompleteSignal) {
            this.parsingCompleteSignal = parsingCompleteSignal;
        }

        @Override
        protected void onDoConnected(final Get request) {
            receivedEvents.addOnConnected(request.method(), request.path(), request.query());
            this.request = request;
        }

        @Override
        public void onBeforeRequestSent(final Get request) {
            receivedEvents.addOnBeforeRequestSent(request.method(), request.path(), request.query());
            super.onBeforeRequestSent(request);
        }

        @Override
        public void onResponseStarted(final Get request, final int code) {
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
            receivedEvents.addOnListenerError(error);
        }

        @Override
        protected void onDoDisconnectedByError(final Get request, final AbstractHttpClientException error) {
            receivedEvents.addOnDisconnectedByError(request.method(), request.path(), request.query(), error);
        }

        @Override
        protected void onDoDisconnected(final Get request) {
            receivedEvents.addOnDisconnected(request.method(), request.path(), request.query());
            parsingCompleteSignal.countDown();
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
        public void onResponseFinished() {
            super.onResponseFinished();

            if (sendRequestsCtr < resendNumber) {
                request.updateQuery("requestNumber=" + sendRequestsCtr);
                request.updateHeader("myTestHeader", "myTestHeaderValue" + sendRequestsCtr);
                request.send();
                sendRequestsCtr++;
            }
        }
    }

    private class ExceptionInEachCallbackListener extends ResponseAsSetOfEventsListener {

        ExceptionInEachCallbackListener(final CountDownLatch parsingCompleteSignal) {
            super(parsingCompleteSignal);
        }

        @Override
        protected void onDoConnected(final Get request) {
            super.onDoConnected(request);
            throw new IllegalStateException("Exception in onConnected");
        }

        @Override
        public void onResponseStarted(final Get request, final int code) {
            super.onResponseStarted(request, code);
            throw new IllegalStateException("Exception in onResponseStarted");
        }

        @Override
        public void onHeader(final CharSequence key, final CharSequence value) {
            super.onHeader(key, value);
            throw new IllegalStateException("Exception in onHeader");
        }

        @Override
        public void onBodyStarted() {
            super.onBodyStarted();
            throw new IllegalStateException("Exception in onBodyStarted");
        }

        @Override
        public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
            super.onBodyContent(buffer, startIndex, length);
            throw new IllegalStateException("Exception in onBodyContent");
        }

        @Override
        public void onBodyFinished() {
            super.onBodyFinished();
            throw new IllegalStateException("Exception in onBodyFinished");
        }

        @Override
        public void onResponseFinished() {
            super.onResponseFinished();
            throw new IllegalStateException("Exception in onResponseFinished");
        }
    }
}