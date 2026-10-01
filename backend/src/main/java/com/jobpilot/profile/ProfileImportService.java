package com.jobpilot.profile;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.jobpilot.profile.cv.CvDraft;
import com.jobpilot.profile.cv.CvFileStorage;
import com.jobpilot.profile.cv.CvStructurer;
import com.jobpilot.profile.cv.CvTextExtractor;
import com.jobpilot.profile.cv.CvTextExtractor.ExtractedCv;
import com.jobpilot.profile.dto.ProfileDtos.ProfileDto;

/**
 * CV upload → text → LLM JSON → profile. Deliberately not @Transactional: the slow LLM call runs
 * outside any DB transaction; only the final write is transactional (ProfileService).
 */
@Service
public class ProfileImportService {

    private final CvTextExtractor extractor;
    private final CvStructurer structurer;
    private final CvFileStorage storage;
    private final ProfileService profiles;

    public ProfileImportService(CvTextExtractor extractor, CvStructurer structurer, CvFileStorage storage,
                                ProfileService profiles) {
        this.extractor = extractor;
        this.structurer = structurer;
        this.storage = storage;
        this.profiles = profiles;
    }

    public ProfileDto importCv(UUID userId, byte[] bytes, String filename) {
        ExtractedCv cv = extractor.extract(bytes, filename);
        CvDraft draft = structurer.structure(cv.text());

        String previous = profiles.originalFilePath(userId);
        String stored = storage.save(userId, bytes, cv.extension());
        try {
            ProfileDto result = profiles.replaceWithDraft(userId, draft, stored);
            storage.deleteQuietly(previous);
            return result;
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored);
            throw e;
        }
    }
}
