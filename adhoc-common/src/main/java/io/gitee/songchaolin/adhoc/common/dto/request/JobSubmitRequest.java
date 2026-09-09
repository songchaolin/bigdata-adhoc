package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** POST /api/job 请求体。 */
@Data
public class JobSubmitRequest {
    @NotBlank(message = "sqlContent 不能为空")
    private String sqlContent;
    @NotBlank(message = "engineType 不能为空")
    private String engineType;          // KYUUBI / STARROCKS（必填）
    private String engineInstance;      // 可选，引擎实例名（如 kyuubi-02），空则用默认实例
    private String clientRequestId;     // 可选，幂等键
    private String fileId;              // 可选，来源文件节点

    // SDK 端设置，server 端从 Header/Metadata 提取后覆盖
    private String userId;              // 用户 ID（SDK 使用）
    private String userName;            // 用户名（SDK 使用）
}
