package com.example.lowcode.asset.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MinioStorageGatewayTest {
    private static final long MAX_BYTES = 10L * 1024 * 1024 + 1;

    @Test
    void readsAtMostBoundedLimitFromAnOversizedObject() throws IOException {
        TrackingInputStream input = new TrackingInputStream(MAX_BYTES + 1);

        byte[] bytes = MinioStorageGateway.readBounded(input, MAX_BYTES);

        assertThat(bytes).hasSize((int) MAX_BYTES);
        assertThat(input.bytesRead()).isEqualTo(MAX_BYTES);
        assertThat(input.maxRequestedLength()).isLessThanOrEqualTo(8192);
    }

    private static final class TrackingInputStream extends InputStream {
        private final long totalBytes;
        private long bytesRead;
        private int maxRequestedLength;

        private TrackingInputStream(long totalBytes) {
            this.totalBytes = totalBytes;
        }

        @Override
        public int read() {
            if (bytesRead >= totalBytes) {
                return -1;
            }
            bytesRead++;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            maxRequestedLength = Math.max(maxRequestedLength, length);
            if (bytesRead >= totalBytes) {
                return -1;
            }
            int returned = (int) Math.min(length, totalBytes - bytesRead);
            bytesRead += returned;
            return returned;
        }

        private long bytesRead() {
            return bytesRead;
        }

        private int maxRequestedLength() {
            return maxRequestedLength;
        }
    }
}
