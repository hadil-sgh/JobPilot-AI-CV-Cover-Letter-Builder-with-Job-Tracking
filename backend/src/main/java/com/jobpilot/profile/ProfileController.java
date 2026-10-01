package com.jobpilot.profile;

import java.io.IOException;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jobpilot.auth.AuthUser;
import com.jobpilot.profile.dto.ProfileDtos.ItemDto;
import com.jobpilot.profile.dto.ProfileDtos.ItemRequest;
import com.jobpilot.profile.dto.ProfileDtos.ProfileDto;
import com.jobpilot.profile.dto.ProfileDtos.ProfileUpdateRequest;
import com.jobpilot.profile.dto.ProfileDtos.ReorderRequest;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;
    private final ProfileImportService importer;

    public ProfileController(ProfileService profiles, ProfileImportService importer) {
        this.profiles = profiles;
        this.importer = importer;
    }

    @GetMapping
    public ProfileDto get(@AuthenticationPrincipal AuthUser user) {
        return profiles.get(user.id());
    }

    @PutMapping
    public ProfileDto update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ProfileUpdateRequest req) {
        return profiles.update(user.id(), req);
    }

    /** Uploads a CV (PDF/DOCX, max 5 MB) and replaces the profile with its parsed content. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileDto importCv(@AuthenticationPrincipal AuthUser user, @RequestParam("file") MultipartFile file)
            throws IOException {
        return importer.importCv(user.id(), file.getBytes(), file.getOriginalFilename());
    }

    @PostMapping("/items")
    public ResponseEntity<ItemDto> addItem(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profiles.addItem(user.id(), req));
    }

    @PutMapping("/items/{id}")
    public ItemDto updateItem(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                              @Valid @RequestBody ItemRequest req) {
        return profiles.updateItem(user.id(), id, req);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        profiles.deleteItem(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/items/order")
    public ProfileDto reorder(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ReorderRequest req) {
        return profiles.reorder(user.id(), req);
    }
}
