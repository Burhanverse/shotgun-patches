package dev.jason.gboardpatches.extension.filetransfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FileTransferLockControllerTest {
    @Test
    public void refCountingIncrementsAndDecrementsCorrectly() {
        FileTransferLockController controller = new FileTransferLockController(null);

        assertEquals(0, controller.getActiveStreamsCount());
        assertFalse(controller.isWakeLockHeld());
        assertFalse(controller.isWifiLockHeld());

        controller.acquire();
        assertEquals(1, controller.getActiveStreamsCount());

        controller.acquire();
        assertEquals(2, controller.getActiveStreamsCount());

        controller.release();
        assertEquals(1, controller.getActiveStreamsCount());

        controller.release();
        assertEquals(0, controller.getActiveStreamsCount());

        controller.release();
        assertEquals(0, controller.getActiveStreamsCount());
    }

    @Test
    public void releaseAllResetsCount() {
        FileTransferLockController controller = new FileTransferLockController(null);

        controller.acquire();
        controller.acquire();
        assertEquals(2, controller.getActiveStreamsCount());

        controller.releaseAll();
        assertEquals(0, controller.getActiveStreamsCount());
    }

    @Test
    public void safetyTimeoutForcesRelease() throws Exception {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        FileTransferLockController controller = new FileTransferLockController(null, executor);

        controller.acquire();
        assertEquals(1, controller.getActiveStreamsCount());

        controller.releaseAll();
        assertEquals(0, controller.getActiveStreamsCount());
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    public void touchReschedulesSafetyTimeout() throws Exception {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        FileTransferLockController controller = new FileTransferLockController(null, executor);

        controller.acquire();
        assertEquals(1, controller.getActiveStreamsCount());
        assertEquals(1, executor.getQueue().size());

        controller.touch();
        assertEquals(1, controller.getActiveStreamsCount());

        controller.release();
        assertEquals(0, controller.getActiveStreamsCount());
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    public void touchRateLimitsRapidCalls() throws Exception {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        FileTransferLockController controller = new FileTransferLockController(null, executor);

        controller.acquire();
        assertEquals(1, executor.getQueue().size());

        controller.touch();
        assertEquals(2, executor.getQueue().size());

        controller.touch();
        assertEquals(2, executor.getQueue().size());

        controller.release();
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }
}
