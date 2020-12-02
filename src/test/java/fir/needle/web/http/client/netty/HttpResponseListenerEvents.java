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

import fir.needle.joint.io.ByteArea;
import fir.needle.web.http.client.AbstractHttpClientException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class HttpResponseListenerEvents {
    private final List<Object> events = new ArrayList<>();

    boolean isEmpty() {
        return events.isEmpty();
    }

    HttpResponseListenerEvents addOnConnected(final String method, final String path, final String query) {
        events.add(new OnConnected(method, path, query));
        return this;
    }

    HttpResponseListenerEvents addOnBeforeRequestSent(final String method, final String path, final String query) {
        events.add(new OnBeforeRequestSent(method, path, query));
        return this;
    }

    HttpResponseListenerEvents addOnResponseStarted(final String method, final String path, final String query,
            final int code) {

        events.add(new OnResponseStarted(method, path, query, code));
        return this;
    }

    HttpResponseListenerEvents addOnHeader(final CharSequence key, final CharSequence value) {
        events.add(new OnHeader(key, value));
        return this;
    }

    HttpResponseListenerEvents addOnBodyStarted() {
        events.add(new OnBodyStarted());
        return this;
    }

    HttpResponseListenerEvents addOnBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        events.add(new OnBodyContent(buffer, startIndex, length));
        return this;
    }

    HttpResponseListenerEvents addOnBodyFinished() {
        events.add(new OnBodyFinished());
        return this;
    }

    HttpResponseListenerEvents addOnResponseFinished() {
        events.add(new OnResponseFinished());
        return this;
    }

    HttpResponseListenerEvents addOnListenerError(final Throwable err) {
        events.add(new OnListenerError(err));
        return this;
    }

    HttpResponseListenerEvents addOnDisconnected(final String method, final String path, final String query) {
        events.add(new OnDisconnected(method, path, query));
        return this;
    }

    HttpResponseListenerEvents addOnDisconnectedByError(final String method, final String path, final String query,
            final AbstractHttpClientException error) {

        events.add(new OnDisconnectedByError(method, path, query, error));
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(events);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final HttpResponseListenerEvents o = (HttpResponseListenerEvents) obj;
        return events.equals(o.events);
    }

    private static final class OnConnected {
        private final String method;
        private final String path;
        private final String query;

        private OnConnected(final String method, final String path, final String query) {
            this.method = method;
            this.path = path;
            this.query = query;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnConnected o = (OnConnected) obj;
            return method.equals(o.method) && path.equals(o.path) && query.equals(o.query);
        }
    }

    private static final class OnBeforeRequestSent {
        private final String method;
        private final String path;
        private final String query;

        private OnBeforeRequestSent(final String method, final String path, final String query) {
            this.method = method;
            this.path = path;
            this.query = query;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnBeforeRequestSent o = (OnBeforeRequestSent) obj;
            return method.equals(o.method) && path.equals(o.path) && query.equals(o.query);
        }
    }

    private static final class OnResponseStarted {
        private final String method;
        private final String path;
        private final String query;
        private final int code;

        private OnResponseStarted(final String method, final String path, final String query, final int code) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.code = code;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnResponseStarted o = (OnResponseStarted) obj;
            return method.equals(o.method) && path.equals(o.path) && query.equals(o.query) && code == o.code;
        }
    }

    private static final class OnHeader {
        private final CharSequence key;
        private final CharSequence value;

        private OnHeader(final CharSequence key, final CharSequence value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnHeader o = (OnHeader) obj;
            return key.equals(o.key) && value.equals(o.value);
        }
    }

    private static final class OnBodyStarted {

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            return obj != null && getClass() == obj.getClass();
        }
    }

    private static final class OnBodyContent {
        private final byte[] buffer;

        private OnBodyContent(final ByteArea buffer, final long startIndex, final long length) {
            this.buffer = new byte[(int) length];

            for (long i = startIndex; i < startIndex + length; i++) {
                this.buffer[(int) i] = buffer.getByte(i);
            }
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            return Arrays.equals(buffer, ((OnBodyContent) obj).buffer);
        }
    }

    private static final class OnBodyFinished {

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            return obj != null && getClass() == obj.getClass();
        }
    }

    private static final class OnResponseFinished {

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            return obj != null && getClass() == obj.getClass();
        }
    }

    private static final class OnListenerError {
        private final Throwable error;

        private OnListenerError(final Throwable error) {
            this.error = error;
        }


        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            return error.getMessage().equals(((OnListenerError) obj).error.getMessage());
        }
    }

    private static final class OnDisconnected {
        private final String method;
        private final String path;
        private final String query;

        private OnDisconnected(final String method, final String path, final String query) {
            this.method = method;
            this.path = path;
            this.query = query;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnDisconnected o = (OnDisconnected) obj;
            return method.equals(o.method) && path.equals(o.path) && query.equals(o.query);
        }
    }

    private static final class OnDisconnectedByError {
        private final String method;
        private final String path;
        private final String query;
        private final AbstractHttpClientException error;

        private OnDisconnectedByError(final String method, final String path, final String query,
                final AbstractHttpClientException error) {

            this.method = method;
            this.path = path;
            this.query = query;
            this.error = error;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnDisconnectedByError o = (OnDisconnectedByError) obj;
            return method.equals(o.method) && path.equals(o.path) && query.equals(o.query) && error.equals(o.error);
        }
    }
}
