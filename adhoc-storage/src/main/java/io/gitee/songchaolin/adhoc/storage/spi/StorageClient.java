package io.gitee.songchaolin.adhoc.storage.spi;

import java.io.InputStream;

/**
 * 结果/日志持久化存储 SPI。内置 local（本地文件系统）/ aliyun（阿里云 OSS）实现；
 * 第三方可自行实现并注册为 Spring Bean（adhoc.storage.type 未匹配内置类型时按 Bean 类型装配）。
 */
public interface StorageClient {

    /** 上传结果文件，返回可回读的 key */
    String uploadResult(String fileName, byte[] data);

    /** 上传日志文件（同 fileName 覆盖写快照），返回可回读的 key */
    String uploadLog(String fileName, byte[] data);

    /** 下载（返回 InputStream，调用方负责 close） */
    InputStream download(String key);

    /** 文件是否存在 */
    boolean exist(String key);
}
