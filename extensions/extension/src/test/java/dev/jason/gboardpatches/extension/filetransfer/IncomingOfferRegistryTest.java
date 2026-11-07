package dev.jason.gboardpatches.extension.filetransfer;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public final class IncomingOfferRegistryTest {
    @Test
    public void registerAndStateLifecycle() {
        IncomingOfferRegistry registry = new IncomingOfferRegistry();
        registry.register("batch-1", Collections.singletonList(new IncomingOfferRegistry.FileInfo("test.txt", 100L)), 100L, false);

        Assert.assertEquals(IncomingOfferRegistry.OfferState.PENDING, registry.getState("batch-1"));
        Assert.assertFalse(registry.isAccepted("batch-1"));

        boolean accepted = registry.accept("batch-1");
        Assert.assertTrue(accepted);
        Assert.assertEquals(IncomingOfferRegistry.OfferState.ACCEPTED, registry.getState("batch-1"));
        Assert.assertTrue(registry.isAccepted("batch-1"));
    }

    @Test
    public void declineOfferTransitionsState() {
        IncomingOfferRegistry registry = new IncomingOfferRegistry();
        registry.register("batch-2", Collections.singletonList(new IncomingOfferRegistry.FileInfo("test.txt", 100L)), 100L, false);

        boolean declined = registry.decline("batch-2");
        Assert.assertTrue(declined);
        Assert.assertEquals(IncomingOfferRegistry.OfferState.DECLINED, registry.getState("batch-2"));
        Assert.assertFalse(registry.isAccepted("batch-2"));
    }

    @Test
    public void expiryAfter120Seconds() {
        IncomingOfferRegistry registry = new IncomingOfferRegistry();
        registry.register("batch-3", Collections.singletonList(new IncomingOfferRegistry.FileInfo("test.txt", 100L)), 100L, false);

        IncomingOfferRegistry.IncomingOffer offer = registry.get("batch-3");
        Assert.assertNotNull(offer);
        Assert.assertEquals(IncomingOfferRegistry.OfferState.PENDING, offer.getState(offer.createdAtMs + 60_000L));
        Assert.assertEquals(IncomingOfferRegistry.OfferState.EXPIRED, offer.getState(offer.createdAtMs + 120_001L));
    }

    @Test
    public void autoAcceptWhenConfigured() {
        IncomingOfferRegistry registry = new IncomingOfferRegistry();
        registry.register("batch-4", Collections.singletonList(new IncomingOfferRegistry.FileInfo("test.txt", 100L)), 100L, true);

        Assert.assertEquals(IncomingOfferRegistry.OfferState.ACCEPTED, registry.getState("batch-4"));
        Assert.assertTrue(registry.isAccepted("batch-4"));
    }

    @Test
    public void capacityCappedAtMaxOffers() {
        IncomingOfferRegistry registry = new IncomingOfferRegistry();
        for (int i = 0; i < 210; i++) {
            registry.register("batch-" + i, Collections.emptyList(), 0L, false);
        }
        Assert.assertTrue(registry.size() <= IncomingOfferRegistry.MAX_OFFERS);
    }
}

