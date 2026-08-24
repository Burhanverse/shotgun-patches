package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Test;

public final class ScreenshotCaptureObserverTest {

    @Test
    public void testIsScreenshotFileMatchesStandardPatterns() {
        Assert.assertTrue(ScreenshotCaptureObserver.isScreenshotFile("Screenshot_20260825-010528.png", "/storage/emulated/0/Pictures/Screenshots"));
        Assert.assertTrue(ScreenshotCaptureObserver.isScreenshotFile("screenshot_123.jpg", "/storage/emulated/0/DCIM/Screenshots"));
        Assert.assertTrue(ScreenshotCaptureObserver.isScreenshotFile("scr_2026.png", "/storage/emulated/0/Pictures"));
        Assert.assertTrue(ScreenshotCaptureObserver.isScreenshotFile("image.png", "Pictures/Screenshots/"));
        Assert.assertTrue(ScreenshotCaptureObserver.isScreenshotFile("SCREENSHOT_456.PNG", ""));
    }

    @Test
    public void testIsScreenshotFileRejectsNonScreenshots() {
        Assert.assertFalse(ScreenshotCaptureObserver.isScreenshotFile("IMG_20260825.jpg", "/storage/emulated/0/DCIM/Camera"));
        Assert.assertFalse(ScreenshotCaptureObserver.isScreenshotFile("photo.png", "/storage/emulated/0/Download"));
        Assert.assertFalse(ScreenshotCaptureObserver.isScreenshotFile("Screenshot_WebClip_1724500000000.png", "/storage/emulated/0/Pictures/Screenshots"));
        Assert.assertFalse(ScreenshotCaptureObserver.isScreenshotFile("Screenshot_2026.png.tmp", "/storage/emulated/0/Pictures/Screenshots"));
        Assert.assertFalse(ScreenshotCaptureObserver.isScreenshotFile(".pending-123-Screenshot.png", "/storage/emulated/0/Pictures/Screenshots"));
        Assert.assertFalse(ScreenshotCaptureObserver.isScreenshotFile(null, null));
    }
}
