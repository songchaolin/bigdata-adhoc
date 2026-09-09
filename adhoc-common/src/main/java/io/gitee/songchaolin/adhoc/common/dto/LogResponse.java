package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /api/{task|job}/log 响应。lines 为日志行。
 *
 * <p><b>hasMore</b>：纯分页信号——当前数据源里 offset 之后是否还有未读行（size-based），与日志是否结束无关。
 *
 * <p><b>complete</b>：日志是否已完整（终态）。含 server 终态轮追加的 COMPLETE 标识时为 true。
 * <b>客户端轮询契约</b>：hasMore=true 继续翻页；hasMore=false 且 complete=false 仍须继续轮询
 * （日志尚未 finalize，后续还会增长）；hasMore=false 且 complete=true 停止轮询。
 * 对始终 complete=false 的异常（collector 未 finalize）由客户端超时兜底，避免无限轮询。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogResponse {
    private List<String> lines;
    private long offset;
    private int limit;
    private boolean hasMore;
    private boolean complete;
}