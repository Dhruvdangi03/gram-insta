package com.instaclone.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.media.dto.VideoProbe;
import org.junit.jupiter.api.Test;

class MediaProcessingServiceTest {

    private final MediaProcessingService service = new MediaProcessingService(null, null);

    @Test
    void parsesWidthHeightAndDurationFromFfprobeJson() {
        String json =
                """
                {
                  "streams": [ { "width": 1280, "height": 720 } ],
                  "format": { "duration": "12.469000" }
                }
                """;

        VideoProbe probe = service.parseProbe(json);

        assertThat(probe.width()).isEqualTo(1280);
        assertThat(probe.height()).isEqualTo(720);
        assertThat(probe.durationSec()).isEqualTo(12);
    }

    @Test
    void roundsDurationToNearestSecond() {
        String json =
                """
                {
                  "streams": [ { "width": 640, "height": 360 } ],
                  "format": { "duration": "3.6" }
                }
                """;

        VideoProbe probe = service.parseProbe(json);

        assertThat(probe.durationSec()).isEqualTo(4);
    }
}
