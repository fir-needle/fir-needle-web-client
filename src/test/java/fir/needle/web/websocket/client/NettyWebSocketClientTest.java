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
package fir.needle.web.websocket.client;

import fir.needle.joint.io.ByteArea;
import fir.needle.joint.io.CharArea;
import fir.needle.web.SilentTestLogger;
import fir.needle.web.websocket.client.netty.NettyWebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyWebSocketClientTest {
    private static final int TEST_TIMEOUT_SECONDS = 3;
    private static final int DEFAULT_READ_TIMEOUT_MS = 500;
    private static final int DEFAULT_RECONNECT_TIMEOUT_MS = 50;
    private static final int DEFAULT_CLOSE_TIMEOUT_MS = 2000;

    private static final int PORT = 8080;
    private static final String WEB_SOCKET_PATH = "/websocket/path";
    private static final int ZERO_START_INDEX = 0;
    private static final boolean LAST_FRAME = true;
    private static final int NO_WS_EXTENSIONS = 0;
    private static final int BYTES_IN_STATUS_CODE = 2;

    private static final int CONTINUATION_FRAME = 0;
    private static final int TEXT_FRAME = 1;
    private static final int BINARY_FRAME = 2;
    private static final int CONNECTION_CLOSE_FRAME = 8;
    private static final int PING_FRAME = 9;
    private static final int PONG_FRAME = 10;

    private static final int NORMAL_CLOSURE_STATUS_CODE = 1000;

    private static final String NORMAL_CLOSURE_MESSAGE = "Normal closure";
    private static final String CLIENT_PING_MESSAGE = "Ping from client";
    private static final String CLIENT_PONG_MESSAGE = "Pong from client";
    private static final String SERVER_PING_MESSAGE = "Ping from server";
    private static final String SERVER_PONG_MESSAGE = "Pong from server";
    private static final String SERVER_BINARY_MESSAGE = "Binary from server";
    private static final String CLIENT_BINARY_MESSAGE = "Binary from client";
    private static final String SERVER_TEXT_MESSAGE = "Text from server";
    private static final String CLIENT_TEXT_MESSAGE = "Text from client";

    private final StringToByteArea byteArea = new StringToByteArea();
    private final StringToCharArea charArea = new StringToCharArea();
    private final SilentTestLogger testLogger = new SilentTestLogger();

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testServerSuccessfullyClosesConnectionAfterHandshake() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(2);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                closeWSConnection(NORMAL_CLOSURE_STATUS_CODE, byteArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, byteArea.length());

                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) {
                assertTrue(isLastFragment);
                assertEquals(CONNECTION_CLOSE_FRAME, opcode);

                final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                final StringBuilder receivedMessage = new StringBuilder();
                for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                    receivedMessage.append((char) data.getByte(i));
                }

                assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());
                webSocketClosedSignal.countDown();
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnCloseFrame(
                        charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX,
                        charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                this.webSocket = webSocket;
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
                webSocket.close();
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException exception) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), exception);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withNoReconnect()
                .withLogger(testLogger)
                .build("localhost", PORT)) {
            testServer.start();

            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);

            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testClientSuccessfullyClosesConnectionAfterHandshake() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException {

                assertTrue(isLastFragment);
                assertEquals(CONNECTION_CLOSE_FRAME, opcode);

                final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                final StringBuilder receivedMessage = new StringBuilder();
                for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                    receivedMessage.append((char) data.getByte(i));
                }

                assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                closeWSConnection(NORMAL_CLOSURE_STATUS_CODE, byteArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, byteArea.length());
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE), ZERO_START_INDEX,
                        charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);


        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();
        final WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                webSocket.close();
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();

            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);

            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSuccessfulHandshakeSeveralPingPongCloseFromClient() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case PING_FRAME:
                        final StringToByteArea stringToByteArea = new StringToByteArea();
                        sendFrame(data, startIndex, length, PONG_FRAME, LAST_FRAME, NO_WS_EXTENSIONS);
                        sendFrame(stringToByteArea.srcString(SERVER_PING_MESSAGE), ZERO_START_INDEX,
                                stringToByteArea.length(), PING_FRAME, LAST_FRAME, NO_WS_EXTENSIONS);

                        readAndProcessFrame();
                        break;

                    case PONG_FRAME:

                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final StringToByteArea stringToByteArea = new StringToByteArea();
        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnPong(stringToByteArea.srcString(""), ZERO_START_INDEX, 0)
                .addOnPing(stringToByteArea.srcString(SERVER_PING_MESSAGE), ZERO_START_INDEX,
                        SERVER_PING_MESSAGE.length())
                .addOnPong(stringToByteArea.srcString(
                        CLIENT_PING_MESSAGE +
                                " string"), ZERO_START_INDEX,
                        stringToByteArea.length())
                .addOnPing(stringToByteArea.srcString(SERVER_PING_MESSAGE), ZERO_START_INDEX,
                        SERVER_PING_MESSAGE.length())
                .addOnPong(stringToByteArea.srcString(
                        CLIENT_PING_MESSAGE +
                                " binary"), ZERO_START_INDEX,
                        stringToByteArea.length())
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE), ZERO_START_INDEX,
                        charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            WebSocket webSocket;
            int ctr;
            StringToByteArea stringToByteArea = new StringToByteArea();

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;
                webSocket.sendPing();
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);

                webSocket.sendPong(message, startIndex, length);

                switch (++ctr) {
                    case 2:
                        webSocket.sendPing(CLIENT_PING_MESSAGE + " string");
                        break;
                    case 4:
                        webSocket.sendPing(stringToByteArea.srcString(CLIENT_PING_MESSAGE + " binary"),
                                ZERO_START_INDEX, stringToByteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected counter value " + ctr);
                }
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);

                if (++ctr >= 5) {
                    webSocket.close();
                }
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();

            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);

            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testEchoBinaryData() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case BINARY_FRAME:

                        sendFrame(data, startIndex, length, opcode, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }

        };

        final String dataToSend = "This message was send in a binary frame";

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, "binary=true")
                .addOnBinaryFrame(byteArea.srcString(
                        dataToSend + 0), ZERO_START_INDEX,
                        byteArea.length(), true)
                .addOnBinaryFrame(byteArea.srcString(
                        dataToSend + 1), ZERO_START_INDEX,
                        byteArea.length(), true)
                .addOnBinaryFrame(byteArea.srcString(
                        dataToSend + 2), ZERO_START_INDEX,
                        byteArea.length(), true)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX,
                        charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, "binary=true");

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            WebSocket webSocket;
            int ctr;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendBinary(byteArea.srcString(dataToSend + ctr++), ZERO_START_INDEX, byteArea.length());
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);

                if (ctr > 2) {
                    webSocket.close();
                    return;
                }

                webSocket.sendBinary(byteArea.srcString(dataToSend + ctr++), ZERO_START_INDEX, byteArea.length());
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, "binary=true", listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testEchoTextData() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case TEXT_FRAME:

                        sendFrame(data, startIndex, length, opcode, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final String dataToSend = "This message was sent in a text frame";

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, "binary=false&text=true")
                .addOnTextFrame(charArea.srcString(
                        dataToSend + 'a'), ZERO_START_INDEX,
                        charArea.length(), true)
                .addOnTextFrame(charArea.srcString(
                        dataToSend + 'c'), ZERO_START_INDEX,
                        charArea.length(), true)
                .addOnTextFrame(charArea.srcString(
                        dataToSend + 'e'), ZERO_START_INDEX,
                        charArea.length(), true)
                .addOnTextFrame(charArea.srcString(
                        dataToSend + 'g'), ZERO_START_INDEX,
                        charArea.length(), true)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, "binary=false&text=true");

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            WebSocket webSocket;
            char crtMessageEnding = 'a';

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendText(charArea.srcString(dataToSend + crtMessageEnding), ZERO_START_INDEX,
                        charArea.length());

                crtMessageEnding += 2;
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);

                if (crtMessageEnding > 'g') {
                    webSocket.close();
                    return;
                }

                webSocket.sendText(charArea.srcString(dataToSend + crtMessageEnding), ZERO_START_INDEX,
                        charArea.length());

                crtMessageEnding += 2;
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, "binary=false&text=true", listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testBadHandshakeWithNoReconnect() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            List<String> handleHandshake() throws Exception {
                webSocketClosedSignal.await();

                return null;
            }

            @Override
            void communicateWithClient() throws IOException {
                //
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException {
                //
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null,
                        new WebSocketReadTimeoutException());

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                this.webSocket = webSocket;
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withNoReconnect()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS / 2)
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            //wait for illegal callbacks to occur
            TimeUnit.MILLISECONDS.sleep(DEFAULT_READ_TIMEOUT_MS + DEFAULT_RECONNECT_TIMEOUT_MS);
            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testBadHandshakeWithSeveralReconnectAttempts() {
        final int numberOfReconnectAttempts = 3;
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            List<String> handleHandshake() throws Exception {
                webSocketClosedSignal.await();
                return null;
            }

            @Override
            void communicateWithClient() throws IOException {
                //
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException {
                //
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null,
                        new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null,
                        new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null,
                        new WebSocketReadTimeoutException());

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            int reconnectAttemptsCtr;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);

                if (++reconnectAttemptsCtr >= numberOfReconnectAttempts) {
                    webSocketClosedSignal.countDown();
                    webSocket.closeAsync();
                }
            }
        };


        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS / 3)
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withNumberOfReconnectAttempts(numberOfReconnectAttempts)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            //wait for illegal callbacks to occur
            TimeUnit.MILLISECONDS.sleep(DEFAULT_READ_TIMEOUT_MS + DEFAULT_RECONNECT_TIMEOUT_MS);
            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testConnectionLostFailedToReconnect() {
        final int numberOfReconnectAttempts = 3;
        final int successfulEchoFramesBeforeConnectionLoose = 2;

        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            private int ctr;

            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case TEXT_FRAME:
                        if (++ctr > successfulEchoFramesBeforeConnectionLoose) {
                            //imitate connection lost
                            webSocketClosedSignal.await();
                            return;
                        }

                        sendFrame(data, startIndex, length, opcode, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final String dataToSend = "This message was sent in a text frame";

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnTextFrame(charArea.srcString(
                        dataToSend + 'a'), ZERO_START_INDEX,
                        charArea.length(), true)
                .addOnTextFrame(charArea.srcString(
                        dataToSend + 'c'), ZERO_START_INDEX,
                        charArea.length(), true)
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null,
                        new WebSocketReadTimeoutException());

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            private WebSocket webSocket;
            private char crtMessageEnding = 'a';
            private int reconnectAttemptsCtr;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendText(charArea.srcString(dataToSend + crtMessageEnding), ZERO_START_INDEX,
                        charArea.length());

                crtMessageEnding += 2;
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);

                if (crtMessageEnding > 'g') {
                    webSocket.closeAsync();
                    return;
                }

                webSocket.sendText(charArea.srcString(dataToSend + crtMessageEnding), ZERO_START_INDEX,
                        charArea.length());

                crtMessageEnding += 2;
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);

                if (++reconnectAttemptsCtr >= numberOfReconnectAttempts) {
                    webSocketClosedSignal.countDown();
                    webSocket.closeAsync();
                }
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS / 3)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            //wait for illegal callbacks to occur
            TimeUnit.MILLISECONDS.sleep(DEFAULT_READ_TIMEOUT_MS + 2 * DEFAULT_RECONNECT_TIMEOUT_MS);
            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testExceptionsInListenerCallbacks() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            void communicateWithClient() throws IOException {
                sendFrame(byteArea.srcString(SERVER_PING_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        PING_FRAME, true, NO_WS_EXTENSIONS);

                sendFrame(byteArea.srcString(SERVER_PONG_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        PONG_FRAME, true, NO_WS_EXTENSIONS);

                sendFrame(byteArea.srcString(SERVER_BINARY_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        BINARY_FRAME, true, NO_WS_EXTENSIONS);

                sendFrame(byteArea.srcString(SERVER_TEXT_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        TEXT_FRAME, true, NO_WS_EXTENSIONS);

                closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                        byteArea.srcString(NORMAL_CLOSURE_MESSAGE), ZERO_START_INDEX, byteArea.length());
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException {
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnListenerError(new IllegalStateException("onOpened"))
                .addOnPing(byteArea.srcString(SERVER_PING_MESSAGE), ZERO_START_INDEX, byteArea.length())
                .addOnListenerError(new IllegalStateException("onPing"))
                .addOnPong(byteArea.srcString(SERVER_PONG_MESSAGE), ZERO_START_INDEX, byteArea.length())
                .addOnListenerError(new IllegalStateException("onPong"))
                .addOnBinaryFrame(byteArea.srcString(SERVER_BINARY_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        true)
                .addOnListenerError(new IllegalStateException("onBinaryFrame"))
                .addOnTextFrame(charArea.srcString(SERVER_TEXT_MESSAGE), ZERO_START_INDEX, charArea.length(),
                        true)
                .addOnListenerError(new IllegalStateException("onTextFrame"))
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnListenerError(new IllegalStateException("onCloseFrame"))
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                throw new IllegalStateException("onOpened");
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
                throw new IllegalStateException("onPing");
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
                throw new IllegalStateException("onPong");
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
                throw new IllegalStateException("onBinaryFrame");
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
                throw new IllegalStateException("onTextFrame");
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
                throw new IllegalStateException("onCloseFrame");
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }

        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testIncomingDataAfterWSCloseCalled() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            void communicateWithClient() throws IOException {
                sendFrame(byteArea.srcString(SERVER_PING_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        PING_FRAME, true, NO_WS_EXTENSIONS);

                sendFrame(byteArea.srcString(SERVER_PONG_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        PONG_FRAME, true, NO_WS_EXTENSIONS);

                sendFrame(byteArea.srcString(SERVER_BINARY_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        BINARY_FRAME, true, NO_WS_EXTENSIONS);

                sendFrame(byteArea.srcString(SERVER_TEXT_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        TEXT_FRAME, true, NO_WS_EXTENSIONS);

                closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                        byteArea.srcString(NORMAL_CLOSURE_MESSAGE), ZERO_START_INDEX, byteArea.length());
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException {
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                webSocket.closeAsync();
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSendingPongMessages() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case PONG_FRAME:
                        sendFrame(data, startIndex, length, PONG_FRAME, LAST_FRAME, NO_WS_EXTENSIONS);

                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnPong(byteArea.srcString(""), ZERO_START_INDEX, byteArea.length())
                .addOnPong(byteArea.srcString(
                        CLIENT_PONG_MESSAGE +
                                " as string"), ZERO_START_INDEX, byteArea.length())
                .addOnPong(byteArea.srcString(
                        CLIENT_PING_MESSAGE +
                                " as binary"), ZERO_START_INDEX, byteArea.length())
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;
            private int sendFramesCtr;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;
                webSocket.sendPong();
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);

                switch (++sendFramesCtr) {
                    case 1:
                        webSocket.sendPong(CLIENT_PONG_MESSAGE + " as string");
                        break;
                    case 2:
                        webSocket.sendPong(byteArea.srcString(CLIENT_PING_MESSAGE + " as binary"),
                                ZERO_START_INDEX, byteArea.length());
                        break;
                    default:
                        webSocket.close();
                }
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSendingOfFragmentedFrames() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case BINARY_FRAME:
                        sendFrame(data, startIndex, length, BINARY_FRAME, isLastFragment, NO_WS_EXTENSIONS);

                        readAndProcessFrame();
                        break;

                    case TEXT_FRAME:

                        sendFrame(data, startIndex, length, TEXT_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONTINUATION_FRAME:

                        sendFrame(data, startIndex, length, CONTINUATION_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnBinaryFrame(byteArea.srcString(
                        CLIENT_BINARY_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME)
                .addOnBinaryFrame(byteArea.srcString(
                        CLIENT_BINARY_MESSAGE + " secondPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME)
                .addOnBinaryFrame(byteArea.srcString(
                        CLIENT_BINARY_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, byteArea.length(), LAST_FRAME)
                .addOnTextFrame(charArea.srcString(
                        CLIENT_TEXT_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, charArea.length(), !LAST_FRAME)
                .addOnTextFrame(charArea.srcString(
                        CLIENT_TEXT_MESSAGE + " secondPart"),
                        ZERO_START_INDEX, charArea.length(), !LAST_FRAME)
                .addOnTextFrame(charArea.srcString(
                        CLIENT_TEXT_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, charArea.length(), LAST_FRAME)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;
            private int receivedFramesCtr;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME);
                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " secondPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME);
                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, byteArea.length(), LAST_FRAME);

                webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, charArea.length(), !LAST_FRAME);
                webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE + " secondPart"),
                        ZERO_START_INDEX, charArea.length(), !LAST_FRAME);
                webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, charArea.length(), LAST_FRAME);
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                if (++receivedFramesCtr >= 6) {
                    webSocket.close();
                }

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                if (++receivedFramesCtr >= 6) {
                    webSocket.close();
                }

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testOneFragmentedMessageInterleavesWithAnotherOne() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case BINARY_FRAME:

                        readAndProcessFrame();
                        break;

                    case TEXT_FRAME:

                        sendFrame(data, startIndex, length, TEXT_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONTINUATION_FRAME:

                        sendFrame(data, startIndex, length, CONTINUATION_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnListenerError(new IllegalStateException(
                        "The fragments of one message must not be interleaved between the" +
                                " fragments of another"))
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME);
                webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), LAST_FRAME);
                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, byteArea.length(), LAST_FRAME);
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
                webSocket.closeAsync();
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
                webSocket.close();
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testControlFramesBetweenMessageFragments() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case BINARY_FRAME:

                        sendFrame(data, startIndex, length, BINARY_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONTINUATION_FRAME:
                        sendFrame(data, startIndex, length, CONTINUATION_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case PING_FRAME:
                        sendFrame(data, startIndex, length, PING_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case PONG_FRAME:
                        sendFrame(data, startIndex, length, PONG_FRAME, isLastFragment, NO_WS_EXTENSIONS);
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnBinaryFrame(byteArea.srcString(
                        CLIENT_BINARY_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME)
                .addOnPing(byteArea.srcString(""), ZERO_START_INDEX, byteArea.length())
                .addOnPong(byteArea.srcString(""), ZERO_START_INDEX, byteArea.length())
                .addOnBinaryFrame(byteArea.srcString(
                        CLIENT_BINARY_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, byteArea.length(), LAST_FRAME)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;
            private boolean isLast;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " firstPart"),
                        ZERO_START_INDEX, byteArea.length(), !LAST_FRAME);
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
                webSocket.sendPong();
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
                isLast = true;
                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE + " lastPart"),
                        ZERO_START_INDEX, byteArea.length(), LAST_FRAME);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);

                if (isLast) {
                    webSocket.close();
                    return;
                }

                webSocket.sendPing();
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
                webSocket.closeAsync();
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testClientConnectionCloseWithCustomCodeAndCharAreaMessage() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final int customClosureCode = 1002;
        final String customClosureMessage = "Terminating the connection due to a protocol error";

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException {
                assertTrue(isLastFragment);
                assertEquals(CONNECTION_CLOSE_FRAME, opcode);

                final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                assertEquals(customClosureCode, statusCode);

                final StringBuilder receivedMessage = new StringBuilder();
                for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                    receivedMessage.append((char) data.getByte(i));
                }

                assertEquals(customClosureMessage, receivedMessage.toString());

                closeWSConnection(customClosureCode, byteArea.srcString(customClosureMessage),
                        ZERO_START_INDEX, byteArea.length());
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnCloseFrame(charArea.srcString(customClosureMessage), ZERO_START_INDEX,
                        charArea.length(), customClosureCode)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                webSocket.close(customClosureCode, customClosureMessage, DEFAULT_CLOSE_TIMEOUT_MS);
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testConnectMsgConnectionLostSeveralAttemptsToReconnectSuccessMsgNormalClose() {
        final int amountOfFailedReconnections = 3;
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final CountDownLatch allowReconnectSignal = new CountDownLatch(amountOfFailedReconnections);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;
                switch (opcode) {
                    case BINARY_FRAME:

                        sendFrame(data, startIndex, length, BINARY_FRAME, isLastFragment, NO_WS_EXTENSIONS);

                        //simulate connection loose
                        try {
                            allowReconnectSignal.await();
                        } catch (final InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }

                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:

                        acceptNextConnection = false;

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnBinaryFrame(byteArea.srcString(CLIENT_BINARY_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        LAST_FRAME)
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnBinaryFrame(byteArea.srcString(CLIENT_BINARY_MESSAGE), ZERO_START_INDEX, byteArea.length(),
                        LAST_FRAME)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE), ZERO_START_INDEX,
                        charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;
            private int reconnectionAttemptsCtr;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;

                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE),
                        ZERO_START_INDEX, byteArea.length(), LAST_FRAME);
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
                webSocket.sendPong();
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);

                if (reconnectionAttemptsCtr > 0) {
                    webSocket.close();
                }
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);

                allowReconnectSignal.countDown();

                if (reconnectionAttemptsCtr++ >= amountOfFailedReconnections) {
                    webSocketClosedSignal.countDown();
                }
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS / 3)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testOnWritingFromClose() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {

                closeWSConnection(NORMAL_CLOSURE_STATUS_CODE, byteArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, byteArea.length());
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) {

                assertTrue(isLastFragment);
                assertEquals(CONNECTION_CLOSE_FRAME, opcode);

                final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                final StringBuilder receivedMessage = new StringBuilder();
                for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                    receivedMessage.append((char) data.getByte(i));
                }

                assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE), ZERO_START_INDEX,
                        charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                this.webSocket = webSocket;
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
                webSocket.closeAsync();
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());

                assertThrows(IllegalStateException.class, webSocket::sendPing);
                assertThrows(IllegalStateException.class, () -> webSocket.sendPing(CLIENT_PING_MESSAGE));
                assertThrows(IllegalStateException.class,
                        () -> webSocket.sendPing(byteArea.srcString(CLIENT_PING_MESSAGE),
                                ZERO_START_INDEX, byteArea.length()));

                assertThrows(IllegalStateException.class, webSocket::sendPong);
                assertThrows(IllegalStateException.class, () -> webSocket.sendPong(CLIENT_PONG_MESSAGE));
                assertThrows(IllegalStateException.class,
                        () -> webSocket.sendPing(byteArea.srcString(CLIENT_PONG_MESSAGE),
                                ZERO_START_INDEX, byteArea.length()));

                assertThrows(IllegalStateException.class,
                        () -> webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE),
                                ZERO_START_INDEX, charArea.length()));
                assertThrows(IllegalStateException.class,
                        () -> webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE),
                                ZERO_START_INDEX, charArea.length(), LAST_FRAME));

                assertThrows(IllegalStateException.class,
                        () -> webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE),
                                ZERO_START_INDEX, byteArea.length()));
                assertThrows(IllegalStateException.class,
                        () -> webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE),
                                ZERO_START_INDEX, byteArea.length(), LAST_FRAME));

                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withNoReconnect()
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();

            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);

            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);

        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSendHandshakeRequestWithCustomHeaders() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                for (final String line : incomingHandshakeRequestLines) {
                    if (line.startsWith("sec-websocket-key")) {
                        continue;
                    }

                    sendFrame(byteArea.srcString(line), ZERO_START_INDEX, byteArea.length(), TEXT_FRAME, LAST_FRAME,
                            NO_WS_EXTENSIONS);
                }

                closeWSConnection(NORMAL_CLOSURE_STATUS_CODE, byteArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, byteArea.length());
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) {

                assertTrue(isLastFragment);
                assertEquals(CONNECTION_CLOSE_FRAME, opcode);

                final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                final StringBuilder receivedMessage = new StringBuilder();
                for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                    receivedMessage.append((char) data.getByte(i));
                }

                assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());
            }

        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnTextFrame(charArea.srcString("GET /websocket/path HTTP/1.1"), ZERO_START_INDEX,
                        charArea.length(), LAST_FRAME)
                .addOnTextFrame(charArea.srcString("testHeader0: testValue0"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString("testHeader1: testValue1"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString("testHeader2: testValue2"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString("upgrade: websocket"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString("connection: upgrade"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString("host: localhost:8080"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString("sec-websocket-origin: http://localhost:8080"), ZERO_START_INDEX,
                        charArea.length(), LAST_FRAME)
                .addOnTextFrame(charArea.srcString("sec-websocket-version: 8"), ZERO_START_INDEX, charArea.length(),
                        LAST_FRAME)
                .addOnTextFrame(charArea.srcString(
                        "sec-websocket-extensions: permessage-deflate;" +
                                "client_max_window_bits,deflate-frame,x-webkit-deflate-frame"), ZERO_START_INDEX,
                        charArea.length(),
                        LAST_FRAME)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
                webSocket.closeAsync();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .withWebSocketVersion08()
                .withHandshakeHttpHeader("testHeader0", "testValue0")
                .withHandshakeHttpHeader("testHeader1", "testValue1")
                .withHandshakeHttpHeader("testHeader2", "testValue2")
                .build("localhost", PORT)) {

            testServer.start();

            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);

            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);

        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testCounterOfUnsuccessfulReconnectsIsResetAfterSuccessfulConnection() {
        final int maxAmountOfReconnects = 2;

        final CountDownLatch webSocketClosedSignal = new CountDownLatch(1);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);

        final CountDownLatch allowFirstReconnectSignal = new CountDownLatch(maxAmountOfReconnects);
        final CountDownLatch allowSecondReconnectSignal = new CountDownLatch(maxAmountOfReconnects);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {
            private boolean isFirstReconnect = true;
            private int amountOfSentPongFrames = 0;

            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            private void simulateConnectionLost() throws InterruptedException {
                if (isFirstReconnect) {
                    allowFirstReconnectSignal.await();
                    isFirstReconnect = false;
                } else {
                    allowSecondReconnectSignal.await();
                }

                acceptNextConnection = true;

            }

            @Override
            void processFrame(final boolean isLastFragment,
                    final int opcode, final ByteArea data, final long startIndex, final long length)
                    throws IOException, InterruptedException {

                final StringBuilder receivedMessage;

                switch (opcode) {
                    case PING_FRAME:
                        sendFrame(data, startIndex, length, PONG_FRAME, LAST_FRAME, NO_WS_EXTENSIONS);

                        simulateConnectionLost();

                        if (++amountOfSentPongFrames >= 3) {
                            readAndProcessFrame();
                        }

                        break;

                    case CONNECTION_CLOSE_FRAME:

                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage = new StringBuilder();
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());

                        acceptNextConnection = false;

                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnPong(byteArea.srcString(""), ZERO_START_INDEX, byteArea.length())
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnPong(byteArea.srcString(""), ZERO_START_INDEX, byteArea.length())
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnClosedByError(WEB_SOCKET_PATH, null, new WebSocketReadTimeoutException())
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnPong(byteArea.srcString(""), ZERO_START_INDEX, byteArea.length())
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {
            private WebSocket webSocket;
            private int reconnectionAttemptsCtr;
            private volatile boolean isFirstReconnect = true;
            private int receivedPongsCtr = 0;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());

                this.webSocket = webSocket;
                reconnectionAttemptsCtr = 0;
                webSocket.sendPing();
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
                if (++receivedPongsCtr >= 3) {
                    webSocket.close();
                }
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {

                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);

                if (isFirstReconnect) {
                    allowFirstReconnectSignal.countDown();
                } else {
                    allowSecondReconnectSignal.countDown();
                }

                if (reconnectionAttemptsCtr++ >= maxAmountOfReconnects - 1) {
                    if (isFirstReconnect) {
                        isFirstReconnect = false;
                        reconnectionAttemptsCtr = 0;
                        return;
                    }
                }

                if (receivedPongsCtr >= 3) {
                    webSocketClosedSignal.countDown();
                }
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS / 3)
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS / 3)
                .withNumberOfReconnectAttempts(maxAmountOfReconnects + 1)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
            e.printStackTrace();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }

    @Test
    void testOnSendPayloadFromOnCloseFrameCallback() {
        final CountDownLatch webSocketClosedSignal = new CountDownLatch(2);
        final CountDownLatch isServerUpSignal = new CountDownLatch(1);
        final CountDownLatch allPayloadIsReceived = new CountDownLatch(3);

        final BaseTestWebSocketServer testServer = new BaseTestWebSocketServer(PORT, isServerUpSignal) {

            @Override
            void communicateWithClient() throws IOException, InterruptedException {
                readAndProcessFrame();
            }

            @Override
            void processFrame(final boolean isLastFragment, final int opcode, final ByteArea data,
                    final long startIndex, final long length) throws IOException, InterruptedException {

                final StringBuilder receivedMessage = new StringBuilder();
                switch (opcode) {
                    case PING_FRAME:
                        allPayloadIsReceived.countDown();

                        closeWSConnection(NORMAL_CLOSURE_STATUS_CODE,
                                byteArea.srcString(NORMAL_CLOSURE_MESSAGE), 0, byteArea.length());

                        readAndProcessFrame();
                        break;

                    case BINARY_FRAME:
                        receivedMessage.setLength(0);
                        for (long i = startIndex; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(CLIENT_BINARY_MESSAGE, receivedMessage.toString());

                        allPayloadIsReceived.countDown();
                        readAndProcessFrame();
                        break;

                    case TEXT_FRAME:
                        receivedMessage.setLength(0);
                        for (long i = startIndex; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(CLIENT_TEXT_MESSAGE, receivedMessage.toString());

                        allPayloadIsReceived.countDown();
                        readAndProcessFrame();
                        break;

                    case CONNECTION_CLOSE_FRAME:
                        final int statusCode = ((int) data.getByte(0) << 8) | (data.getByte(1) & 255);

                        assertEquals(NORMAL_CLOSURE_STATUS_CODE, statusCode);

                        receivedMessage.setLength(0);
                        for (long i = startIndex + BYTES_IN_STATUS_CODE; i < startIndex + length; i++) {
                            receivedMessage.append((char) data.getByte(i));
                        }

                        assertEquals(NORMAL_CLOSURE_MESSAGE, receivedMessage.toString());

                        webSocketClosedSignal.countDown();
                        break;

                    default:
                        throw new IllegalStateException("Unexpected frame");
                }
            }
        };

        final WebSocketListenerEvents expectedEvents = new WebSocketListenerEvents()
                .addOnBeforeOpen()
                .addOnOpened(WEB_SOCKET_PATH, null)
                .addOnCloseFrame(charArea.srcString(NORMAL_CLOSURE_MESSAGE),
                        ZERO_START_INDEX, charArea.length(), NORMAL_CLOSURE_STATUS_CODE)
                .addOnClosed(WEB_SOCKET_PATH, null);

        final WebSocketListenerEvents receivedEvents = new WebSocketListenerEvents();

        final WebSocketListener listener = new WebSocketListener() {

            WebSocket webSocket;

            @Override
            public void onBeforeOpen(final WebSocketHandShaker handShaker) {
                receivedEvents.addOnBeforeOpen();
            }

            @Override
            public void onOpened(final WebSocket webSocket) {
                receivedEvents.addOnOpened(webSocket.path(), webSocket.query());
                this.webSocket = webSocket;
                webSocket.sendPing(); //signal for the server to initiate websocket close handshake
            }

            @Override
            public void onPing(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPing(message, startIndex, length);
            }

            @Override
            public void onPong(final ByteArea message, final long startIndex, final long length) {
                receivedEvents.addOnPong(message, startIndex, length);
            }

            @Override
            public void onBinaryFrame(final ByteArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnBinaryFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onTextFrame(final CharArea message, final long startIndex, final long length,
                    final boolean isFinalFragment) {
                receivedEvents.addOnTextFrame(message, startIndex, length, isFinalFragment);
            }

            @Override
            public void onCloseFrame(final CharArea message, final long startIndex, final long length,
                    final int statusCode) {
                receivedEvents.addOnCloseFrame(message, startIndex, length, statusCode);

                webSocket.sendText(charArea.srcString(CLIENT_TEXT_MESSAGE), ZERO_START_INDEX,
                        charArea.length());
                webSocket.sendBinary(byteArea.srcString(CLIENT_BINARY_MESSAGE), ZERO_START_INDEX, byteArea.length());
            }

            @Override
            public void onListenerError(final Throwable error) {
                receivedEvents.addOnListenerError(error);
            }

            @Override
            public void onClosed(final WebSocket webSocket) {
                receivedEvents.addOnClosed(webSocket.path(), webSocket.query());
                webSocketClosedSignal.countDown();
            }

            @Override
            public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
                receivedEvents.addOnClosedByError(webSocket.path(), webSocket.query(), error);
                webSocketClosedSignal.countDown();
            }
        };

        try (NettyWebSocketClient client = NettyWebSocketClient.builder()
                .withReconnectTimeout(DEFAULT_RECONNECT_TIMEOUT_MS)
                .withNoReconnect()
                .withReadTimeout(DEFAULT_READ_TIMEOUT_MS)
                .withLogger(testLogger)
                .build("localhost", PORT)) {

            testServer.start();
            isServerUpSignal.await();

            client.openConnection(WEB_SOCKET_PATH, null, listener);
            webSocketClosedSignal.await();
            allPayloadIsReceived.await();

            assertEquals(expectedEvents, receivedEvents);
        } catch (final InterruptedException e) {
            testServer.interrupt();
        } finally {
            try {
                testServer.join();
            } catch (final InterruptedException e) {
                testServer.interrupt();
            }
        }
    }
}