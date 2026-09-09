package io.gitee.songchaolin.adhoc.common.exception;

/**
 * 即席查询平台统一错误码：枚举名（name）作为机器可读标识透传给前端/SDK，
 * defaultMessage 作为面向用户的友好中文提示（经 {@link AdhocException} -> 全局异常处理器拼成 "CODE: msg" 返回）。
 * 抛业务异常时优先用 1-参构造（走 default）；需附带上下文细节时用 2-参构造传入更具体的中文消息。
 */
public enum AdhocErrorCode {
    ADHOC_JOB_TOO_MANY_TASKS("SQL 语句段数超过单次提交上限"),
    ADHOC_JOB_LIMIT_EXCEEDED("Job 提交数量超过限制，请稍后重试"),
    ADHOC_JOB_NO_EXECUTABLE_SQL("未检测到可执行的 SQL 语句"),
    ADHOC_ENGINE_TYPE_REQUIRED("引擎类型不能为空"),
    ADHOC_ENGINE_TYPE_INVALID("引擎类型非法"),
    ADHOC_ENGINE_PARAMS_INVALID("引擎参数非法"),
    ADHOC_ENGINE_INSTANCE_NOT_FOUND("引擎实例不存在或未配置，请检查实例名"),
    ADHOC_SQL_SYNTAX_ERROR("SQL 语法错误"),
    ADHOC_RESULT_NO_RESULT("未查询到结果数据"),
    ADHOC_RESULT_INCOMPLETE("结果尚未完整生成，请稍后重试"),
    ADHOC_RESULT_LOST("结果数据已丢失"),
    ADHOC_LOG_INCOMPLETE("日志尚未完整生成，请稍后重试"),
    ADHOC_EXECUTOR_READ_BUSY("执行器读取繁忙，请稍后重试"),
    ADHOC_EXECUTOR_CRASHED("执行器已宕机，任务执行中断"),
    ADHOC_QUERY_TIMEOUT("查询执行超时"),
    ADHOC_SESSION_LOST("引擎会话已失效"),
    ADHOC_SERVER_CRASHED("调度服务异常，请稍后重试"),
    ADHOC_QUEUE_WAIT_TIMEOUT("排队等待超时，请稍后重试"),
    ADHOC_SCHEDULE_NO_EXEC_AVAILABLE("暂无可用执行器，请稍后重试"),
    ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED("当前引擎不支持该 SQL 语句类型"),
    ADHOC_SQL_DANGEROUS_STATEMENT("检测到危险 SQL 语句，已被拦截"),
    ADHOC_JOB_NOT_FOUND("Job 不存在或已失效"),
    ADHOC_JOB_FORBIDDEN("无权访问该 Job"),

    // 目录树模块
    ADHOC_NODE_NOT_FOUND("节点不存在"),
    ADHOC_NODE_NAME_DUPLICATE("节点名称已存在"),
    ADHOC_NODE_NAME_INVALID("节点名称含有非法字符"),
    ADHOC_DIRECTORY_NOT_EMPTY("目录非空，无法删除"),
    ADHOC_ROOT_NODE_IMMUTABLE("根节点不可修改"),
    ADHOC_PARENT_NOT_DIRECTORY("父节点不是目录类型"),
    ADHOC_MOVE_TO_SELF_OR_CHILD("不能将节点移动到自身或其子节点下"),
    ADHOC_USER_CONTEXT_MISSING("缺少用户身份信息，请通过网关访问"),

    // 元数据自动补全
    ADHOC_METADATA_QUERY_FAILED("元数据查询失败"),
    ADHOC_METADATA_NOT_CONFIGURED("元数据服务未配置"),

    // SQL 控制台（运维大盘只读查询）
    ADHOC_SQL_QUERY_ONLY_SELECT("仅支持 SELECT/SHOW/DESCRIBE 查询语句"),
    ADHOC_SQL_QUERY_FAILED("查询执行失败");

    private final String defaultMessage;

    AdhocErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
