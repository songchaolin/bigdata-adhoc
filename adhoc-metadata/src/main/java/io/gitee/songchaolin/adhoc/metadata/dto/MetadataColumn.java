package io.gitee.songchaolin.adhoc.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 列元数据：列名 + 类型 + 注释 + 是否分区键（Hive 分区列也是合法列，补全时与普通列一并返回）。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetadataColumn {
    private String name;
    private String type;
    private String comment;
    private boolean partition;
}
