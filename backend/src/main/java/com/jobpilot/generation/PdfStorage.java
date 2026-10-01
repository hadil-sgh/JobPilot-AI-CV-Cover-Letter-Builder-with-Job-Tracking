package com.jobpilot.generation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.jobpilot.common.error.ApiException;

/** Stores rendered PDFs under {@code <storage-dir>/pdf/<applicationId>/} with generated names. */
@Component
public class PdfStorage {

    private final Path root;

    public PdfStorage(@Value("${jobpilot.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public String save(UUID applicationId, UUID documentId, byte[] pdf) {
        Path dir = root.resolve("pdf").resolve(applicationId.toString());
        Path file = dir.resolve(documentId + ".pdf");
        try {
            Files.createDirectories(dir);
            Files.write(file, pdf);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store PDF", e);
        }
        return root.relativize(file).toString().replace('\\', '/');
    }

    public byte[] load(String relativePath) {
        Path file = relativePath == null ? null : root.resolve(relativePath).normalize();
        if (file == null || !file.startsWith(root) || !Files.exists(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No PDF yet: render the document first");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
