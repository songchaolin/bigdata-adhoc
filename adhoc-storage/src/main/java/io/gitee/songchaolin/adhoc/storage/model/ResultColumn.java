package io.gitee.songchaolin.adhoc.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 结果集一列。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultColumn {
    private int colIndex;
    private String colName;
    private String colType;
}
