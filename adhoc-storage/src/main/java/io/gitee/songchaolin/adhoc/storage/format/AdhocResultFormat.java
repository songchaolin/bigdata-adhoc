package io.gitee.songchaolin.adhoc.storage.format;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 结果文件长度前缀格式（借鉴 Linkis Dolphin，自研）。
 * 文件布局：MAGIC(4B) + schema行(INT_LEN + schemaJsonBytes) + data行0(INT_LEN + rowJsonBytes) + ...
 * 长度前缀用 4 字节大端 int，支持 skip（只读长度、seek 跳过行体）实现廉价分页。
 */
public final class AdhocResultFormat {

    /** 4 字节 magic：ASCII "ADH0"（0x41 0x44 0x48 0x30）。 */
    public static final byte[] MAGIC = {0x41, 0x44, 0x48, 0x30};

    public static final int INT_LEN = 4;

    private AdhocResultFormat() {
    }

    /** 写 4 字节大端 int。 */
    public static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /** 读 4 字节大端 int；EOF 抛 EOFException。 */
    public static int readInt(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if (b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0) {
            throw new EOFException("Unexpected EOF reading int");
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }
}
