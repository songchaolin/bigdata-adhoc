package io.gitee.songchaolin.adhoc.executor.engine;

import io.gitee.songchaolin.adhoc.storage.model.ResultRow;
import io.gitee.songchaolin.adhoc.storage.model.ResultSchema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 一次查询的结果：hasResultSet（DQL=true / DDL·DML=false）+ schema + rows + updateCount。
 * updateCount：DDL/DML 影响行数（st.getUpdateCount()，-1 表示无更新计数如 CREATE）；DQL 恒 -1（无意义）。
 */
@Data
@AllArgsConstructor
public class QueryResult {
    private boolean hasResultSet;
    private ResultSchema schema;
    private List<ResultRow> rows;
    private long updateCount;
}