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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;

abstract class BaseTestWebSocketServer extends Thread {
    private static final String EOL = "\r\n";
    private static final int EOS = -1;
    private static final String WEBSOCKET_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String SHA1 = "SHA-1";
    private static final String WEBSOCKET_KEY_HEADER = "sec-websocket-key";
    private static final String RESPONSE_PATTERN = "HTTP/1.1 101 Switching Protocols" + EOL +
            "Upgrade: websocket" + EOL +
            "Connection: Upgrade" + EOL +
            "Sec-WebSocket-Accept: %s" + EOL + EOL;

    private final int port;
    private final CountDownLatch isUpSignal;

    private static final int FIN_MASK = 0b010000000;
    private static final int OPCODE_MASK = 0b00001111;
    private static final int MASK_BIT_NUMBER = 7;
    private static final int PAYLOAD_LEN_MASK = 0b01111111;

    private static final int MAX_PAYLOAD_LENGTH = 125;
    private static final int ZERO_START_INDEX = 0;
    private static final int STATUS_CODE_BYTES = 2;
    private static final int BYTES_IN_MASK = 4;

    private static final int CONTINUATIION_FRAME = 0;
    private static final int TEXT_FRAME = 1;
    private static final int BINARY_FRAME = 2;
    private static final int CONNECTION_CLOSE_FRAME = 8;
    private static final int PING_FRAME = 9;
    private static final int PONG_FRAME = 10;

    List<String> incomingHandshakeRequestLines;

    private InputStream in;
    private OutputStream out;

    boolean acceptNextConnection = true;

    BaseTestWebSocketServer(final int port, final CountDownLatch isUpSignal) {
        this.port = port;
        this.isUpSignal = isUpSignal;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            if (isUpSignal != null) {
                isUpSignal.countDown();
            }

            while (acceptNextConnection) {
                Socket clientSocket = null;

                try {
                    acceptNextConnection = false;

                    clientSocket = serverSocket.accept();
                    in = clientSocket.getInputStream();
                    out = clientSocket.getOutputStream();

                    incomingHandshakeRequestLines = handleHandshake();

                    communicateWithClient();

                } catch (final IOException e) {
                    acceptNextConnection = true;
                } catch (final Exception e) {
                    e.printStackTrace();
                    return;
                } finally {
                    try {
                        in.close();
                        out.close();
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    final void sendFrame(final ByteArea message, final long startIndex, final long length, final int opcode,
                         final boolean isFinalFragment, final int rcv) throws IOException {

        int b0 = 0;
        if (isFinalFragment) {
            b0 |= 1 << 7;
        }

        b0 |= rcv % 8 << 4;
        b0 |= opcode & 0b00001111;

        out.write(b0);
        out.write((int) (127 & length)); //127 - zero at 7th bit (MASK)

        for (long i = startIndex; i < startIndex + length; i++) {
            out.write(message.getByte(i));
        }

        out.flush();
    }

    final void closeWSConnection(final int statusCode, final ByteArea message, final long startIndex,
            final long length) throws IOException {

        int b0 = 1 << 7;
        b0 |= CONNECTION_CLOSE_FRAME;

        out.write(b0);
        out.write((int) (127 & (length + 2))); //127 - zero at 7th bit (MASK)

        out.write(statusCode >> 8);
        out.write(statusCode);

        for (long i = startIndex; i < startIndex + length; i++) {
            out.write(message.getByte(i));
        }

        out.flush();
    }


    List<String> handleHandshake() throws Exception {
        String crt;
        String handshakeWSkey = null;
        final List<String> incomingHandshakeLines = new ArrayList<>();
        while (!(crt = readLine(in)).isEmpty()) {
            incomingHandshakeLines.add(crt);
            final String[] split = crt.split(": ");

            if (split[0].equals(WEBSOCKET_KEY_HEADER)) {
                handshakeWSkey = encodeWSKey(split[1] + WEBSOCKET_ACCEPT_GUID);
            }
        }

        if (handshakeWSkey == null) {
            throw new IllegalStateException("Incoming handshake has no key");
        }

        out.write(String.format(RESPONSE_PATTERN, handshakeWSkey).getBytes());
        out.flush();

        return incomingHandshakeLines;
    }

    final String readLine(final InputStream in) throws IOException {
        final StringBuilder res = new StringBuilder();

        while (true) {
            final int crtByte = in.read();

            if (crtByte == EOS) {
                throw new IOException("Input stream is closed");
            }

            if ((char) crtByte == '\r') {
                in.read();
                break;
            }

            res.append((char) crtByte);
        }

        return res.toString();
    }

    final void readAndProcessFrame() throws IOException, InterruptedException {
        final int b0 = in.read();

        if (b0 == EOS) {
            throw new IOException("Input stream is closed");
        }

        final boolean isLastFragment = (b0 & FIN_MASK) != 0;
        final int opcode = b0 & OPCODE_MASK;

        final int b1 = in.read();

        if (b1 == EOS) {
            throw new IOException("Input stream is closed");
        }

        final boolean isMasked = (b1 >> MASK_BIT_NUMBER) == 1;

        if (!isMasked) {
            throw new IllegalStateException("Frame received form client was not masked!");
        }

        final int payloadLength = b1 & PAYLOAD_LEN_MASK;
        if (payloadLength > MAX_PAYLOAD_LENGTH) {
            throw new IllegalStateException("Too huge payload");
        }

        final byte mask[] = new byte[BYTES_IN_MASK];

        for (int i = 0; i < BYTES_IN_MASK; i++) {
            final int crtByte = in.read();

            if (crtByte == EOS) {
                throw new IOException("Input stream is closed");
            }

            mask[i] = (byte) crtByte;
        }

        final byte[] payload = new byte[payloadLength];

        for (int i = 0; i < payloadLength; i++) {
            final int crtByte = in.read();

            if (crtByte == EOS) {
                throw new IOException("Input stream is closed");
            }

            payload[i] = (byte) (((byte) crtByte) ^ mask[i % BYTES_IN_MASK]);
        }

        processFrame(isLastFragment, opcode, index -> payload[(int) index], ZERO_START_INDEX, payloadLength);
    }

    abstract void communicateWithClient() throws IOException, InterruptedException;

    abstract void processFrame(boolean isLastFragment, int opcode, ByteArea data, long startIndex, long length)
            throws IOException, InterruptedException;

    private String encodeWSKey(final String key) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance(SHA1);

        digest.reset();
        digest.update(key.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(digest.digest());
    }
}