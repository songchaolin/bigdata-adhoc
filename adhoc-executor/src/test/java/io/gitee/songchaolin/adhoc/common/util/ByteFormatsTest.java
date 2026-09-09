package io.gitee.songchaolin.adhoc.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ByteFormatsTest {

    @Test
    void bytes() {
        assertThat(ByteFormats.formatSize(0)).isEqualTo("0B");
        assertThat(ByteFormats.formatSize(1023)).isEqualTo("1023B");
    }

    @Test
    void kb() {
        assertThat(ByteFormats.formatSize(1024)).isEqualTo("1.00KB");
        assertThat(ByteFormats.formatSize(1048575)).isEqualTo("1024.00KB");
    }

    @Test
    void mb() {
        assertThat(ByteFormats.formatSize(1048576L)).isEqualTo("1.00MB");
    }

    @Test
    void gb() {
        assertThat(ByteFormats.formatSize(1024L * 1024 * 1024)).isEqualTo("1.00GB");
    }
}