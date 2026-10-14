package com.musiclibrary.util;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Utility methods for managing Swing Event Dispatch Thread (EDT) execution safely and uniformly.
 */
public final class SwingUtils {

    private SwingUtils() {
        // Prevent instantiation
    }

    /**
     * Executes the task on the Event Dispatch Thread asynchronously.
     * If already on the EDT (or in a headless environment), runs the task immediately.
     *
     * @param task task to execute
     */
    public static void runOnEdt(Runnable task) {
        if (task == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread() || GraphicsEnvironment.isHeadless()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /**
     * Executes the task on the Event Dispatch Thread synchronously, blocking until complete.
     * If already on the EDT (or in a headless environment), runs the task immediately.
     *
     * @param task task to execute
     */
    public static void runOnEdtAndWait(Runnable task) {
        if (task == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread() || GraphicsEnvironment.isHeadless()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("Exception occurred while executing task on EDT", cause);
            }
        }
    }
}
