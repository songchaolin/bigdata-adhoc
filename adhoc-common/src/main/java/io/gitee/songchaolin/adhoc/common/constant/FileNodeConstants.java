package io.gitee.songchaolin.adhoc.common.constant;

/**
 * 目录树常量定义
 */
public class FileNodeConstants {
    /** 节点名称非法字符 */
    public static final String INVALID_NAME_CHARS = "/\\:*?\"<>|";

    /** 节点名称最小长度 */
    public static final int NODE_NAME_MIN_LENGTH = 1;

    /** 节点名称最大长度 */
    public static final int NODE_NAME_MAX_LENGTH = 128;

    /** 描述最大长度 */
    public static final int DESCRIPTION_MAX_LENGTH = 512;

    /** 全局根节点名称（单例全局根） */
    public static final String ROOT_NODE_NAME = "root";

    /** 全局根节点 ID（固定，单例，便于迁移脚本与应用层确定性引用） */
    public static final String GLOBAL_ROOT_NODE_ID = "root_global";

    /** 全局根所属系统用户 ID */
    public static final String SYSTEM_USER_ID = "__system__";

    /** 全局根所属系统用户名 */
    public static final String SYSTEM_USER_NAME = "系统";

    /** 用户根目录名称前缀：root_ + userId（全局根下标识各用户根） */
    public static final String USER_ROOT_NAME_PREFIX = "root_";

    /** 软删除保留天数 */
    public static final int CLEANUP_RETENTION_DAYS = 7;

    private FileNodeConstants() {
    }
}
