package com.resume.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysis {

    private int resumeScore;
    private String resumeFeedback;
    private int atsScore;
    private List<String> atsIssues;
    private List<String> atsSuggestions;
    private List<String> foundKeywords;
    private List<String> missingKeywords;
    private int keywordScore;
    private List<Skill> skills;
    private List<Education> education;
    private List<Experience> experience;
    private String summary;
    private Instant createdAt;
}
