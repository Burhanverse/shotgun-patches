package dev.jason.gboardpatches.extension.filetransfer;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class FileOfferRegistryTest {
    @Test
    public void tokenMatchesHex16ByteSpecification() {
        String token = FileOfferRegistry.generateToken();
        Assert.assertNotNull(token);
        Assert.assertEquals(32, token.length());
        Assert.assertTrue(token.matches("^[0-9a-f]{32}$"));
    }

    @Test
    public void registerAndRetrieveOffer() {
        FileOfferRegistry registry = new FileOfferRegistry();
        String token = FileOfferRegistry.generateToken();
        FileOffer offer = FileOffer.forTestingStream(
                token,
                new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)),
                "test.txt",
                4L,
                "text/plain",
                "batch-1",
                0,
                1,
                "");
        registry.register(offer);
        Assert.assertEquals(1, registry.size());
        FileOffer retrieved = registry.get(token);
        Assert.assertNotNull(retrieved);
        Assert.assertEquals(token, retrieved.getToken());
        Assert.assertEquals("test.txt", retrieved.getName());
        Assert.assertEquals(4L, retrieved.getSize());
    }

    @Test
    public void batchCapEnforcedAt200Offers() {
        FileOfferRegistry registry = new FileOfferRegistry();
        String batchId = "large-batch";
        for (int i = 0; i < FileOfferRegistry.MAX_OFFERS_PER_BATCH; i++) {
            String token = FileOfferRegistry.generateToken();
            FileOffer offer = FileOffer.forTestingStream(
                    token,
                    new ByteArrayInputStream(new byte[0]),
                    "file_" + i + ".bin",
                    0L,
                    "application/octet-stream",
                    batchId,
                    i,
                    FileOfferRegistry.MAX_OFFERS_PER_BATCH,
                    "");
            registry.register(offer);
        }
        Assert.assertEquals(200, registry.size());

        String overflowToken = FileOfferRegistry.generateToken();
        FileOffer overflowOffer = FileOffer.forTestingStream(
                overflowToken,
                new ByteArrayInputStream(new byte[0]),
                "overflow.bin",
                0L,
                "application/octet-stream",
                batchId,
                200,
                201,
                "");
        try {
            registry.register(overflowOffer);
            Assert.fail("Expected IllegalStateException on 201st batch offer");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(overflowOffer.isClosed());
        }
    }

    @Test
    public void cancelBatchClosesAllOffers() {
        FileOfferRegistry registry = new FileOfferRegistry();
        String batchId = "batch-cancel";
        FileOffer offer1 = FileOffer.forTestingStream(
                "token-1",
                new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)),
                "f1.txt",
                1L,
                "text/plain",
                batchId,
                0,
                2,
                "");
        FileOffer offer2 = FileOffer.forTestingStream(
                "token-2",
                new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8)),
                "f2.txt",
                1L,
                "text/plain",
                batchId,
                1,
                2,
                "");
        registry.register(offer1);
        registry.register(offer2);
        Assert.assertEquals(2, registry.size());

        registry.cancelBatch(batchId);
        Assert.assertEquals(0, registry.size());
        Assert.assertTrue(offer1.isClosed());
        Assert.assertTrue(offer2.isClosed());
    }

    @Test
    public void completeOfferMarksCompletedAndCloses() {
        FileOfferRegistry registry = new FileOfferRegistry();
        FileOffer offer = FileOffer.forTestingStream(
                "token-comp",
                new ByteArrayInputStream("done".getBytes(StandardCharsets.UTF_8)),
                "done.txt",
                4L,
                "text/plain",
                "batch-comp",
                0,
                1,
                "");
        registry.register(offer);
        registry.completeOffer("token-comp");
        Assert.assertNull(registry.get("token-comp"));
        Assert.assertTrue(offer.isCompleted());
        Assert.assertTrue(offer.isClosed());
    }

    @Test
    public void idleTimeoutExpiresUnusedBatch() throws InterruptedException {
        FileOfferRegistry registry = new FileOfferRegistry(1000L);
        FileOffer offer = FileOffer.forTestingStream(
                "token-idle",
                new ByteArrayInputStream("idle".getBytes(StandardCharsets.UTF_8)),
                "idle.txt",
                4L,
                "text/plain",
                "batch-idle",
                0,
                1,
                "");
        registry.register(offer);
        long now = System.currentTimeMillis();
        registry.cleanExpiredOffers(now + 500L);
        Assert.assertNotNull(registry.get("token-idle"));

        registry.cleanExpiredOffers(now + 1500L);
        Assert.assertNull(registry.get("token-idle"));
        Assert.assertTrue(offer.isClosed());
    }

    @Test
    public void batchActivityRefreshesExpiryForOtherOffers() {
        FileOfferRegistry registry = new FileOfferRegistry(1000L);
        String batchId = "batch-activity";
        FileOffer offer1 = FileOffer.forTestingStream(
                "token-act-1",
                new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)),
                "f1.txt",
                1L,
                "text/plain",
                batchId,
                0,
                2,
                "");
        FileOffer offer2 = FileOffer.forTestingStream(
                "token-act-2",
                new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8)),
                "f2.txt",
                1L,
                "text/plain",
                batchId,
                1,
                2,
                "");
        registry.register(offer1);
        registry.register(offer2);

        long baseTime = System.currentTimeMillis();
        offer1.recordBytesServed(1L, baseTime + 600L);
        registry.touch("token-act-1", baseTime + 600L);

        registry.cleanExpiredOffers(baseTime + 1200L);
        Assert.assertNotNull(registry.get("token-act-2"));
        Assert.assertFalse(offer2.isClosed());

        registry.cleanExpiredOffers(baseTime + 2000L);
        Assert.assertNull(registry.get("token-act-2"));
        Assert.assertTrue(offer2.isClosed());
    }
}
