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


import fir.needle.joint.lang.Cancelable;
import fir.needle.web.http.client.Get;
import fir.needle.web.http.client.HttpResponseListener;
import fir.needle.web.http.client.PreparableRequestsFactory;
import fir.needle.web.http.client.PreparedGet;
import fir.needle.web.http.client.ScheduledGet;

public class NettyPreparedGet extends RequestBuilder<NettyPreparedGet> implements PreparedGet {

    @Override
    public void send(final HttpResponseListener<Get> listener) {
        this.listener = listener;
        if (chain == null) {
            chain = new PreparedRequestsChain(client.reconnectAttemptsNumber);
        }

        client.sendPreparedChain(chain.add(new NettyGetRequest(this)));
    }

    @Override
    public Cancelable schedule(final int repeatPeriodMs, final HttpResponseListener<ScheduledGet> listener) {
        this.listener = listener;
        this.repeatPeriodMs = repeatPeriodMs;

        final NettyScheduledGet result = new NettyScheduledGet(this);
        client.sendScheduledRequest(result);

        return result;
    }

    @Override
    public PreparableRequestsFactory addToChain(final HttpResponseListener<Get> listener) {
        this.listener = listener;

        if (chain == null) {
            chain = new PreparedRequestsChain(client.reconnectAttemptsNumber);
        }
        chain.add(new NettyGetRequest(this));

        return this;
    }
}