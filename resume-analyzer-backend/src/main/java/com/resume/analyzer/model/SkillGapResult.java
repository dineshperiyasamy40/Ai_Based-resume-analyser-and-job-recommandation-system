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
public class SkillGapResult {
    private List<String> presentSkills;
    private List<String> missingSkills;
    private List<JobRecommendation> recommendedJobs;
    private int overallSkillMatchScore;
}
