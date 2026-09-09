package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_executor_instance")
public class AdhocExecutorInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String instanceId;
    private String host;
    private Integer grpcPort;
    private String status;
    private Integer accepting;
    private Date startTime;
    private String version;
    private Date heartbeatTime;
    private Date lastDownTime;
    private Integer maxConcurrentTasks;
    private String engineTypes;
    private Double cpuUsage;
    private Double memUsage;
    private Double diskUsage;
    private String runningTasks;
    private Double cpuUsagePct;
    private Double systemCpuUsagePct;
    private Double memoryUsagePct;
    private Long memoryUsedMb;
    private Long memoryMaxMb;
    private Integer threadCount;
    private Long lastGcPauseMs;
    private Long heapCommittedMb;
    private Long nonHeapUsedMb;
    private Long nonHeapCommittedMb;
    private Integer daemonThreadCount;
    private Long gcCount;
    private Long gcTimeMs;
    private Double gcTimeRatioPct;
    private Integer loadedClassCount;
    private Long uptimeMs;
    private Double loadScore;
    private Date createTime;
    private Date updateTime;
}
