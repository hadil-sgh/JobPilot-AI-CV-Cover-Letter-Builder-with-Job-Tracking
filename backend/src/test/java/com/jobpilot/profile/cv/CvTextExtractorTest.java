package com.jobpilot.profile.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.jobpilot.common.error.ApiException;

class CvTextExtractorTest {

    private final CvTextExtractor extractor = new CvTextExtractor();

    private static final String[] CV = {
            "Ada Lovelace - Software Engineer",
            "Experience: Analytical Engines Ltd, 2021 - Present",
            "Built a Java 17 / Spring Boot API used by 3 teams"};

    @Test
    void extractsPdfText() {
        var cv = extractor.extract(TestFiles.pdf(CV), "cv.pdf");
        assertThat(cv.extension()).isEqualTo("pdf");
        assertThat(cv.text()).contains("Analytical Engines Ltd").contains("Spring Boot");
    }

    @Test
    void extractsDocxText() {
        var cv = extractor.extract(TestFiles.docx(CV), "cv.docx");
        assertThat(cv.extension()).isEqualTo("docx");
        assertThat(cv.text()).contains("Ada Lovelace");
    }

    @Test
    void detectsTypeFromContentNotName() {
        // A real PDF uploaded with a misleading name is still read as PDF...
        assertThat(extractor.extract(TestFiles.pdf(CV), "cv.docx").extension()).isEqualTo("pdf");
        // ...and a text file renamed to .pdf is rejected.
        byte[] text = String.join("\n", CV).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> extractor.extract(text, "cv.pdf"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> extractor.extract(new byte[0], "cv.pdf"))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> extractor.extract(new byte[(int) CvTextExtractor.MAX_BYTES + 1], "cv.pdf"))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsPdfWithoutText() {
        assertThatThrownBy(() -> extractor.extract(TestFiles.pdf(), "scan.pdf"))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void normalizeStripsControlCharsAndBlankLines() {
        assertThat(CvTextExtractor.normalize("a\u0000b\r\n\r\n\r\n\r\nc  \t d"))
                .isEqualTo("ab\n\nc d");
    }
}
