/**
 * MIT License
 * <p>
 * Copyright (c) 2019 Anatoly Gudkov
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

public interface WebSocketListener {

    void onBeforeOpen(WebSocketHandShaker handShaker);

    void onOpened(WebSocket webSocket);

    void onPing(ByteArea message, long startIndex, long length);

    void onPong(ByteArea message, long startIndex, long length);

    void onBinaryFrame(ByteArea message, long startIndex, long length, boolean isFinalFragment);

    void onTextFrame(CharArea message, long startIndex, long length, boolean isFinalFragment);

    void onCloseFrame(CharArea message, long startIndex, long length, int statusCode);

    void onListenerError(Throwable error);

    void onClosed(WebSocket webSocket);

    void onClosedByError(WebSocket webSocket, AbstractWebSocketClientException error);
}