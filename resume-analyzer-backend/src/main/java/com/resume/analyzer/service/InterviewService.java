package com.resume.analyzer.service;

import com.resume.analyzer.dto.AnswerRequest;
import com.resume.analyzer.model.AnswerFeedback;
import com.resume.analyzer.model.AnswerResult;
import com.resume.analyzer.model.InterviewQuestion;
import com.resume.analyzer.model.InterviewSession;
import com.resume.analyzer.model.Resume;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.repository.InterviewRepository;
import com.resume.analyzer.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ResumeRepository resumeRepository;
    private final OllamaService ollamaService;

    public InterviewSession createSession(String userId, String resumeId, String targetRole,
                                          String difficulty, int questionCount) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        ResumeAnalysis analysis = resume.getAnalysis();
        if (analysis == null) {
            throw new IllegalArgumentException(
                    "Interview generation unavailable: resume has not been analyzed yet.");
        }
        if (questionCount <= 0) {
            questionCount = 5;
        }

        String resolvedRole = (targetRole == null || targetRole.isBlank()) ? "General" : targetRole;
        String resolvedDifficulty = (difficulty == null || difficulty.isBlank()) ? "medium" : difficulty;

        List<InterviewQuestion> questions = ollamaService.generateInterviewQuestions(
                analysis, resolvedRole, resolvedDifficulty, questionCount);

        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .resumeId(resumeId)
                .resumeFileName(resume.getFileName())
                .targetRole(resolvedRole)
                .difficulty(resolvedDifficulty)
                .questions(questions)
                .answerResults(new ArrayList<>())
                .completed(false)
                .overallScore(0)
                .createdAt(Instant.now())
                .build();
        return interviewRepository.save(session);
    }

    public AnswerFeedback submitAnswer(String userId, String sessionId, AnswerRequest request) {
        InterviewSession session = getSession(userId, sessionId);
        if (session.isCompleted()) {
            throw new IllegalArgumentException("This interview session is already completed.");
        }
        List<InterviewQuestion> questions = session.getQuestions();
        if (questions == null || request.getQuestionIndex() < 0 || request.getQuestionIndex() >= questions.size()) {
            throw new IllegalArgumentException("Invalid question index.");
        }
        if (request.getAnswer() == null || request.getAnswer().isBlank()) {
            throw new IllegalArgumentException("Answer cannot be empty.");
        }

        InterviewQuestion question = questions.get(request.getQuestionIndex());
        AnswerFeedback feedback = ollamaService.evaluateAnswer(question, request.getAnswer());

        if (session.getAnswerResults() == null) {
            session.setAnswerResults(new ArrayList<>());
        }
        session.getAnswerResults().add(AnswerResult.builder()
                .question(question)
                .userAnswer(request.getAnswer())
                .feedback(feedback)
                .build());

        if (session.getAnswerResults().size() >= questions.size()) {
            session.setCompleted(true);
            int total = 0;
            for (AnswerResult result : session.getAnswerResults()) {
                total += result.getFeedback().getScore();
            }
            int overall = questions.isEmpty() ? 0 : (int) Math.round((double) total / questions.size());
            session.setOverallScore(overall);
            session.setSummaryFeedback(buildSummary(session));
        }

        interviewRepository.save(session);
        return feedback;
    }

    public List<InterviewSession> listSessions(String userId) {
        return interviewRepository.findByUserId(userId);
    }

    public InterviewSession getSession(String userId, String sessionId) {
        return interviewRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Interview session not found"));
    }

    private String buildSummary(InterviewSession session) {
        int answered = session.getAnswerResults() == null ? 0 : session.getAnswerResults().size();
        int total = session.getQuestions() == null ? 0 : session.getQuestions().size();
        return String.format(
                "You answered %d of %d questions with an average score of %d%%. " +
                "Review each question's model answer in the summary below to prepare for your real interview.",
                answered, total, session.getOverallScore());
    }
}