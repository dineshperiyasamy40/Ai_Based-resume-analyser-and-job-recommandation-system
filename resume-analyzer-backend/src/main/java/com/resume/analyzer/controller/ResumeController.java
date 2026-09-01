package com.resume.analyzer.controller;

import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.Resume;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.SkillGapAnalysis;
import com.resume.analyzer.model.SkillGapResult;
import com.resume.analyzer.model.User;
import com.resume.analyzer.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@AuthenticationPrincipal User user,
                                    @RequestParam("file") MultipartFile file) {
        try {
            Resume resume = resumeService.uploadResume(user.getId(), file);
            return ResponseEntity.status(HttpStatus.CREATED).body(resume);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.getUserResumes(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            return ResponseEntity.ok(resumeService.getResume(user.getId(), id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<?> getAnalysis(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            Resume resume = resumeService.getResume(user.getId(), id);
            ResumeAnalysis analysis = resume.getAnalysis();
            if (analysis == null) {
                resume = resumeService.reanalyze(user.getId(), id);
                analysis = resume.getAnalysis();
            }
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<?> reanalyze(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            Resume resume = resumeService.reanalyze(user.getId(), id);
            return ResponseEntity.ok(resume.getAnalysis());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/skill-gap")
    public ResponseEntity<?> getSkillGap(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            SkillGapAnalysis result = resumeService.getSkillGapAnalysis(user.getId(), id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<?> getJobs(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            List<JobRecommendation> jobs = resumeService.getJobRecommendations(user.getId(), id);
            return ResponseEntity.ok(jobs);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
