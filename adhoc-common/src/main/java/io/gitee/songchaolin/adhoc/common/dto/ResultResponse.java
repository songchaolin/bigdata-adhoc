package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /api/task/result 响应。rows 为 JSON 编码的行。分页字段 current/size 与请求一致。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultResponse {
    private List<ColumnDto> schema;
    private List<String> rows;
    private Long current;
    private Long size;
    private long totalRows;
    private boolean hasMore;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDto {
        private int colIndex;
        private String colName;
        private String colType;
    }
}
