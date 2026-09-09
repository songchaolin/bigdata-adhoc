package io.gitee.songchaolin.adhoc.sqlparser.parser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import com.starrocks.sql.parser.StarRocksLexer;
import com.starrocks.sql.parser.StarRocksParser;

/** StarRocks SQL 解析器工厂（StarRocks 3.5.8 g4 + UpperCaseCharStream + BailErrorStrategy）。 */
public class StarRocksSqlParserFactory {

    private StarRocksSqlParserFactory() {
    }

    public static StarRocksParser createParser(String sql) {
        StarRocksLexer lexer = new StarRocksLexer(new UpperCaseCharStream(CharStreams.fromString(sql)));
        StarRocksParser parser = new StarRocksParser(new CommonTokenStream(lexer));
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }
}
