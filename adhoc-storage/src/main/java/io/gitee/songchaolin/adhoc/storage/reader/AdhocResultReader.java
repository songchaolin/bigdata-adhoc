package io.gitee.songchaolin.adhoc.storage.reader;

import io.gitee.songchaolin.adhoc.storage.format.AdhocResultFormat;
import io.gitee.songchaolin.adhoc.storage.format.AdhocResultSerializer;
import io.gitee.songchaolin.adhoc.storage.model.ResultRow;
import io.gitee.songchaolin.adhoc.storage.model.ResultSchema;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 本地结果文件 reader：校验 MAGIC + 读 schema 行 + skip/逐行读。
 * skip(n) 只读 4B 长度 + skip 行体（不反序列化），O(skip) 廉价分页（参考 Linkis StorageResultSetReader.skip）。
 */
public class AdhocResultReader implements Closeable {

    private final BufferedInputStream in;
    private ResultSchema schema;

    public AdhocResultReader(File file) throws IOException {
        this(new FileInputStream(file));
    }

    /** 从 InputStream 读（HDFS 读用，server 直读 HDFS 兜底）。 */
    public AdhocResultReader(InputStream inputStream) throws IOException {
        this.in = inputStream instanceof BufferedInputStream
                ? (BufferedInputStream) inputStream
                : new BufferedInputStream(inputStream);
        readAndCheckMagic();
        int schemaLen = AdhocResultFormat.readInt(in);
        byte[] schemaBytes = readFully(in, schemaLen);
        this.schema = AdhocResultSerializer.schemaFromJson(schemaBytes);
    }

    public ResultSchema getSchema() {
        return schema;
    }

    /** 跳过 n 行（只读长度 + skip 行体，不反序列化）。 */
    public void skip(long n) throws IOException {
        for (long i = 0; i < n; i++) {
            int len = AdhocResultFormat.readInt(in);
            skipFully(in, len);
        }
    }

    /** 是否还有下一行。 */
    public boolean hasNext() throws IOException {
        in.mark(INT_LEN_PLACEHOLDER);
        int b = in.read();
        if (b < 0) {
            return false;
        }
        in.reset();
        return true;
    }

    /** 读下一行（反序列化）。 */
    public ResultRow next() throws IOException {
        int len = AdhocResultFormat.readInt(in);
        byte[] rowBytes = readFully(in, len);
        return AdhocResultSerializer.rowFromJson(rowBytes);
    }

    private void readAndCheckMagic() throws IOException {
        byte[] magic = new byte[AdhocResultFormat.MAGIC.length];
        int read = readFully(in, magic);
        if (read != magic.length) {
            throw new IOException("Invalid result file: too short for MAGIC");
        }
        for (int i = 0; i < magic.length; i++) {
            if (magic[i] != AdhocResultFormat.MAGIC[i]) {
                throw new IOException("Invalid result file: MAGIC mismatch");
            }
        }
    }

    private static int readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        int n = b.length;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) {
                return off;
            }
            off += r;
        }
        return off;
    }

    /** 读 len 字节，返回新分配的 byte[]；不足抛 EOFException。 */
    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] b = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(b, off, len - off);
            if (r < 0) {
                throw new EOFException("Unexpected EOF, expected " + len + " bytes, got " + off);
            }
            off += r;
        }
        return b;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long s = in.skip(remaining);
            if (s <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("Unexpected EOF while skipping");
                }
                remaining--;
            } else {
                remaining -= s;
            }
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private static final int INT_LEN_PLACEHOLDER = 1;
}
