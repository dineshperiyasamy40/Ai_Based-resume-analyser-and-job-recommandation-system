package com.resume.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resume.analyzer.model.AnswerFeedback;
import com.resume.analyzer.model.Education;
import com.resume.analyzer.model.Experience;
import com.resume.analyzer.model.InterviewQuestion;
import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.LearningStep;
import com.resume.analyzer.model.RecommendedRole;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.ResumeImprovement;
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

    /**
     * Generates an ATS-optimized, improved version of a resume using Ollama.
     */
    public ResumeImprovement improveResume(String resumeText, ResumeAnalysis analysis) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("Resume improvement unavailable: resume text is empty.");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", IMPROVE_RESUME_SYSTEM_PROMPT);

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            String prompt = String.format(
                    "Original resume text:\n\n%s\n\nCurrent analysis:\n" +
                    "- Missing keywords: %s\n" +
                    "- ATS issues: %s\n" +
                    "- ATS suggestions: %s\n\n" +
                    "Rewrite this resume following the instructions.",
                    resumeText,
                    String.join(", ", analysis.getMissingKeywords()),
                    String.join(", ", analysis.getAtsIssues()),
                    String.join(", ", analysis.getAtsSuggestions())
            );
            userMessage.put("content", prompt);

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
                throw new IllegalArgumentException("Ollama returned an empty improvement.");
            }
            return parseImprovement(content);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Resume improvement unavailable: " + e.getMessage(), e);
        }
    }

    private ResumeImprovement parseImprovement(String contentJson) throws Exception {
        JsonNode node = objectMapper.readTree(contentJson);
        return ResumeImprovement.builder()
                .improvedResume(node.path("improvedResume").asText(""))
                .keywordInsertions(stringsAt(node.path("keywordInsertions")))
                .actionVerbs(stringsAt(node.path("actionVerbs")))
                .atsFormattingTips(stringsAt(node.path("atsFormattingTips")))
                .rationale(node.path("rationale").asText(""))
                .build();
    }

    /**
     * Generates mock interview questions tailored to the candidate's profile using Ollama.
     */
    public List<InterviewQuestion> generateInterviewQuestions(ResumeAnalysis analysis, String targetRole,
                                                              String difficulty, int count) {
        if (analysis == null) {
            throw new IllegalArgumentException("Cannot generate interview questions: invalid resume analysis");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", INTERVIEW_QUESTIONS_SYSTEM_PROMPT);

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            String prompt = String.format(
                    "Target Role: %s\nDifficulty: %s\nNumber of Questions: %d\n\nSkills: %s\nExperience: %s\nEducation: %s",
                    targetRole,
                    difficulty,
                    count,
                    String.join(", ", extractResumeSkills(analysis)),
                    formatExperience(analysis.getExperience()),
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
                throw new IllegalArgumentException("Ollama interview questions generation failed");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("message").path("content").asText("");
            return parseInterviewQuestions(content);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Interview questions generation unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Evaluates a candidate's answer to an interview question using Ollama.
     */
    public AnswerFeedback evaluateAnswer(InterviewQuestion question, String userAnswer) {
        if (question == null || userAnswer == null || userAnswer.isBlank()) {
            throw new IllegalArgumentException("Cannot evaluate answer: question or answer is empty");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", ANSWER_FEEDBACK_SYSTEM_PROMPT);

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            String prompt = String.format(
                    "Question: %s\nCategory: %s\nDifficulty: %s\nModel answer (for reference): %s\n\nCandidate's answer:\n%s",
                    question.getQuestion(),
                    question.getCategory(),
                    question.getDifficulty(),
                    question.getModelAnswer(),
                    userAnswer
            );
            userMessage.put("content", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizedBaseUrl() + "/api/chat",
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Ollama answer evaluation failed");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("message").path("content").asText("");
            return parseAnswerFeedback(content);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Answer evaluation unavailable: " + e.getMessage(), e);
        }
    }

    private List<InterviewQuestion> parseInterviewQuestions(String contentJson) throws Exception {
        JsonNode node = objectMapper.readTree(contentJson);
        JsonNode questionsNode = node.isArray() ? node : node.path("questions");
        List<InterviewQuestion> questions = new ArrayList<>();
        for (JsonNode q : questionsNode) {
            questions.add(InterviewQuestion.builder()
                    .question(q.path("question").asText())
                    .category(q.path("category").asText("behavioral"))
                    .difficulty(q.path("difficulty").asText("medium"))
                    .tips(q.path("tips").asText(""))
                    .modelAnswer(q.path("modelAnswer").asText(""))
                    .build());
        }
        return questions;
    }

    private AnswerFeedback parseAnswerFeedback(String contentJson) throws Exception {
        JsonNode node = objectMapper.readTree(contentJson);
        return AnswerFeedback.builder()
                .score(clamp(node.path("score").asInt(0)))
                .strengths(stringsAt(node.path("strengths")))
                .improvements(stringsAt(node.path("improvements")))
                .suggestedAnswer(node.path("suggestedAnswer").asText(""))
                .build();
    }

    private String formatExperience(List<Experience> experience) {
        if (experience == null || experience.isEmpty()) {
            return "Not specified";
        }
        List<String> roles = new ArrayList<>();
        for (Experience e : experience) {
            if (e.getRole() != null && e.getCompany() != null) {
                roles.add(e.getRole() + " at " + e.getCompany() + " (" + e.getDuration() + ")");
            }
        }
        return roles.isEmpty() ? "Not specified" : String.join(", ", roles);
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

    private static final String IMPROVE_RESUME_SYSTEM_PROMPT = """
            You are an expert ATS optimization specialist and professional resume writer.
            Rewrite the provided resume to maximize its Applicant Tracking System (ATS) compatibility and impact.
            Return ONLY a valid JSON object with this exact schema:
            {
              "improvedResume": "string",
              "keywordInsertions": ["string"],
              "actionVerbs": ["string"],
              "atsFormattingTips": ["string"],
              "rationale": "string"
            }
            - improvedResume: The complete rewritten resume as plain text. Use standard headings (Summary, Skills, Experience, Education). Use strong action verbs, quantify achievements with numbers and %, weave in the missing keywords naturally, and keep it clean and parseable by ATS (no tables, columns, or graphics).
            - keywordInsertions: Up to 8 entries describing which missing keyword to add and where, e.g. "Add 'AWS' to the Skills section and to the first experience bullet".
            - actionVerbs: 5-10 recommended action verbs used in the rewrite.
            - atsFormattingTips: 3-6 general ATS-safe formatting recommendations.
            - rationale: 2-3 sentences summarizing the most important changes made and why.
            Preserve the person's real information - never invent employers, roles, degrees, or credentials that were not in the original resume. Do not add markdown or text outside the JSON object.
            """;

    private static final String INTERVIEW_QUESTIONS_SYSTEM_PROMPT = """
            You are an expert technical interviewer and career coach.
            Generate realistic mock interview questions tailored to the candidate's target role, skills, and background.
            Return ONLY a valid JSON object with this exact schema:
            {
              "questions": [
                {
                  "question": "string",
                  "category": "technical|behavioral|situational",
                  "difficulty": "easy|medium|hard",
                  "tips": "string",
                  "modelAnswer": "string"
                }
              ]
            }
            - question: A realistic interview question a hiring manager would ask for the target role.
            - category: Mix of technical (skills-based), behavioral (past experience), and situational (hypothetical) questions.
            - difficulty: Must match the requested difficulty level for the bulk, with a reasonable spread.
            - tips: What the interviewer is looking for in a strong answer (2-3 sentences).
            - modelAnswer: A strong sample answer tailored to the candidate's skills (2-4 sentences, can reference their resume).
            - Generate exactly the requested number of questions. Do not add markdown or text outside the JSON object.
            """;

    private static final String ANSWER_FEEDBACK_SYSTEM_PROMPT = """
            You are an expert interview coach evaluating a candidate's answer.
            Return ONLY a valid JSON object with this exact schema:
            {
              "score": 0,
              "strengths": ["string"],
              "improvements": ["string"],
              "suggestedAnswer": "string"
            }
            - score: Overall answer quality from 0 to 100.
            - strengths: 2-4 specific things the candidate did well.
            - improvements: 2-4 specific, actionable ways to improve the answer.
            - suggestedAnswer: A concise model answer (2-4 sentences) the candidate should study.
            - Be constructive, encouraging, and specific. Do not add markdown or text outside the JSON object.
            """;

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
