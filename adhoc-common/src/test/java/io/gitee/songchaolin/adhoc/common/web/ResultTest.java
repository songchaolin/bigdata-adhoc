package io.gitee.songchaolin.adhoc.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Result 线上格式回归测试：code/msg/data 三字段 + 关键 code 值（前端/SDK 依赖）。 */
class ResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successHasCode1() {
        Result<String> r = Result.success("data");
        assertEquals(1, r.getCode());
        assertEquals("data", r.getData());
        assertNotNull(r.getMsg());
    }

    @Test
    void errorKeepsCustomCodeAndMsg() {
        Result<Void> r = Result.error(0, "boom");
        assertEquals(0, r.getCode());
        assertEquals("boom", r.getMsg());
    }

    @Test
    void resultCodeEnumValuesMatchLegacyWireFormat() {
        assertEquals(1, ResultCodeEnum.SUCCESS.getCode());
        assertEquals("操作成功", ResultCodeEnum.SUCCESS.getMsg());
        assertEquals(0, ResultCodeEnum.FAIL.getCode());
        assertEquals("失败", ResultCodeEnum.FAIL.getMsg());
        assertEquals(300004, ResultCodeEnum.PARAMS_IS_INVALID.getCode());
        assertEquals("参数无效", ResultCodeEnum.PARAMS_IS_INVALID.getMsg());
        assertEquals(600007, ResultCodeEnum.SYSTEM_INNER_ERROR.getCode());
        assertEquals("系统内部错误", ResultCodeEnum.SYSTEM_INNER_ERROR.getMsg());
    }

    @Test
    void jsonShapeIsCodeMsgData() throws Exception {
        String json = mapper.writeValueAsString(Result.success("x"));
        assertTrue(json.contains("\"code\":1"));
        assertTrue(json.contains("\"data\":\"x\""));
        assertTrue(json.contains("\"msg\""));
        // 精确锁定字段集：多出的第四个字段会被发现
        assertEquals(3, mapper.readTree(json).size());
    }
}
