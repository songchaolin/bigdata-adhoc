package io.gitee.songchaolin.adhoc.executor.engine;

/**
 * 服务端日志/进度转发 sink（函数式）。
 * engine 执行期间把服务端 operation log 行转发给调用方（runner 写入 job/task LogBuffer），
 * 实现服务端日志与平台日志统一。
 */
@FunctionalInterface
public interface LogSink {
    void append(String line);
}
