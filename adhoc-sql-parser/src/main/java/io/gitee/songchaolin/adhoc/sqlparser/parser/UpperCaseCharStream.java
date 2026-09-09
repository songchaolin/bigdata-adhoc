package io.gitee.songchaolin.adhoc.sqlparser.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * 大小写不敏感 CharStream：LA() 转大写用于关键字匹配，getText() 保留原文用于标识符取值。
 * 仿 StarRocks CaseInsensitiveStream（fe/fe-core/.../sql/parser/CaseInsensitiveStream.java）。
 * Spark 与 StarRocks 两套 g4 共用。
 */
public class UpperCaseCharStream implements CharStream {
    private final CharStream source;

    public UpperCaseCharStream(CharStream source) {
        this.source = source;
    }

    @Override
    public String getText(Interval interval) {
        return source.getText(interval);
    }

    @Override
    public void consume() {
        source.consume();
    }

    @Override
    public int LA(int i) {
        int c = source.LA(i);
        if (c <= 0) return c;
        return Character.toUpperCase(c);
    }

    @Override
    public int mark() {
        return source.mark();
    }

    @Override
    public void release(int marker) {
        source.release(marker);
    }

    @Override
    public int index() {
        return source.index();
    }

    @Override
    public void seek(int index) {
        source.seek(index);
    }

    @Override
    public int size() {
        return source.size();
    }

    @Override
    public String getSourceName() {
        return source.getSourceName();
    }
}