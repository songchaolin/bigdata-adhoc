package io.gitee.songchaolin.adhoc.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdhocResultSummaryMapper extends BaseMapper<AdhocResultSummary> {

    /** 本地 TTL 2h：LOCAL_PERSISTENT + local_expire_time 过期 -> PERSISTENT_ONLY。 */
    int updateExpiredLocalToPersistentOnly();

    /** HDFS TTL 30d：查超过 N 天的 result_summary（待删 HDFS 文件 + 行）。 */
    List<AdhocResultSummary> selectOldResultSummaries(@Param("days") int days);
}