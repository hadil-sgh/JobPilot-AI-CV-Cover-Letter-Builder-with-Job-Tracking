package com.jobpilot.job;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobpilot.auth.AuthUser;
import com.jobpilot.job.dto.JobDtos.AnalyzeRequest;
import com.jobpilot.job.dto.JobDtos.EvidenceReport;
import com.jobpilot.job.dto.JobDtos.JobDto;
import com.jobpilot.job.dto.JobDtos.JobSummary;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    /** Sanitises the pasted JD, analyses it with the local LLM (prompt B) and stores it. */
    @PostMapping("/analyze")
    public JobDto analyze(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AnalyzeRequest req) {
        return jobs.analyze(user.id(), req.text(), req.sourceUrl());
    }

    @GetMapping
    public List<JobSummary> recent(@AuthenticationPrincipal AuthUser user) {
        return jobs.recent(user.id());
    }

    @GetMapping("/{id}")
    public JobDto get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return jobs.get(user.id(), id);
    }

    @GetMapping("/{id}/evidence")
    public EvidenceReport evidence(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return jobs.evidence(user.id(), id);
    }
}
