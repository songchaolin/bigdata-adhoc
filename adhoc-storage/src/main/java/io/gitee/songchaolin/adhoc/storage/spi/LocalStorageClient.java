package io.gitee.songchaolin.adhoc.storage.spi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 本地文件系统实现（开箱即用，适合试用/单机）：key 即 base-dir 下相对路径
 * （result/... 与 log/... 前缀），上传同 key 覆盖写。
 */
public class LocalStorageClient implements StorageClient {

    private final File baseDir;

    public LocalStorageClient(String baseDir) {
        this.baseDir = new File(baseDir);
    }

    @Override
    public String uploadResult(String fileName, byte[] data) {
        return write("result/" + fileName, data);
    }

    @Override
    public String uploadLog(String fileName, byte[] data) {
        return write("log/" + fileName, data);
    }

    private String write(String key, byte[] data) {
        File f = resolve(key);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        } catch (IOException e) {
            throw new IllegalStateException("local storage write failed: " + key, e);
        }
        return key;
    }

    @Override
    public InputStream download(String key) {
        try {
            return new FileInputStream(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("local storage read failed: " + key, e);
        }
    }

    @Override
    public boolean exist(String key) {
        return resolve(key).isFile();
    }

    private File resolve(String key) {
        // 拒绝路径穿越（key 含 .. 时抛出）
        File f = new File(baseDir, key);
        if (!f.toPath().normalize().startsWith(baseDir.toPath().normalize())) {
            throw new IllegalArgumentException("illegal storage key: " + key);
        }
        return f;
    }
}
