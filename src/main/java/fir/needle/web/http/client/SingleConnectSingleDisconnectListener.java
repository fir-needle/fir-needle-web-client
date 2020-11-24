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
package fir.needle.web.http.client;

public abstract class SingleConnectSingleDisconnectListener<R extends HttpRequest> implements HttpResponseListener<R> {

    private static final int INITIAL = 0;
    private static final int DISCONNECTED = 1;
    private static final int CONNECTED = 2;

    private int state = INITIAL;

    @Override
    public final void onConnected(final R request) {
        if (state < CONNECTED) {
            onDoConnected(request);
            state = CONNECTED;
        }
    }

    @Override
    public final void onDisconnected(final R request) {
        if (state != DISCONNECTED) {
            onDoDisconnected(request);
            state = DISCONNECTED;
        }
    }

    @Override
    public final void onDisconnectedByError(final R request, final AbstractHttpClientException error) {
        if (state != DISCONNECTED) {
            onDoDisconnectedByError(request, error);
            state = DISCONNECTED;
        }
    }

    protected abstract void onDoConnected(R request);

    protected abstract void onDoDisconnected(R request);

    protected abstract void onDoDisconnectedByError(R request, AbstractHttpClientException error);
}