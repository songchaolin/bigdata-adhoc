package io.gitee.songchaolin.adhoc.sqlparser.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * ANTLR 错误监听器：捕获第一个语法错误的详情（行/列/offending token/msg）。
 * BailErrorStrategy 抛异常前会通知 listener，所以能捕获到错误详情。
 * 用于构造带 visual marker 的错误消息。
 */
public class CollectingErrorListener extends BaseErrorListener {

    private boolean hasError = false;
    private int line = -1;
    private int col = -1;
    private String offendingToken;
    private String msg;

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg, RecognitionException e) {
        if (!hasError) { // 只捕获第一个错误
            this.hasError = true;
            this.line = line;
            this.col = charPositionInLine;
            this.offendingToken = (offendingSymbol instanceof Token)
                    ? ((Token) offendingSymbol).getText() : "?";
            this.msg = msg;
        }
    }

    public boolean hasError() { return hasError; }
    public int getLine() { return line; }
    public int getCol() { return col; }
    public String getOffendingToken() { return offendingToken; }
    public String getMsg() { return msg; }
}
