package com.resume.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGapAnalysis {
    private List<String> presentSkills;
    private List<String> criticalGaps; // Most important skills to learn
    private List<LearningStep> learningPath; // Ordered steps to close gaps
    private List<JobRecommendation> recommendedJobs;
    private int overallSkillMatchScore; // 0-100
    private String analysisInsights; // AI-generated summary
}
