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

import fir.needle.joint.io.ByteArea;

public class SingleConnectSingleDisconnectAdapter<R extends HttpRequest> extends
        SingleConnectSingleDisconnectListener<R> {

    @Override
    protected void onConnect(final R request) {
        //
    }

    @Override
    public void onResponseStarted(final R request, final int code) {
        //
    }

    @Override
    public void onHeader(final CharSequence key, final CharSequence value) {
        //
    }

    @Override
    public void onBodyStarted() {
        //
    }

    @Override
    public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        //
    }

    @Override
    public void onBodyFinished() {
        //
    }

    @Override
    public void onResponseFinished() {
        //
    }

    @Override
    public void onListenerError(final Throwable error) {
        //
    }

    @Override
    public void onDisconnectedByError(final R request, final String reason) {
        //
    }

    @Override
    protected void onDisconnect(final R request) {
        //
    }

    @Override
    protected void onDisconnectByError(final R request, final String reason) {
        //
    }
}
