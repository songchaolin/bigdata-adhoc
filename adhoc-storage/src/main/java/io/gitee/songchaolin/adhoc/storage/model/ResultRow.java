package io.gitee.songchaolin.adhoc.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 结果集一行（值数组）。每行序列化为 JSON 数组存文件。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultRow {
    private Object[] values;
}
