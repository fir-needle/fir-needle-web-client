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

public abstract class SingleConnectSingleDisconnectListener implements WebSocketListener {
    private static final int INITIAL = 0;
    private static final int OPENED = 1;
    private static final int CLOSED = 2;

    private int state = INITIAL;

    @Override
    public void onOpened(final WebSocket webSocket) {
        if (state < OPENED) {
            onOpen(webSocket);
            state = OPENED;
        }
    }

    @Override
    public void onClosed(final WebSocket webSocket) {
        if (state != CLOSED) {
            onClose(webSocket);
            state = CLOSED;
        }
    }

    @Override
    public void onClosedByError(final WebSocket webSocket, final AbstractWebSocketClientException error) {
        if (state != CLOSED) {
            onCloseByError(webSocket, error);
            state = CLOSED;
        }
    }

    protected abstract void onOpen(WebSocket webSocket);

    protected abstract void onClose(WebSocket webSocket);

    protected abstract void onCloseByError(WebSocket webSocket, AbstractWebSocketClientException error);
}
