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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class WebSocketListenerEvents {
    private final List<Object> events = new ArrayList<>();

    boolean isEmpty() {
        return events.isEmpty();
    }

    WebSocketListenerEvents addOnOpened(final String path, final String query) {
        events.add(new OnOpened(path, query));
        return this;
    }

    WebSocketListenerEvents addOnPing(final ByteArea message, final long startIndex, final long length) {
        events.add(new OnPing(message, startIndex, length));
        return this;
    }

    WebSocketListenerEvents addOnPong(final ByteArea message, final long startIndex, final long length) {
        events.add(new OnPong(message, startIndex, length));
        return this;
    }

    WebSocketListenerEvents addOnBinaryFrame(final ByteArea message, final long startIndex, final long length,
            final boolean isFinalFragment) {

        events.add(new OnBinaryFrame(message, startIndex, length, isFinalFragment));
        return this;
    }

    WebSocketListenerEvents addOnTextFrame(final CharArea message, final long startIndex, final long length,
                                           final boolean isFinalFragment) {

        events.add(new OnTextFrame(message, startIndex, length, isFinalFragment));
        return this;
    }

    WebSocketListenerEvents addOnListenerError(final Throwable error) {
        events.add(new OnListenerError(error));
        return this;
    }

    WebSocketListenerEvents addOnClosed(final String path, final String query, final CharArea message,
            final long startIndex, final long length, final int statusCode) {
        events.add(new OnClosed(path, query, message, startIndex, length, statusCode));
        return this;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        return events.equals(((WebSocketListenerEvents) obj).events);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private final class OnOpened {
        private final String path;
        private final String query;

        private OnOpened(final String path, final String query) {
            this.path = path;
            this.query = query;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnOpened o = (OnOpened) obj;
            return path.equals(o.path) && (query == null && o.query == null || query != null && query.equals(o.query));
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private final class OnPing {
        private final byte[] buffer;

        private OnPing(final ByteArea message, final long startIndex, final long length) {
            this.buffer = new byte[(int) length];

            for (long i = startIndex; i < startIndex + length; i++) {
                this.buffer[(int) i] = message.getByte(i);
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            return Arrays.equals(buffer, ((OnPing) obj).buffer);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private final class OnPong {
        private final byte[] buffer;

        private OnPong(final ByteArea message, final long startIndex, final long length) {
            this.buffer = new byte[(int) length];

            for (long i = startIndex; i < startIndex + length; i++) {
                this.buffer[(int) i] = message.getByte(i);
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            return Arrays.equals(buffer, ((OnPong) obj).buffer);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private final class OnBinaryFrame {
        private final byte[] buffer;
        private final boolean isFinalFragment;

        private OnBinaryFrame(final ByteArea message, final long startIndex, final long length,
                final boolean isFinalFragment) {

            this.buffer = new byte[(int) length];
            this.isFinalFragment = isFinalFragment;

            for (long i = startIndex; i < startIndex + length; i++) {
                this.buffer[(int) i] = message.getByte(i);
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            return isFinalFragment == ((OnBinaryFrame) obj).isFinalFragment &&
                    Arrays.equals(buffer, ((OnBinaryFrame) obj).buffer);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private final class OnTextFrame {
        private final char[] buffer;
        private final boolean isFinalFragment;

        private OnTextFrame(final CharArea message, final long startIndex, final long length,
                final boolean isFinalFragment) {

            this.buffer = new char[(int) length];
            this.isFinalFragment = isFinalFragment;

            for (long i = startIndex; i < startIndex + length; i++) {
                this.buffer[(int) i] = message.getChar(i);
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnTextFrame o = (OnTextFrame) obj;
            return Arrays.equals(buffer, o.buffer) && isFinalFragment == o.isFinalFragment;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private final class OnListenerError {
        private final Throwable throwable;

        private OnListenerError(final Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final OnListenerError that = (OnListenerError) o;
            return throwable.getClass().equals(that.throwable.getClass()) &&
                    throwable.getMessage().equals(that.throwable.getMessage());
        }

        @Override
        public int hashCode() {
            return Objects.hash(throwable);
        }
    }

    private final class OnClosed {
        private final String path;
        private final String query;
        private final char[] buffer;
        private final int statusCode;

        private OnClosed(final String path, final String query, final CharArea message, final long startIndex,
                final long length, final int statusCode) {

            this.path = path;
            this.query = query;
            this.buffer = new char[(int) length];

            for (long i = 0; i < length; i++) {
                this.buffer[(int) i] = message.getChar(i + startIndex);
            }
            this.statusCode = statusCode;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj != null && getClass() == obj.getClass())) {
                return false;
            }

            final OnClosed o = (OnClosed) obj;
            return path.equals(o.path) &&
                    (query == null && o.query == null || query != null && query.equals(o.query)) &&
                    Arrays.equals(buffer, o.buffer) && statusCode == o.statusCode;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }
}
