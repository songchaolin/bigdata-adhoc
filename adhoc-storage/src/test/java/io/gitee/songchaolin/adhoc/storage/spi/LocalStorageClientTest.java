package io.gitee.songchaolin.adhoc.storage.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LocalStorageClient 单测：临时目录读写回环 + 同 key 覆盖 + 路径穿越拒绝。 */
class LocalStorageClientTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadDownloadExistRoundtrip() throws Exception {
        LocalStorageClient client = new LocalStorageClient(tempDir.toString());
        byte[] data = "hello adhoc".getBytes("UTF-8");

        String key = client.uploadResult("r1.bin", data);
        assertTrue(key.startsWith("result/"));

        assertTrue(client.exist(key));
        try (InputStream in = client.download(key)) {
            byte[] buf = new byte[1024];
            int n = in.read(buf);
            assertEquals("hello adhoc", new String(buf, 0, n, "UTF-8"));
        }

        String logKey = client.uploadLog("job1/job.log", data);
        assertTrue(logKey.startsWith("log/"));
        assertTrue(client.exist(logKey));
        try (InputStream in = client.download(logKey)) {
            assertEquals("hello adhoc", new String(readAll(in), "UTF-8"));
        }
    }

    @Test
    void uploadOverwritesSameKey() throws Exception {
        LocalStorageClient client = new LocalStorageClient(tempDir.toString());
        String key = client.uploadLog("job.log", "v1".getBytes("UTF-8"));
        client.uploadLog("job.log", "v2".getBytes("UTF-8"));
        try (InputStream in = client.download(key)) {
            assertEquals("v2", new String(readAll(in), "UTF-8"));
        }
    }

    @Test
    void existReturnsFalseForMissing() {
        LocalStorageClient client = new LocalStorageClient(tempDir.toString());
        assertFalse(client.exist("result/nope.bin"));
    }

    @Test
    void rejectsPathTraversalKey() {
        LocalStorageClient client = new LocalStorageClient(tempDir.toString());
        assertThrows(IllegalArgumentException.class, () -> client.download("../outside.bin"));
        assertThrows(IllegalArgumentException.class, () -> client.exist("log/../../etc/passwd"));
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
