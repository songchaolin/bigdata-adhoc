package io.gitee.songchaolin.adhoc.common.enums;

/**
 * 文件树节点类型。
 * <ul>
 *   <li>DIRECTORY：普通目录</li>
 *   <li>FILE：SQL 脚本文件</li>
 *   <li>USER_ROOT：用户根目录（全局根下，每用户一个，user 隔离边界，不可删/改/移）</li>
 * </ul>
 */
public enum NodeType {
    DIRECTORY,
    FILE,
    USER_ROOT;

    public boolean is(String value) {
        return name().equals(value);
    }

    /**
     * 是否为容器（可包含子节点）：DIRECTORY 或 USER_ROOT。
     */
    public static boolean isContainer(String value) {
        return DIRECTORY.is(value) || USER_ROOT.is(value);
    }

    public static NodeType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (NodeType e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
