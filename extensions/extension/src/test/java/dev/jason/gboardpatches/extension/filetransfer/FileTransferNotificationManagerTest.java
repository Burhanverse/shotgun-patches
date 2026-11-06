package dev.jason.gboardpatches.extension.filetransfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class FileTransferNotificationManagerTest {
    private FileTransferNotificationManager manager;
    private FileOfferRegistry registry;

    @Before
    public void setUp() {
        manager = FileTransferNotificationManager.getInstance();
        manager.init(RuntimeEnvironment.getApplication());
        registry = new FileOfferRegistry(600_000L);
        manager.setFileOfferRegistry(registry);
        manager.setFileStreamHandler(new FileStreamHandler());
    }

    @Test
    public void batchLifecycleTracksProgressAndCompletion() {
        String batchId = "batch_test_1";
        String token = "token_test_1";
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);

        FileOffer offer = FileOffer.forTestingStream(
                token,
                new ByteArrayInputStream(content),
                "hello.txt",
                content.length,
                "text/plain",
                batchId,
                0,
                1,
                null);

        registry.register(offer);
        manager.onBatchStarted(null, batchId, Collections.singletonList(offer));

        manager.onBytesServed(batchId, 5L);
        manager.onBytesServed(batchId, 6L);

        manager.onOfferCompleted(batchId, token);
        assertFalse(offer.isClosed());
    }

    @Test
    public void cancelBatchClosesRegistryOffers() {
        String batchId = "batch_cancel_1";
        String token = "token_cancel_1";
        byte[] content = "Cancel me".getBytes(StandardCharsets.UTF_8);

        FileOffer offer = FileOffer.forTestingStream(
                token,
                new ByteArrayInputStream(content),
                "cancel.txt",
                content.length,
                "text/plain",
                batchId,
                0,
                1,
                null);

        registry.register(offer);
        manager.onBatchStarted(null, batchId, Collections.singletonList(offer));

        assertFalse(offer.isClosed());
        assertEquals(1, registry.size());

        manager.cancelBatch(batchId);

        assertTrue(offer.isClosed());
        assertEquals(0, registry.size());
    }

    @Test
    public void cancelAllClearsBatches() {
        String batchId1 = "batch_all_1";
        String token1 = "token_all_1";
        String batchId2 = "batch_all_2";
        String token2 = "token_all_2";

        FileOffer offer1 = FileOffer.forTestingStream(
                token1,
                new ByteArrayInputStream(new byte[10]),
                "f1.bin",
                10L,
                "application/octet-stream",
                batchId1,
                0,
                1,
                null);

        FileOffer offer2 = FileOffer.forTestingStream(
                token2,
                new ByteArrayInputStream(new byte[20]),
                "f2.bin",
                20L,
                "application/octet-stream",
                batchId2,
                0,
                1,
                null);

        registry.register(offer1);
        registry.register(offer2);

        manager.onBatchStarted(null, batchId1, Collections.singletonList(offer1));
        manager.onBatchStarted(null, batchId2, Collections.singletonList(offer2));

        manager.cancelAll();

        assertTrue(offer1.isClosed());
        assertTrue(offer2.isClosed());
        assertEquals(0, registry.size());
    }

    @Test
    public void uploadStartedAndProgressPublishesReceiveProgressNotification() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        org.robolectric.shadows.ShadowNotificationManager shadowNm = org.robolectric.Shadows.shadowOf(nm);
        nm.cancelAll();

        manager.onUploadStarted("up1", "photo.jpg", 1000L, 0L, "b1", 1, 2);

        Notification progress = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_PROGRESS);
        assertTrue(progress != null);
        assertEquals("Receiving 1/2", progress.extras.getString(Notification.EXTRA_TITLE));

        manager.onUploadProgress("up1", 500L, 1000L);
    }

    @Test
    public void uploadCompletedSingleFileCancelsProgressAndPostsResult() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        org.robolectric.shadows.ShadowNotificationManager shadowNm = org.robolectric.Shadows.shadowOf(nm);
        nm.cancelAll();

        manager.onUploadStarted("up2", "doc.pdf", 2000L, 0L, null, 1, 1);
        Uri fileUri = Uri.parse("content://media/external/downloads/123");
        manager.onUploadCompleted("up2", "doc.pdf", null, 1, 1, fileUri, "application/pdf");

        Notification progress = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_PROGRESS);
        Notification result = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertNull(progress);
        assertTrue(result != null);
        assertEquals("Transfer complete", result.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Received doc.pdf", result.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void uploadCompletedBatchSummarizesWhenAllFinished() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        org.robolectric.shadows.ShadowNotificationManager shadowNm = org.robolectric.Shadows.shadowOf(nm);
        nm.cancelAll();

        manager.onUploadStarted("up3_1", "file1.txt", 100L, 0L, "batchX", 1, 2);
        manager.onUploadCompleted("up3_1", "file1.txt", "batchX", 1, 2, Uri.parse("content://media/1"), "text/plain");

        assertNull(shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT));

        manager.onUploadStarted("up3_2", "file2.txt", 100L, 0L, "batchX", 2, 2);
        manager.onUploadCompleted("up3_2", "file2.txt", "batchX", 2, 2, Uri.parse("content://media/2"), "text/plain");

        Notification result = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertTrue(result != null);
        assertEquals("Transfer complete", result.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("2 files received", result.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void uploadFailedCancelsProgressAndPostsResult() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        org.robolectric.shadows.ShadowNotificationManager shadowNm = org.robolectric.Shadows.shadowOf(nm);
        nm.cancelAll();

        manager.onUploadStarted("up4", "broken.bin", 500L, 0L, null, 1, 1);
        manager.onUploadFailed("up4", null, "Disk full");

        assertNull(shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_PROGRESS));
        Notification result = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertTrue(result != null);
        assertEquals("Transfer failed", result.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Disk full", result.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void cancelUploadAbortsActiveUploadAndPostsCancelledResult() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        org.robolectric.shadows.ShadowNotificationManager shadowNm = org.robolectric.Shadows.shadowOf(nm);
        nm.cancelAll();

        ActiveUploadRegistry uploadRegistry = new ActiveUploadRegistry();
        manager.setActiveUploadRegistry(uploadRegistry);

        File tempFile = File.createTempFile("test_sink", ".tmp");
        TempFileUploadSink sink = new TempFileUploadSink(tempFile);
        ActiveUploadRegistry.UploadSession session = new ActiveUploadRegistry.UploadSession(
                "up5", "test.bin", 100L, sink, 0L, System.currentTimeMillis());
        uploadRegistry.put(session);

        manager.onUploadStarted("up5", "test.bin", 100L, 0L, null, 1, 1);
        manager.cancelUpload("up5", null);

        assertNull(uploadRegistry.get("up5"));
        assertFalse(tempFile.exists());

        assertNull(shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_PROGRESS));
        Notification result = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertTrue(result != null);
        assertEquals("Transfer cancelled", result.extras.getString(Notification.EXTRA_TITLE));
    }

    @Test
    public void notificationOperationsSafeWithoutContext() {
        FileTransferNotificationManager uninitManager = new FileTransferNotificationManager();
        uninitManager.onUploadStarted("up6", "file.txt", 100L, 0L, null, 1, 1);
        uninitManager.onUploadProgress("up6", 50L, 100L);
        uninitManager.onUploadCompleted("up6", "file.txt", null, 1, 1, null, null);
        uninitManager.onUploadFailed("up6", null, "reason");
        uninitManager.cancelUpload("up6", null);
    }

    @Test
    public void staleBatchNotificationFinalizesAfterTwoMinutes() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        org.robolectric.shadows.ShadowNotificationManager shadowNm = org.robolectric.Shadows.shadowOf(nm);
        nm.cancelAll();

        java.util.concurrent.atomic.AtomicLong fakeClock = new java.util.concurrent.atomic.AtomicLong(1_000_000L);
        manager.setClock(fakeClock::get);

        manager.onUploadStarted("up_stale_1", "file1.txt", 100L, 0L, "batch-stale-1", 1, 3);
        manager.onUploadCompleted("up_stale_1", "file1.txt", "batch-stale-1", 1, 3, null, null);

        Notification initialResult = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertNull(initialResult);

        fakeClock.addAndGet(60_000L);
        manager.checkStaleBatches(fakeClock.get());
        assertNull(shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT));

        fakeClock.addAndGet(61_000L);
        manager.checkStaleBatches(fakeClock.get());

        assertNull(shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_PROGRESS));
        Notification staleResult = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertTrue(staleResult != null);
        assertEquals("Transfer complete", staleResult.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Received 1 of 3 files", staleResult.extras.getString(Notification.EXTRA_TEXT));
        assertFalse((staleResult.flags & Notification.FLAG_ONGOING_EVENT) != 0);

        nm.cancelAll();
        fakeClock.set(2_000_000L);
        manager.onUploadStarted("up_stale_zero", "file0.txt", 100L, 0L, "batch-stale-zero", 1, 2);

        fakeClock.addAndGet(121_000L);
        manager.checkStaleBatches(fakeClock.get());

        assertNull(shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_PROGRESS));
        Notification zeroResult = shadowNm.getNotification(FileTransferNotificationManager.NOTIFICATION_ID_RECEIVE_RESULT);
        assertTrue(zeroResult != null);
        assertEquals("Transfer interrupted", zeroResult.extras.getString(Notification.EXTRA_TITLE));
        assertFalse((zeroResult.flags & Notification.FLAG_ONGOING_EVENT) != 0);
    }
}
