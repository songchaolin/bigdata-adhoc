package io.gitee.songchaolin.adhoc.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 结果集 schema（列列表）。序列化为 JSON 数组存文件头。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultSchema {
    private List<ResultColumn> columns;
}
