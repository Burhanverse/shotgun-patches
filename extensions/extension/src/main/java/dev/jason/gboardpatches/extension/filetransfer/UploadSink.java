package dev.jason.gboardpatches.extension.filetransfer;

import android.net.Uri;

import java.io.Closeable;
import java.io.IOException;

public interface UploadSink extends Closeable {
    void writeAt(long offset, byte[] data, int length) throws IOException;

    void fsync() throws IOException;

    void publish() throws IOException;

    void abort();

    @Override
    void close() throws IOException;

    long getPosition();

    default Uri getUri() {
        return null;
    }
}

