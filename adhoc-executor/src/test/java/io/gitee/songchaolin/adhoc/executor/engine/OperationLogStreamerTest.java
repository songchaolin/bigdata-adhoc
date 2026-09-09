package io.gitee.songchaolin.adhoc.executor.engine;

import org.apache.hive.jdbc.HiveStatement;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogStreamerTest {

    @Test
    void pollOnce_forwardsIncrementalLines() throws Exception {
        HiveStatement stmt = Mockito.mock(HiveStatement.class);
        when(stmt.getQueryLog(true, 1000)).thenReturn(Arrays.asList("line1", "line2"));
        List<String> got = new ArrayList<>();

        new OperationLogStreamer(stmt, got::add, 50L, 1000).pollOnce();

        assertThat(got).containsExactly("line1", "line2");
        verify(stmt).getQueryLog(true, 1000);
    }

    @Test
    void pollOnce_swallowsException() throws Exception {
        HiveStatement stmt = Mockito.mock(HiveStatement.class);
        when(stmt.getQueryLog(true, 1000)).thenThrow(new SQLException("opHandle not ready"));
        List<String> got = new ArrayList<>();

        new OperationLogStreamer(stmt, got::add, 50L, 1000).pollOnce(); // 不抛

        assertThat(got).isEmpty();
    }

    @Test
    void pollOnce_skipsEmptyAndBlank() throws Exception {
        HiveStatement stmt = Mockito.mock(HiveStatement.class);
        when(stmt.getQueryLog(true, 1000)).thenReturn(Arrays.asList("", "   ", "ok"));
        List<String> got = new ArrayList<>();

        new OperationLogStreamer(stmt, got::add, 50L, 1000).pollOnce();

        assertThat(got).containsExactly("ok");
    }
}
