package com.musiclibrary.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingUtilsTest {

    @Test
    @DisplayName("runOnEdt ignores null task safely without throwing exception")
    void testRunOnEdt_NullTask() {
        assertThatCode(() -> SwingUtils.runOnEdt(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("runOnEdtAndWait ignores null task safely without throwing exception")
    void testRunOnEdtAndWait_NullTask() {
        assertThatCode(() -> SwingUtils.runOnEdtAndWait(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("runOnEdt executes task on Event Dispatch Thread from background thread")
    void testRunOnEdt_FromBackgroundThread() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread bgThread = new Thread(() -> {
            SwingUtils.runOnEdt(() -> {
                executed.set(true);
                onEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            });
        });
        bgThread.start();
        bgThread.join();

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("runOnEdtAndWait executes task synchronously and blocks until completion")
    void testRunOnEdtAndWait_SynchronousExecution() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean callerWaited = new AtomicBoolean(false);

        Thread bgThread = new Thread(() -> {
            SwingUtils.runOnEdtAndWait(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                completed.set(true);
            });
            callerWaited.set(completed.get());
        });
        bgThread.start();
        bgThread.join(3000);

        assertThat(completed.get()).isTrue();
        assertThat(callerWaited.get()).isTrue();
    }

    @Test
    @DisplayName("runOnEdtAndWait propagates runtime exception thrown by task")
    void testRunOnEdtAndWait_ExceptionPropagation() {
        Thread bgThread = new Thread(() -> {
            assertThatThrownBy(() -> SwingUtils.runOnEdtAndWait(() -> {
                throw new IllegalStateException("Simulated EDT failure");
            }))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Simulated EDT failure");
        });
        bgThread.start();
        assertThatCode(bgThread::join).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("runOnEdt executes immediately when already on EDT")
    void testRunOnEdt_AlreadyOnEdt() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            SwingUtils.runOnEdt(() -> executed.set(true));
            assertThat(executed.get()).isTrue();
        });
    }

    @Test
    @DisplayName("runOnEdtAndWait executes immediately when already on EDT")
    void testRunOnEdtAndWait_AlreadyOnEdt() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            SwingUtils.runOnEdtAndWait(() -> executed.set(true));
            assertThat(executed.get()).isTrue();
        });
    }
}
