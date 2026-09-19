package com.resume.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "interview_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String resumeId;

    private String resumeFileName;

    private String targetRole;

    private String difficulty;

    private List<InterviewQuestion> questions;

    private List<AnswerResult> answerResults;

    private boolean completed;

    private int overallScore;

    private String summaryFeedback;

    private Instant createdAt = Instant.now();
}