package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.support.SampleFiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T-B19: el tipo sale del contenido, no del nombre ni del Content-Type. */
class TikaFileTypeDetectorTest {

    private final TikaFileTypeDetector detector = new TikaFileTypeDetector();

    @Test
    void detectsTheFourAllowedFormats() {
        assertEquals("image/jpeg", detector.detect(SampleFiles.jpeg()));
        assertEquals("image/png", detector.detect(SampleFiles.png()));
        assertEquals("image/webp", detector.detect(SampleFiles.webp()));
        assertEquals("video/mp4", detector.detect(SampleFiles.mp4()));
    }

    /** custom-mimetypes.xml: sin él, Tika los toma por QuickTime y se rechazarían MP4 válidos. */
    @Test
    void detectsMp4WithCommonIsoBrands() {
        for (String brand : new String[]{"isom", "iso2", "avc1", "mp41", "mp42"}) {
            assertEquals("video/mp4", detector.detect(SampleFiles.isoMedia(brand)), brand);
        }
    }

    @Test
    void detectsFormatsOutsideTheWhitelist() {
        assertEquals("image/gif", detector.detect(SampleFiles.gif()));
        assertEquals("text/html", detector.detect(SampleFiles.html()));
        assertEquals("video/quicktime", detector.detect(SampleFiles.isoMedia("qt  ")));
        assertEquals("image/heic", detector.detect(SampleFiles.isoMedia("heic")));
    }
}
