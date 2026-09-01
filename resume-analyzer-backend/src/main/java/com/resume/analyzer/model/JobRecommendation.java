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
public class JobRecommendation {
    private String jobId;
    private String title;
    private String company;
    private String location;
    private String description;
    private String url;
    private String employmentType;
    private String postedAt;
    private double matchScore;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> jobHighlights;
}
