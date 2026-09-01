package com.resume.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resume.analyzer.model.Education;
import com.resume.analyzer.model.Experience;
import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.LearningStep;
import com.resume.analyzer.model.RecommendedRole;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.Skill;
import com.resume.analyzer.model.SkillGapAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${app.ollama.model:llama3.2}")
    private String model;

    public ResumeAnalysis analyzeResume(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("Resume analysis unavailable: resume text is empty.");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", SYSTEM_PROMPT);
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Here is the resume content:\n\n" + resumeText);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizedBaseUrl() + "/api/chat",
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException(
                        "Ollama returned HTTP " + response.getStatusCode().value() + ".");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalArgumentException("Ollama returned an empty analysis.");
            }
            return parseAnalysis(content);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Resume analysis unavailable: Ollama request failed (" + e.getMessage()
                            + "). Start Ollama and run 'ollama pull " + model + "'.", e);
        }
    }

    private String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static final String SYSTEM_PROMPT = """
            You are an expert resume analyzer and ATS specialist.
            Analyze the provided resume and return only a valid JSON object with this exact schema:
            {
              "resumeScore": {"score": 0, "feedback": "string"},
              "atsScore": {"score": 0, "issues": ["string"], "suggestions": ["string"]},
              "keywords": {"found": ["string"], "missing": ["string"]},
              "skills": [{"name": "string", "level": "beginner|intermediate|advanced|expert"}],
              "education": [{"degree": "string", "institution": "string", "field": "string", "years": "string"}],
              "experience": [{"role": "string", "company": "string", "duration": "string", "responsibilities": "string"}],
              "summary": "string"
            }
            Scores must be integers from 0 to 100. Do not add markdown or text outside the JSON object.
            """;

    private ResumeAnalysis parseAnalysis(String contentJson) throws Exception {
        JsonNode node = objectMapper.readTree(contentJson);
        JsonNode resume = node.path("resumeScore");
        JsonNode ats = node.path("atsScore");

        List<Skill> skills = new ArrayList<>();
        for (JsonNode skill : node.path("skills")) {
            skills.add(Skill.builder().name(skill.path("name").asText()).level(skill.path("level").asText()).build());
        }

        List<Education> education = new ArrayList<>();
        for (JsonNode item : node.path("education")) {
            education.add(Education.builder()
                    .degree(item.path("degree").asText())
                    .institution(item.path("institution").asText())
                    .field(item.path("field").asText())
                    .years(item.path("years").asText())
                    .build());
        }

        List<Experience> experience = new ArrayList<>();
        for (JsonNode item : node.path("experience")) {
            experience.add(Experience.builder()
                    .role(item.path("role").asText())
                    .company(item.path("company").asText())
                    .duration(item.path("duration").asText())
                    .responsibilities(item.path("responsibilities").asText())
                    .build());
        }

        List<String> found = stringsAt(node.path("keywords").path("found"));
        List<String> missing = stringsAt(node.path("keywords").path("missing"));
        List<String> issues = stringsAt(ats.path("issues"));
        List<String> suggestions = stringsAt(ats.path("suggestions"));
        int keywordScore = found.isEmpty() && missing.isEmpty()
                ? 50 : (int) Math.round(100.0 * found.size() / (found.size() + missing.size()));

        return ResumeAnalysis.builder()
                .resumeScore(clamp(resume.path("score").asInt(0)))
                .resumeFeedback(resume.path("feedback").asText(""))
                .atsScore(clamp(ats.path("score").asInt(0)))
                .atsIssues(issues)
                .atsSuggestions(suggestions)
                .foundKeywords(found)
                .missingKeywords(missing)
                .keywordScore(keywordScore)
                .skills(skills)
                .education(education)
                .experience(experience)
                .summary(node.path("summary").asText(""))
                .createdAt(Instant.now())
                .build();
    }

    private List<String> stringsAt(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> {
                if (!value.asText().isBlank()) values.add(value.asText());
            });
        }
        return values;
    }

    /**
     * Analyzes skill gaps and generates a learning path using Ollama.
     */
    public SkillGapAnalysis analyzeSkillGap(ResumeAnalysis analysis, List<JobRecommendation> matchedJobs) {
        if (analysis == null || matchedJobs == null || matchedJobs.isEmpty()) {
            throw new IllegalArgumentException("Cannot analyze skill gap: invalid input");
        }

        try {
            List<String> resumeSkills = extractResumeSkills(analysis);
            List<String> jobSkills = extractJobRequiredSkills(matchedJobs);
            
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", SKILL_GAP_SYSTEM_PROMPT);
            
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            String prompt = String.format(
                    "Resume Skills: %s\n\nRequired Job Skills: %s\n\nAnalyze the gaps and create a learning path.",
                    String.join(", ", resumeSkills),
                    String.join(", ", jobSkills)
            );
            userMessage.put("content", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizedBaseUrl() + "/api/chat",
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Ollama skill gap analysis failed");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("message").path("content").asText("");
            return parseSkillGapAnalysis(content, analysis, matchedJobs);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Skill gap analysis unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Generates recommended job roles using Ollama based on resume analysis.
     */
    public List<RecommendedRole> generateJobRoles(ResumeAnalysis analysis) {
        if (analysis == null) {
            throw new IllegalArgumentException("Cannot generate job roles: invalid resume analysis");
        }

        try {
            List<String> resumeSkills = extractResumeSkills(analysis);
            String experienceLevel = inferExperienceLevel(analysis);
            
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", JOB_ROLES_SYSTEM_PROMPT);
            
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            String prompt = String.format(
                    "Skills: %s\nExperience Level: %s\nEducation: %s\nGenerate 3-5 ideal job role recommendations.",
                    String.join(", ", resumeSkills),
                    experienceLevel,
                    formatEducation(analysis.getEducation())
            );
            userMessage.put("content", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizedBaseUrl() + "/api/chat",
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Ollama job roles generation failed");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("message").path("content").asText("");
            return parseJobRoles(content);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Job roles generation unavailable: " + e.getMessage(), e);
        }
    }

    private SkillGapAnalysis parseSkillGapAnalysis(String contentJson, ResumeAnalysis analysis, List<JobRecommendation> matchedJobs) throws Exception {
        JsonNode node = objectMapper.readTree(contentJson);
        
        List<String> presentSkills = stringsAt(node.path("presentSkills"));
        List<String> criticalGaps = stringsAt(node.path("criticalGaps"));
        int overallScore = node.path("overallSkillMatchScore").asInt(0);
        String insights = node.path("analysisInsights").asText("");

        List<LearningStep> learningPath = new ArrayList<>();
        for (JsonNode step : node.path("learningPath")) {
            learningPath.add(LearningStep.builder()
                    .skill(step.path("skill").asText())
                    .priority(step.path("priority").asText("MEDIUM"))
                    .estimatedWeeks(step.path("estimatedWeeks").asInt(4))
                    .description(step.path("description").asText(""))
                    .resources(stringsAt(step.path("resources")))
                    .build());
        }

        return SkillGapAnalysis.builder()
                .presentSkills(presentSkills)
                .criticalGaps(criticalGaps)
                .learningPath(learningPath)
                .recommendedJobs(matchedJobs)
                .overallSkillMatchScore(clamp(overallScore))
                .analysisInsights(insights)
                .build();
    }

    private List<RecommendedRole> parseJobRoles(String contentJson) throws Exception {
        JsonNode node = objectMapper.readTree(contentJson);
        List<RecommendedRole> roles = new ArrayList<>();

        // Handle both array and object responses
        JsonNode rolesNode = node.isArray() ? node : node.path("roles");
        if (!rolesNode.isArray()) {
            rolesNode = node.path("recommendedRoles");
        }

        for (JsonNode roleNode : rolesNode) {
            roles.add(RecommendedRole.builder()
                    .roleName(roleNode.path("roleName").asText())
                    .seniority(roleNode.path("seniority").asText("mid"))
                    .matchPercentage(roleNode.path("matchPercentage").asDouble(0))
                    .requiredSkills(stringsAt(roleNode.path("requiredSkills")))
                    .niceToHaveSkills(stringsAt(roleNode.path("niceToHaveSkills")))
                    .careerJustification(roleNode.path("careerJustification").asText(""))
                    .build());
        }

        return roles;
    }

    private List<String> extractResumeSkills(ResumeAnalysis analysis) {
        List<String> skills = new ArrayList<>();
        if (analysis.getSkills() != null) {
            for (Skill s : analysis.getSkills()) {
                if (s.getName() != null && !s.getName().isBlank()) {
                    skills.add(s.getName());
                }
            }
        }
        if (analysis.getFoundKeywords() != null) {
            skills.addAll(analysis.getFoundKeywords());
        }
        return skills;
    }

    private List<String> extractJobRequiredSkills(List<JobRecommendation> jobs) {
        java.util.Set<String> skills = new java.util.HashSet<>();
        for (JobRecommendation job : jobs) {
            if (job.getDescription() != null) {
                // Extract keywords from job description
                String desc = job.getDescription().toLowerCase();
                String[] keywords = {
                    "java", "python", "javascript", "typescript", "react", "angular", "vue",
                    "node", "spring", "docker", "kubernetes", "aws", "sql", "mongodb",
                    "microservices", "rest", "graphql", "ci/cd", "git", "linux"
                };
                for (String keyword : keywords) {
                    if (desc.contains(keyword)) {
                        skills.add(keyword);
                    }
                }
            }
        }
        return new ArrayList<>(skills);
    }

    private String inferExperienceLevel(ResumeAnalysis analysis) {
        if (analysis.getExperience() == null || analysis.getExperience().isEmpty()) {
            return "entry-level";
        }
        int expCount = analysis.getExperience().size();
        if (expCount <= 1) return "junior";
        if (expCount <= 3) return "mid";
        return "senior";
    }

    private String formatEducation(List<Education> education) {
        if (education == null || education.isEmpty()) {
            return "Not specified";
        }
        java.util.List<String> degrees = new ArrayList<>();
        for (Education e : education) {
            if (e.getDegree() != null && e.getInstitution() != null) {
                degrees.add(e.getDegree() + " from " + e.getInstitution());
            }
        }
        return degrees.isEmpty() ? "Not specified" : String.join(", ", degrees);
    }

    private static final String SKILL_GAP_SYSTEM_PROMPT = """
            You are an expert career advisor and skill gap analyst.
            Analyze the provided resume skills and required job skills, then return ONLY a valid JSON object with this exact schema:
            {
              "presentSkills": ["string"],
              "criticalGaps": ["string"],
              "overallSkillMatchScore": 0,
              "analysisInsights": "string",
              "learningPath": [
                {
                  "skill": "string",
                  "priority": "HIGH|MEDIUM|LOW",
                  "estimatedWeeks": 0,
                  "description": "string",
                  "resources": ["string"]
                }
              ]
            }
            - presentSkills: Skills the candidate already has from their resume
            - criticalGaps: Top 3-5 missing skills that are most important for target roles
            - overallSkillMatchScore: Overall match score 0-100
            - learningPath: Ordered list of skills to learn, starting with HIGH priority
            - Do not add markdown or text outside the JSON object.
            """;

    private static final String JOB_ROLES_SYSTEM_PROMPT = """
            You are an expert career coach who recommends ideal job roles.
            Based on the provided skills, experience level, and education, return ONLY a valid JSON array of recommended roles:
            [
              {
                "roleName": "string",
                "seniority": "junior|mid|senior|lead",
                "matchPercentage": 0,
                "requiredSkills": ["string"],
                "niceToHaveSkills": ["string"],
                "careerJustification": "string"
              }
            ]
            - roleName: Specific job title
            - seniority: Expected career level for this role
            - matchPercentage: How well this person matches the role (0-100)
            - requiredSkills: Essential skills needed (ordered by importance)
            - niceToHaveSkills: Optional skills that would be beneficial
            - careerJustification: 1-2 sentences explaining why this role fits
            - Return 3-5 diverse role recommendations
            - Do not add markdown or text outside the JSON array.
            """;

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
