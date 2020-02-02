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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;

class EchoServer extends Thread {
    private static final String EOL = "\r\n";

    private final int port;
    private final List<Integer> bytesToAccept;
    private final CountDownLatch isUpSignal;

    volatile String receivedMessage;
    volatile String sentMessage;


    EchoServer(final int port, final List<Integer> bytesToAccept, final CountDownLatch isUpSignal) {
        this.port = port;
        this.bytesToAccept = bytesToAccept;
        this.isUpSignal = isUpSignal;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            if (isUpSignal != null) {
                isUpSignal.countDown();
            }

            try (Socket clientSocket = serverSocket.accept();
                    InputStream in = clientSocket.getInputStream();
                    OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream())) {


                for (final Integer bytesInCrtMessage : bytesToAccept) {

                    final StringBuilder result = new StringBuilder()
                            .append("HTTP/1.1 200 OK")
                            .append(EOL)
                            .append("Content-Type: text/html; charset=utf-8")
                            .append(EOL)
                            .append("Content-Length: ");

                    final byte b[] = new byte[bytesInCrtMessage];
                    readFully(in, b, 0, b.length);

                    receivedMessage = new String(b, 0, b.length);

                    result.append(b.length)
                            .append(EOL)
                            .append(EOL)
                            .append(receivedMessage);

                    sentMessage = result.toString();
                    out.write(result.toString());
                    out.flush();
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private static void readFully(final InputStream in, final byte[] b, final int off, final int len)
            throws IOException {

        if (len < 0) {
            throw new IndexOutOfBoundsException();
        }

        int n = 0;
        while (n < len && !Thread.currentThread().isInterrupted()) {
            final int count = in.read(b, off + n, len - n);
            if (count < 0) {
                return;
            }
            n += count;
        }
    }
}
