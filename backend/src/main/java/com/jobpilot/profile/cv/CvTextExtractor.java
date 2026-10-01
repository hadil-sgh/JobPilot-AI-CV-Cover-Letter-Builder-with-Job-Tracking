package com.jobpilot.profile.cv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.common.text.TextClean;

/**
 * Extracts plain text from an uploaded CV. The file type is detected from the content (magic
 * bytes via tika-core), not trusted from the name or Content-Type. Only PDF and DOCX are accepted.
 */
@Component
public class CvTextExtractor {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_CHARS = 30_000;
    static final int MAX_PDF_PAGES = 10;

    static final String PDF = "application/pdf";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final Tika tika = new Tika();

    public record ExtractedCv(String text, String extension) {
    }

    public ExtractedCv extract(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 5 MB");
        }
        String type = detect(bytes, filename);
        String text;
        String extension;
        try {
            if (PDF.equals(type)) {
                text = pdfText(bytes);
                extension = "pdf";
            } else if (DOCX.equals(type)) {
                text = docxText(bytes);
                extension = "docx";
            } else {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF and DOCX files are supported");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The file could not be read. Is it a valid PDF or DOCX?");
        }
        String cleaned = normalize(text);
        if (cleaned.length() < 30) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No text found in this file (is it a scanned image?). Fill in your profile manually instead.");
        }
        return new ExtractedCv(cleaned.length() > MAX_CHARS ? cleaned.substring(0, MAX_CHARS) : cleaned, extension);
    }

    private String detect(byte[] bytes, String filename) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            // Name is only a hint (needed to tell DOCX apart from other zip files); magic bytes decide.
            return tika.detect(in, filename == null ? "" : filename);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The file could not be read");
        }
    }

    private static String pdfText(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Password-protected PDFs are not supported");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(doc.getNumberOfPages(), MAX_PDF_PAGES));
            return stripper.getText(doc);
        }
    }

    private static String docxText(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    static String normalize(String text) {
        return TextClean.normalizeBlock(text);
    }
}
