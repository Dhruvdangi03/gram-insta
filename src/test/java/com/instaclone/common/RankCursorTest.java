package com.instaclone.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RankCursorTest {

    @Test
    void roundTripsThroughEncodeDecode() {
        RankCursor original = new RankCursor(42, 7);

        RankCursor decoded = RankCursor.decode(original.encode());

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> RankCursor.decode("not-a-real-cursor")).isInstanceOf(BadRequestException.class);
    }
}
