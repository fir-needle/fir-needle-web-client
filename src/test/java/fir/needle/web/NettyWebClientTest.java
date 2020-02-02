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
package fir.needle.web;

import fir.needle.joint.lang.Cancelable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NettyWebClientTest {
    private static final int TEST_TIMEOUT_SECONDS = 3;
    private final SilentTestLogger silentLogger = new SilentTestLogger();

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testExecute() {
        try (NettyWebClient nettyWebClient = new NettyWebClient()) {
            final CountDownLatch executeFinishedSignal = new CountDownLatch(1);

            nettyWebClient.execute(executeFinishedSignal::countDown);

            executeFinishedSignal.await();

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testSchedule() {
        try (NettyWebClient nettyWebClient = new NettyWebClient()) {
            final CountDownLatch executeFinishedSignal = new CountDownLatch(3);

            final Cancelable schedule = nettyWebClient.schedule(executeFinishedSignal::countDown, 500);

            executeFinishedSignal.await();
            schedule.cancel();

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testEventLoopGroupIsNotClosedAfterWebSocketClientClosed() {
        try (NettyWebClient nettyWebClient = new NettyWebClient()) {
            nettyWebClient.webSocketBuilder()
                    .withLogger(silentLogger)
                    .build("localhost", 8080)
                    .close();

            final CountDownLatch executeFinishedSignal = new CountDownLatch(1);

            assertDoesNotThrow(() -> nettyWebClient.execute(executeFinishedSignal::countDown));

            executeFinishedSignal.await();

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    void testEventLoopGroupIsNotClosedAfterHttpClientClosed() {
        try (NettyWebClient nettyWebClient = new NettyWebClient()) {
            nettyWebClient.httpClientBuilder()
                    .withLogger(silentLogger)
                    .build("localhost", 8080)
                    .close();

            final CountDownLatch executeFinishedSignal = new CountDownLatch(1);

            assertDoesNotThrow(() -> nettyWebClient.execute(executeFinishedSignal::countDown));

            executeFinishedSignal.await();

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS * 30)
    @Disabled
    void testCloseWhileScheduledTask() {
        try (NettyWebClient nettyWebClient = new NettyWebClient()) {
            final CountDownLatch executeFinishedSignal = new CountDownLatch(3);

            final Cancelable schedule = nettyWebClient.schedule(() -> {
                try {
                    System.out.println("+1");
                    TimeUnit.SECONDS.sleep(5);
                    System.out.println("+2");
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }, 1000);

            //            executeFinishedSignal.await();
//            schedule.cancel();

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}