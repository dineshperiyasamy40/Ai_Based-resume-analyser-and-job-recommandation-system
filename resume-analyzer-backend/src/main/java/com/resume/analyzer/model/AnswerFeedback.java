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
public class AnswerFeedback {

    private int score;
    private List<String> strengths;
    private List<String> improvements;
    private String suggestedAnswer;
}