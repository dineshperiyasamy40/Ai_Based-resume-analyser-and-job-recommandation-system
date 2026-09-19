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
public class ResumeImprovement {

    private String improvedResume;
    private List<String> keywordInsertions;
    private List<String> actionVerbs;
    private List<String> atsFormattingTips;
    private String rationale;
}