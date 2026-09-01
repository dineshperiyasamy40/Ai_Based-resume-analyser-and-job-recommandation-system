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
public class LearningStep {
    private String skill;
    private String priority; // HIGH, MEDIUM, LOW
    private int estimatedWeeks;
    private String description; // Why this skill is needed
    private List<String> resources; // Learning resources (optional)
}
