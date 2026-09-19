package com.resume.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSetupRequest {

    private String resumeId;
    private String targetRole;
    private String difficulty;
    private int questionCount;
}