package com.resume.analyzer.service;

import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.Resume;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.SkillGapAnalysis;
import com.resume.analyzer.model.SkillGapResult;
import com.resume.analyzer.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final OllamaService ollamaService;
    private final JSearchService jSearchService;
    private final MatchingService matchingService;

    private final Tika tika = new Tika();

    public Resume uploadResume(String userId, MultipartFile file) {
        String content;
        try {
            content = tika.parseToString(file.getInputStream());
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException(
                        "Failed to parse resume file: no readable text was found. Please upload a text-based PDF or DOCX file.");
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("Failed to parse resume file: " + e.getMessage(), e);
        }

        Resume resume = Resume.builder()
                .userId(userId)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .content(content)
                .build();
        return saveAndAnalyze(resume);
    }

    public Resume getResume(String userId, String resumeId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
    }

    public Resume reanalyze(String userId, String resumeId) {
        Resume resume = getResume(userId, resumeId);
        return saveAndAnalyze(resume);
    }

    public SkillGapAnalysis getSkillGapAnalysis(String userId, String resumeId) {
        Resume resume = getResume(userId, resumeId);
        return computeSkillGapAnalysis(resume);
    }

    public SkillGapResult getSkillGap(String userId, String resumeId) {
        Resume resume = getResume(userId, resumeId);
        return computeSkillGap(resume);
    }

    public List<JobRecommendation> getJobRecommendations(String userId, String resumeId) {
        Resume resume = getResume(userId, resumeId);
        return computeRecommendations(resume);
    }

    private Resume saveAndAnalyze(Resume resume) {
        ResumeAnalysis analysis = ollamaService.analyzeResume(resume.getContent());
        resume.setAnalysis(analysis);
        return resumeRepository.save(resume);
    }

    private SkillGapAnalysis computeSkillGapAnalysis(Resume resume) {
        List<JobRecommendation> jobs = computeRecommendations(resume);
        return matchingService.buildSkillGapAnalysis(resume.getAnalysis(), jobs);
    }

    private SkillGapResult computeSkillGap(Resume resume) {
        List<JobRecommendation> jobs = computeRecommendations(resume);
        return matchingService.buildSkillGap(resume.getAnalysis(), jobs);
    }

    private List<JobRecommendation> computeRecommendations(Resume resume) {
        if (resume.getAnalysis() == null) {
            throw new IllegalArgumentException(
                    "Job recommendations unavailable: resume has not been analyzed yet.");
        }
        List<JobRecommendation> jobs = jSearchService.searchAndRecommend(resume.getAnalysis());
        if (jobs == null || jobs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Job recommendations unavailable: no jobs could be generated for this resume.");
        }
        return matchingService.calculateMatches(jobs, resume.getAnalysis());
    }

    public List<Resume> getUserResumes(String userId) {
        return resumeRepository.findByUserId(userId);
    }
}
