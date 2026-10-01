package com.jobpilot.profile.cv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps the original uploaded CV under {@code <storage-dir>/cv/<userId>/}. File names are
 * generated (never taken from the upload), so a crafted name cannot escape the directory.
 */
@Component
public class CvFileStorage {

    private final Path root;

    public CvFileStorage(@Value("${jobpilot.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    /** Stores the file and returns its path relative to the storage root. */
    public String save(UUID userId, byte[] bytes, String extension) {
        Path dir = root.resolve("cv").resolve(userId.toString());
        Path file = dir.resolve(UUID.randomUUID() + "." + extension);
        try {
            Files.createDirectories(dir);
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store CV file", e);
        }
        return root.relativize(file).toString().replace('\\', '/');
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null) {
            return;
        }
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort: an orphaned old CV file is harmless.
        }
    }
}
