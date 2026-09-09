package io.gitee.songchaolin.adhoc.executor.split;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 拆分出的一段可执行 SQL：prefixSql（前序 SET/USE 累积，可空）+ sql（本段可执行语句）+ segmentIndex。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlSegment {
    private String prefixSql;
    private String sql;
    private int segmentIndex;
}
