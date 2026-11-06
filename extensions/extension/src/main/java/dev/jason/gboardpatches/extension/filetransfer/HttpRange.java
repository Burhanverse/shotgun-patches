package dev.jason.gboardpatches.extension.filetransfer;

import java.util.Locale;

public final class HttpRange {
    public static final HttpRange INVALID = new HttpRange(-1L, -1L, -1L);

    public final long start;
    public final long end;
    public final long length;
    public final long total;

    public HttpRange(long start, long end, long total) {
        this.start = start;
        this.end = end;
        this.length = (start >= 0 && end >= start) ? (end - start + 1L) : 0L;
        this.total = total;
    }

    public static HttpRange parse(String headerValue, long totalLength) {
        if (headerValue == null || headerValue.isBlank() || totalLength < 0L) {
            return null;
        }
        String value = headerValue.trim();
        if (!value.toLowerCase(Locale.US).startsWith("bytes=")) {
            return INVALID;
        }
        String rangeSpec = value.substring(6).trim();
        if (rangeSpec.contains(",")) {
            return INVALID;
        }
        int dashIndex = rangeSpec.indexOf('-');
        if (dashIndex < 0) {
            return INVALID;
        }
        String first = rangeSpec.substring(0, dashIndex).trim();
        String second = rangeSpec.substring(dashIndex + 1).trim();
        try {
            if (first.isEmpty()) {
                if (second.isEmpty() || totalLength == 0L) {
                    return INVALID;
                }
                long suffixLength = Long.parseLong(second);
                if (suffixLength <= 0L) {
                    return INVALID;
                }
                long actualSuffix = Math.min(suffixLength, totalLength);
                long start = totalLength - actualSuffix;
                return new HttpRange(start, totalLength - 1L, totalLength);
            }
            long start = Long.parseLong(first);
            if (start < 0L || (totalLength == 0L && start > 0L) || (totalLength > 0L && start >= totalLength)) {
                return INVALID;
            }
            if (totalLength == 0L) {
                return INVALID;
            }
            long end;
            if (second.isEmpty()) {
                end = totalLength - 1L;
            } else {
                end = Long.parseLong(second);
                if (end < start || end >= totalLength) {
                    return INVALID;
                }
            }
            return new HttpRange(start, end, totalLength);
        } catch (NumberFormatException ignored) {
            return INVALID;
        }
    }

    public boolean isInvalid() {
        return this == INVALID || start < 0L || end < start;
    }
}

