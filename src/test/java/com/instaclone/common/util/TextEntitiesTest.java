package com.instaclone.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextEntitiesTest {

    @Test
    void extractsDistinctMentionsInOrder() {
        assertThat(TextEntities.extractMentions("hey @alice and @bob_1, also @alice again"))
                .containsExactly("alice", "bob_1");
    }

    @Test
    void mentionStopsAtTrailingSentenceDots() {
        assertThat(TextEntities.extractMentions("thanks @alice.")).containsExactly("alice");
        assertThat(TextEntities.extractMentions("see @a.b.c...")).containsExactly("a.b.c");
    }

    @Test
    void ignoresEmailsTooShortNamesAndDoubleAts() {
        assertThat(TextEntities.extractMentions("mail me at foo@bar.com")).isEmpty();
        assertThat(TextEntities.extractMentions("hi @ab")).isEmpty();
        assertThat(TextEntities.extractMentions("@@alice")).isEmpty();
        assertThat(TextEntities.extractMentions(null)).isEmpty();
    }

    @Test
    void capsMentionsPerText() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            text.append("@user").append(i).append(' ');
        }
        assertThat(TextEntities.extractMentions(text.toString())).hasSize(TextEntities.MAX_MENTIONS);
    }

    @Test
    void extractsLowercaseDistinctHashtags() {
        assertThat(TextEntities.extractHashtags("Loving #Sunset and #sunset #Beach_Day!"))
                .containsExactly("sunset", "beach_day");
    }

    @Test
    void skipsNumericOnlyAndEmbeddedHashes() {
        assertThat(TextEntities.extractHashtags("#1 place, item#5, &#39; #2024go"))
                .containsExactly("2024go");
        assertThat(TextEntities.extractHashtags("##double")).isEmpty();
    }

    @Test
    void supportsNonAsciiLetters() {
        assertThat(List.copyOf(TextEntities.extractHashtags("#café #日本"))).containsExactly("café", "日本");
    }
}
