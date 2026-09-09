package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_server_instance")
public class AdhocServerInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String instanceId;
    private String host;
    private Integer httpPort;
    private Integer grpcPort;
    private String status;
    private Integer accepting;
    private Date startTime;
    private String version;
    private Date heartbeatTime;
    private Integer activeJobs;
    private Double cpuUsagePct;
    private Double systemCpuUsagePct;
    private Double memoryUsagePct;
    private Long memoryUsedMb;
    private Long memoryMaxMb;
    private Long heapCommittedMb;
    private Long nonHeapUsedMb;
    private Long nonHeapCommittedMb;
    private Integer threadCount;
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
