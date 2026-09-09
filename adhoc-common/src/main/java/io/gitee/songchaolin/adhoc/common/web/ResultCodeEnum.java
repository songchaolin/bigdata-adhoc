package io.gitee.songchaolin.adhoc.common.web;

/**
 * 统一响应码。code 值与历史线上格式对齐（前端/SDK 判断 code==1 为成功），不可变更。
 * <p>命名说明：Enum 后缀偏离项目"枚举无后缀"规约，系沿用被替换的公司类名，降低替换 diff 与认知成本。
 */
public enum ResultCodeEnum {

    SUCCESS(1, "操作成功"),
    FAIL(0, "失败"),
    PARAMS_IS_INVALID(300004, "参数无效"),
    SYSTEM_INNER_ERROR(600007, "系统内部错误");

    private final int code;
    private final String msg;

    ResultCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
