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
public class RecommendedRole {
    private String roleName;
    private String seniority; // junior, mid, senior, lead
    private double matchPercentage; // 0-100
    private List<String> requiredSkills; // Ordered by priority
    private List<String> niceToHaveSkills;
    private String careerJustification; // Why this role fits
}
