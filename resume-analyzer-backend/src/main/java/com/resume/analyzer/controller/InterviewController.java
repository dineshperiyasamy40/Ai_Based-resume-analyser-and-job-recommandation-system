package com.resume.analyzer.controller;

import com.resume.analyzer.dto.AnswerRequest;
import com.resume.analyzer.dto.InterviewSetupRequest;
import com.resume.analyzer.model.AnswerFeedback;
import com.resume.analyzer.model.InterviewSession;
import com.resume.analyzer.model.User;
import com.resume.analyzer.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@AuthenticationPrincipal User user,
                                      @RequestBody InterviewSetupRequest request) {
        try {
            InterviewSession session = interviewService.createSession(
                    user.getId(),
                    request.getResumeId(),
                    request.getTargetRole(),
                    request.getDifficulty(),
                    request.getQuestionCount());
            return ResponseEntity.status(HttpStatus.CREATED).body(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<?> answer(@AuthenticationPrincipal User user,
                                    @PathVariable String sessionId,
                                    @RequestBody AnswerRequest request) {
        try {
            AnswerFeedback feedback = interviewService.submitAnswer(user.getId(), sessionId, request);
            return ResponseEntity.ok(feedback);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(@AuthenticationPrincipal User user) {
        List<InterviewSession> sessions = interviewService.listSessions(user.getId());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> get(@AuthenticationPrincipal User user, @PathVariable String sessionId) {
        try {
            return ResponseEntity.ok(interviewService.getSession(user.getId(), sessionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}