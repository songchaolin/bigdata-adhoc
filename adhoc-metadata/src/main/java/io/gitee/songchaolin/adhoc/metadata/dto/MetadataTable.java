package io.gitee.songchaolin.adhoc.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 表元数据：表名 + 类型（TABLE/VIEW 等）+ 注释。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetadataTable {
    private String name;
    private String type;
    private String comment;
}
