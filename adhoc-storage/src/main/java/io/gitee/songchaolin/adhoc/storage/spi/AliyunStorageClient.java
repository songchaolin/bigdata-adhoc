package io.gitee.songchaolin.adhoc.storage.spi;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 阿里云 OSS 实现：endpoint + bucket + accessKey/secretKey 配置。
 * 上传同 key 覆盖写（日志快照模式，server 按 offset 增量拉取不受影响）。
 */
public class AliyunStorageClient implements StorageClient {

    private final OSS oss;
    private final String bucket;

    public AliyunStorageClient(String endpoint, String bucket, String accessKey, String secretKey) {
        this.oss = new OSSClientBuilder().build(endpoint, accessKey, secretKey);
        this.bucket = bucket;
    }

    @Override
    public String uploadResult(String fileName, byte[] data) {
        return put("result/" + fileName, data);
    }

    @Override
    public String uploadLog(String fileName, byte[] data) {
        return put("log/" + fileName, data);
    }

    private String put(String key, byte[] data) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(data.length);
        oss.putObject(bucket, key, new ByteArrayInputStream(data), meta);
        return key;
    }

    @Override
    public InputStream download(String key) {
        OSSObject obj = oss.getObject(bucket, key);
        return obj.getObjectContent();
    }

    @Override
    public boolean exist(String key) {
        return oss.doesObjectExist(bucket, key);
    }
}
