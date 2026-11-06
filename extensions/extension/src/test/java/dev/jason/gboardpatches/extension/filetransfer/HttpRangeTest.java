package dev.jason.gboardpatches.extension.filetransfer;

import org.junit.Assert;
import org.junit.Test;

public final class HttpRangeTest {
    @Test
    public void nullOrEmptyHeaderReturnsNull() {
        Assert.assertNull(HttpRange.parse(null, 1000L));
        Assert.assertNull(HttpRange.parse("", 1000L));
        Assert.assertNull(HttpRange.parse("   ", 1000L));
    }

    @Test
    public void validFullRangeParsedCorrectly() {
        HttpRange range = HttpRange.parse("bytes=0-499", 1000L);
        Assert.assertNotNull(range);
        Assert.assertFalse(range.isInvalid());
        Assert.assertEquals(0L, range.start);
        Assert.assertEquals(499L, range.end);
        Assert.assertEquals(500L, range.length);
        Assert.assertEquals(1000L, range.total);
    }

    @Test
    public void validOpenEndedRangeParsedCorrectly() {
        HttpRange range = HttpRange.parse("bytes=500-", 1000L);
        Assert.assertNotNull(range);
        Assert.assertFalse(range.isInvalid());
        Assert.assertEquals(500L, range.start);
        Assert.assertEquals(999L, range.end);
        Assert.assertEquals(500L, range.length);
        Assert.assertEquals(1000L, range.total);
    }

    @Test
    public void validSuffixRangeParsedCorrectly() {
        HttpRange range = HttpRange.parse("bytes=-200", 1000L);
        Assert.assertNotNull(range);
        Assert.assertFalse(range.isInvalid());
        Assert.assertEquals(800L, range.start);
        Assert.assertEquals(999L, range.end);
        Assert.assertEquals(200L, range.length);
        Assert.assertEquals(1000L, range.total);
    }

    @Test
    public void suffixRangeLargerThanTotalClampsToStart() {
        HttpRange range = HttpRange.parse("bytes=-5000", 1000L);
        Assert.assertNotNull(range);
        Assert.assertFalse(range.isInvalid());
        Assert.assertEquals(0L, range.start);
        Assert.assertEquals(999L, range.end);
        Assert.assertEquals(1000L, range.length);
        Assert.assertEquals(1000L, range.total);
    }

    @Test
    public void invalidRangesDetected() {
        Assert.assertTrue(HttpRange.parse("bytes=1000-", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=500-400", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=-0", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=-", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=abc-def", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("notbytes=0-100", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=0-10,20-30", 1000L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=0-10", 0L).isInvalid());
        Assert.assertTrue(HttpRange.parse("bytes=-10", 0L).isInvalid());
    }
}

