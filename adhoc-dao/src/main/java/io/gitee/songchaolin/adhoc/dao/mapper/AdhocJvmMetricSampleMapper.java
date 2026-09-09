package io.gitee.songchaolin.adhoc.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocJvmMetricSample;

/**
 * 实例 JVM/OS 指标采样历史 Mapper。insert/delete/selectList 全走 {@link BaseMapper} + Lambda（对齐 CLAUDE.md §3.4 禁字符串列名）。
 * <p>写入：{@link io.gitee.songchaolin.adhoc.server.ha.ServerHeartbeatTask} / {@link io.gitee.songchaolin.adhoc.executor.ha.ExecutorHeartbeatTask}
 * 心跳每 5s insert 一条（{@link AdhocJvmMetricSample#of} 装配）。
 * <p>清理：{@link io.gitee.songchaolin.adhoc.server.ha.JvmMetricSampleCleanupTask} LambdaUpdateWrapper.lt(sampleTime, before) delete。
 * <p>查询：{@link io.gitee.songchaolin.adhoc.server.service.MetricsService#getJvmSeries} LambdaQueryWrapper.in(instanceId).between(sampleTime) selectList。
 */
public interface AdhocJvmMetricSampleMapper extends BaseMapper<AdhocJvmMetricSample> {
}
