package com.jobpilot.application;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobpilot.application.ApplicationService.ApplicationDto;
import com.jobpilot.application.ApplicationService.CreateRequest;
import com.jobpilot.auth.AuthUser;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> create(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.create(user.id(), req));
    }

    @GetMapping
    public List<ApplicationDto> recent(@AuthenticationPrincipal AuthUser user) {
        return applications.recent(user.id());
    }

    @GetMapping("/{id}")
    public ApplicationDto get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return applications.get(user.id(), id);
    }
}
