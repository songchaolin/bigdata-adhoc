package io.gitee.songchaolin.adhoc.common.spi;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;

/** SQL 解析引擎 SPI。KyuubiSparkParserEngine（Spark g4）/ StarRocksParserEngine（StarRocks g4，P7）。 */
public interface SqlParserEngine {
    SqlParseResult parse(String sql);
}
