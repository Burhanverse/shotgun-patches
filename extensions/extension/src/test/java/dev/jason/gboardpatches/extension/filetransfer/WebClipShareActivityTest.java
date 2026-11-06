package dev.jason.gboardpatches.extension.filetransfer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService;
import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncWebPortal;

@RunWith(RobolectricTestRunner.class)
public class WebClipShareActivityTest {
    private ClipboardSyncWebPortal portal;
    private Socket clientSocket;
    private Thread clientReaderThread;

    @Before
    public void setUp() {
        FileOfferRegistry.getInstance().clear();
        ClipboardSyncService.setActivePortalForTesting(null);
        WebClipShareActivity.setWorkerExecutorForTesting(Runnable::run);
        Robolectric.setupContentProvider(TestFileProvider.class, "com.external.provider");
    }

    @After
    public void tearDown() {
        WebClipShareActivity.setWorkerExecutorForTesting(null);
        ClipboardSyncService.setActivePortalForTesting(null);
        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (Throwable ignored) {
            }
        }
        if (portal != null) {
            try {
                portal.stop();
            } catch (Throwable ignored) {
            }
        }
    }

    @Test
    public void whenPortalNotRunning_finishesWithoutRegisteringOffers() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
    }

    @Test
    public void whenPortalHasNoDesktopClients_finishesWithoutRegisteringOffers() throws Exception {
        portal = new ClipboardSyncWebPortal(0, new TestClipboardBridge(), ClipboardSyncWebPortal.WebAssets.empty());
        portal.start();
        ClipboardSyncService.setActivePortalForTesting(portal);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
        Assert.assertEquals(0, portal.getFileOfferRegistry().size());
    }

    @Test
    public void whenTextOnlyShare_showsToastAndFinishes() throws Exception {
        portal = new ClipboardSyncWebPortal(0, new TestClipboardBridge(), ClipboardSyncWebPortal.WebAssets.empty());
        portal.start();
        ClipboardSyncService.setActivePortalForTesting(portal);

        connectDesktopClient(portal.getPort());
        Assert.assertTrue(portal.hasConnectedDesktopClients());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "Hello text sync only");

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
        Assert.assertEquals(0, portal.getFileOfferRegistry().size());
    }

    @Test
    public void whenFileUriProvided_rejectsNonContentUri() throws Exception {
        portal = new ClipboardSyncWebPortal(0, new TestClipboardBridge(), ClipboardSyncWebPortal.WebAssets.empty());
        portal.start();
        ClipboardSyncService.setActivePortalForTesting(portal);

        connectDesktopClient(portal.getPort());
        Assert.assertTrue(portal.hasConnectedDesktopClients());

        File testFile = new File(RuntimeEnvironment.getApplication().getCacheDir(), "file_uri.txt");
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write("Payload".getBytes(StandardCharsets.UTF_8));
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(testFile));

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
        Assert.assertEquals(0, portal.getFileOfferRegistry().size());

        testFile.delete();
    }

    @Test
    public void whenInternalPackageAuthorityProvided_rejectsToPreventConfusedDeputy() throws Exception {
        portal = new ClipboardSyncWebPortal(0, new TestClipboardBridge(), ClipboardSyncWebPortal.WebAssets.empty());
        portal.start();
        ClipboardSyncService.setActivePortalForTesting(portal);

        connectDesktopClient(portal.getPort());
        Assert.assertTrue(portal.hasConnectedDesktopClients());

        Context context = RuntimeEnvironment.getApplication();
        Uri internalUri = Uri.parse("content://" + context.getPackageName() + ".provider/private/secret.db");

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, internalUri);

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
        Assert.assertEquals(0, portal.getFileOfferRegistry().size());
    }

    @Test
    public void whenValidContentUriProvided_registersAndBroadcastsOffer() throws Exception {
        portal = new ClipboardSyncWebPortal(0, new TestClipboardBridge(), ClipboardSyncWebPortal.WebAssets.empty());
        portal.start();
        ClipboardSyncService.setActivePortalForTesting(portal);

        connectDesktopClient(portal.getPort());
        Assert.assertTrue(portal.hasConnectedDesktopClients());

        File testFile = new File(RuntimeEnvironment.getApplication().getCacheDir(), "share_sample.txt");
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write("Sample payload for webclip".getBytes(StandardCharsets.UTF_8));
        }
        TestFileProvider.setBackingFile(testFile);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://com.external.provider/files/share_sample.txt"));

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
        Assert.assertEquals(1, portal.getFileOfferRegistry().size());

        testFile.delete();
    }

    @Test
    public void whenActionSendMultiple_registersAllOffersInSingleBatch() throws Exception {
        portal = new ClipboardSyncWebPortal(0, new TestClipboardBridge(), ClipboardSyncWebPortal.WebAssets.empty());
        portal.start();
        ClipboardSyncService.setActivePortalForTesting(portal);

        connectDesktopClient(portal.getPort());
        Assert.assertTrue(portal.hasConnectedDesktopClients());

        File testFile = new File(RuntimeEnvironment.getApplication().getCacheDir(), "multi_sample.txt");
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write("Multi content".getBytes(StandardCharsets.UTF_8));
        }
        TestFileProvider.setBackingFile(testFile);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse("content://com.external.provider/1"));
        uris.add(Uri.parse("content://com.external.provider/2"));

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        WebClipShareActivity activity = Robolectric.buildActivity(WebClipShareActivity.class, intent)
                .create()
                .get();

        Assert.assertTrue(activity.isFinishing());
        Assert.assertEquals(2, portal.getFileOfferRegistry().size());

        testFile.delete();
    }

    @Test
    public void cleanOrphanedStagedFilesDeletesOnlyMatchingFiles() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File staged1 = new File(context.getCacheDir(), "webclip_staged_1.tmp");
        File staged2 = new File(context.getCacheDir(), "webclip_staged_2.tmp");
        File other = new File(context.getCacheDir(), "other_cache_file.txt");

        staged1.createNewFile();
        staged2.createNewFile();
        other.createNewFile();

        Assert.assertTrue(staged1.exists());
        Assert.assertTrue(staged2.exists());
        Assert.assertTrue(other.exists());

        WebClipShareActivity.cleanOrphanedStagedFiles(context);

        Assert.assertFalse(staged1.exists());
        Assert.assertFalse(staged2.exists());
        Assert.assertTrue(other.exists());

        other.delete();
    }

    private void connectDesktopClient(int port) throws Exception {
        clientSocket = new Socket("127.0.0.1", port);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                clientSocket.getOutputStream(), StandardCharsets.UTF_8));
        writer.write("GET /events?clientId=linux-desktop-test HTTP/1.1\r\n");
        writer.write("Host: 127.0.0.1:" + port + "\r\n");
        writer.write("\r\n");
        writer.flush();

        CountDownLatch connectedLatch = new CountDownLatch(1);
        clientReaderThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        clientSocket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        connectedLatch.countDown();
                    }
                }
            } catch (Throwable ignored) {
            }
        });
        clientReaderThread.setDaemon(true);
        clientReaderThread.start();
        Assert.assertTrue(connectedLatch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);
    }

    private static final class TestClipboardBridge implements ClipboardSyncWebPortal.ClipboardBridge {
        @Override
        public void applyDesktopClipboard(String text) {
        }

        @Override
        public void applyDesktopImage(byte[] imageBytes, String mimeType) {
        }
    }

    public static final class TestFileProvider extends ContentProvider {
        private static File backingFile;

        public static void setBackingFile(File file) {
            backingFile = file;
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            if (backingFile != null && backingFile.exists()) {
                cursor.addRow(new Object[]{backingFile.getName(), backingFile.length()});
            }
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return "text/plain";
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (backingFile != null && backingFile.exists()) {
                return ParcelFileDescriptor.open(backingFile, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            throw new FileNotFoundException("Backing file not found");
        }
    }
}
