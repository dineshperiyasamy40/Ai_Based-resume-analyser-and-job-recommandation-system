package com.resume.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResult {

    private InterviewQuestion question;
    private String userAnswer;
    private AnswerFeedback feedback;
}