package com.resume.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.Skill;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderAndMatchingServiceTest {

    @Test
        void ollamaFailsClearlyWhenResumeTextIsMissing() {
                OllamaService service = new OllamaService();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                                () -> service.analyzeResume(""));

                assertTrue(exception.getMessage().contains("resume text is empty"));
    }

    @Test
    void jSearchUsesFallbackJobsWhenApiKeyIsMissing() {
        JSearchService service = new JSearchService();
        ReflectionTestUtils.setField(service, "jsearchApiKey", "");

        List<JobRecommendation> jobs = service.searchAndRecommend(analysisWithSkill("Java"));

        assertTrue(jobs.size() >= 1);
        assertTrue(jobs.get(0).getJobId().startsWith("fallback-"));
        assertTrue(jobs.get(0).getDescription().toLowerCase().contains("java"));
    }

    @Test
    void jSearchReadsJobsFromSearchV2NestedData() throws Exception {
        JSearchService service = new JSearchService();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree("""
                {"status":"OK","data":{"jobs":[{"job_id":"1","job_title":"Java Developer"}]}}
                """);

        JsonNode jobs = service.extractJobsArray(root);

        assertTrue(jobs.isArray());
        assertEquals(1, jobs.size());
        assertEquals("Java Developer", jobs.get(0).path("job_title").asText());
    }

    @Test
    void matchingResultsUseTheCurrentResumeSkills() {
        MatchingService service = new MatchingService();
        JobRecommendation job = JobRecommendation.builder()
                .jobId("job-1")
                .title("Java Developer")
                .description("Requires Java and Spring Boot")
                .build();

        JobRecommendation javaMatch = service.calculateMatches(
                List.of(copy(job)), analysisWithSkill("Java" )).get(0);
        JobRecommendation pythonMatch = service.calculateMatches(
                List.of(copy(job)), analysisWithSkill("Python")).get(0);

        assertEquals(33, javaMatch.getMatchScore());
        assertEquals(0, pythonMatch.getMatchScore());
        assertTrue(javaMatch.getMatchedSkills().contains("java"));
        assertTrue(pythonMatch.getMissingSkills().contains("java"));
    }

    private ResumeAnalysis analysisWithSkill(String skill) {
        return ResumeAnalysis.builder()
                .skills(List.of(new Skill(skill, "advanced")))
                .foundKeywords(List.of(skill))
                .build();
    }

    private JobRecommendation copy(JobRecommendation job) {
        return JobRecommendation.builder()
                .jobId(job.getJobId())
                .title(job.getTitle())
                .description(job.getDescription())
                .build();
    }
}
