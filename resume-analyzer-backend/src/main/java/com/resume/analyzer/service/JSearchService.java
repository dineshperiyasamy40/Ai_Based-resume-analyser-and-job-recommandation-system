package com.resume.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.RecommendedRole;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.Skill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class JSearchService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private OllamaService ollamaService;

    @Value("${app.jsearch.api-key:}")
    private String jsearchApiKey;

    @Value("${app.jsearch.host:jsearch.p.rapidapi.com}")
    private String host;

    @Value("${app.jsearch.base-url:https://jsearch.p.rapidapi.com}")
    private String baseUrl;

    private static final List<String> GENERIC_QUERIES = List.of(
            "software developer",
            "java developer",
            "frontend developer",
            "full stack developer"
    );

    public List<JobRecommendation> searchAndRecommend(ResumeAnalysis analysis) {
        Map<String, JobRecommendation> jobsById = new java.util.LinkedHashMap<>();

        if (jsearchApiKey != null && !jsearchApiKey.isBlank()) {
            List<String> queryTerms = buildQueries(analysis);
            if (queryTerms.isEmpty()) {
                queryTerms = new ArrayList<>(GENERIC_QUERIES);
            }
            collectJobs(queryTerms, "month", jobsById);
            if (jobsById.isEmpty()) {
                System.err.println("JSearch returned no jobs for resume queries; retrying generic searches.");
                collectJobs(GENERIC_QUERIES, "all", jobsById);
            }
        } else {
            System.err.println("JSearch API key missing; using resume-based fallback jobs.");
        }

        List<JobRecommendation> results = new ArrayList<>(jobsById.values());
        if (results.isEmpty()) {
            results = buildFallbackJobs(analysis);
        }
        return results;
    }

    private void collectJobs(List<String> queryTerms, String datePosted, Map<String, JobRecommendation> jobsById) {
        for (String query : queryTerms) {
            List<JobRecommendation> results = searchJobs(query, "1", datePosted);
            for (JobRecommendation job : results) {
                if (job.getJobId() != null && !job.getJobId().isBlank()) {
                    jobsById.putIfAbsent(job.getJobId(), job);
                } else {
                    jobsById.putIfAbsent(job.getTitle() + "|" + job.getCompany(), job);
                }
            }
            if (jobsById.size() >= 30) {
                break;
            }
        }
    }

    private static final List<String> GENERIC_SKILLS = List.of(
            "communication", "teamwork", "problem solving", "git", "github",
            "agile", "scrum", "jira", "leadership", "management", "collaboration",
            "critical thinking", "time management", "english", "excel", "microsoft office"
    );

    private List<String> buildQueries(ResumeAnalysis analysis) {
        List<String> queries = new ArrayList<>();

        List<String> ollamaRoles = getOllamaRecommendedRoles(analysis);
        if (!ollamaRoles.isEmpty()) {
            return ollamaRoles;
        }

        List<String> roleKeywords = extractRoleKeywords(analysis);
        List<String> targetRoles = inferRoles(roleKeywords);
        for (String role : targetRoles) {
            if (queries.size() < 4) {
                queries.add(role);
            }
        }

        if (queries.isEmpty()) {
            if (roleKeywords.isEmpty()) {
                return new ArrayList<>(GENERIC_QUERIES.subList(0, 2));
            }
            queries.add(String.join(" ", roleKeywords) + " developer");
        }
        return queries;
    }

    private List<String> extractRoleKeywords(ResumeAnalysis analysis) {
        List<String> roleKeywords = new ArrayList<>();
        if (analysis == null) {
            return roleKeywords;
        }
        List<Skill> primarySkills = analysis.getSkills();
        if (primarySkills != null) {
            for (Skill s : primarySkills) {
                if (s.getName() == null) continue;
                String name = s.getName().trim();
                if (name.isEmpty() || GENERIC_SKILLS.contains(name.toLowerCase(Locale.ROOT))) continue;
                roleKeywords.add(name);
                if (roleKeywords.size() >= 3) break;
            }
        }

        if (roleKeywords.isEmpty() && analysis.getFoundKeywords() != null) {
            for (String kw : analysis.getFoundKeywords()) {
                if (kw == null) continue;
                String name = kw.trim();
                if (name.isEmpty() || GENERIC_SKILLS.contains(name.toLowerCase(Locale.ROOT))) continue;
                roleKeywords.add(name);
                if (roleKeywords.size() >= 3) break;
            }
        }
        return roleKeywords;
    }

    private List<String> getOllamaRecommendedRoles(ResumeAnalysis analysis) {
        if (ollamaService == null) {
            return new ArrayList<>();
        }

        try {
            List<RecommendedRole> roles = ollamaService.generateJobRoles(analysis);
            List<String> queries = new ArrayList<>();
            for (RecommendedRole role : roles) {
                if (role.getRoleName() != null && !role.getRoleName().isBlank()) {
                    queries.add(role.getRoleName());
                    if (queries.size() >= 4) break;
                }
            }
            return queries;
        } catch (Exception e) {
            System.err.println("Ollama role generation failed, falling back to hard-coded inference: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> inferRoles(List<String> skills) {
        List<String> roles = new ArrayList<>();
        Set<String> lower = new HashSet<>();
        for (String s : skills) {
            lower.add(s.toLowerCase(Locale.ROOT));
        }
        if (lower.contains("python") || lower.contains("machine learning")
                || lower.contains("tensorflow") || lower.contains("pytorch")
                || lower.contains("data")) {
            roles.add("machine learning engineer");
            roles.add("data scientist");
            return roles;
        }
        if (lower.contains("angular") || lower.contains("react")
                || lower.contains("typescript") || lower.contains("vue")
                || lower.contains("javascript") || lower.contains("html")) {
            roles.add("frontend developer");
            roles.add("full stack developer");
            return roles;
        }
        if (lower.contains("docker") || lower.contains("kubernetes")
                || lower.contains("aws") || lower.contains("azure")
                || lower.contains("terraform") || lower.contains("jenkins")) {
            roles.add("devops engineer");
            roles.add("cloud engineer");
            return roles;
        }
        if (lower.contains("java") || lower.contains("spring") || lower.contains("spring boot")
                || lower.contains("hibernate") || lower.contains("jpa")) {
            roles.add("java developer");
            roles.add("backend developer");
            return roles;
        }
        if (!skills.isEmpty()) {
            roles.add(String.join(" ", skills) + " developer");
        }
        roles.add("software developer");
        return roles;
    }

    List<JobRecommendation> buildFallbackJobs(ResumeAnalysis analysis) {
        List<String> keywords = extractRoleKeywords(analysis);
        List<String> roles = inferRoles(keywords);
        List<String> resumeSkills = new ArrayList<>();
        if (analysis != null && analysis.getSkills() != null) {
            for (Skill skill : analysis.getSkills()) {
                if (skill.getName() != null && !skill.getName().isBlank()) {
                    resumeSkills.add(skill.getName());
                }
            }
        }
        if (resumeSkills.isEmpty()) {
            resumeSkills = List.of("Java", "SQL", "Git");
        }

        String skillBlob = String.join(", ", resumeSkills);
        String extraMarketSkills = "Docker, Kubernetes, AWS, REST API, CI/CD, Agile";
        List<JobRecommendation> jobs = new ArrayList<>();
        int index = 1;
        for (String role : roles) {
            jobs.add(JobRecommendation.builder()
                    .jobId("fallback-" + index)
                    .title(capitalize(role))
                    .company("Market Match Co.")
                    .location("Remote / India")
                    .description("We are hiring a " + role + ". Required skills include "
                            + skillBlob + ", " + extraMarketSkills + ".")
                    .url("")
                    .employmentType("FULLTIME")
                    .postedAt("")
                    .build());
            index++;
            if (jobs.size() >= 5) {
                break;
            }
        }
        return jobs;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Software Developer";
        }
        String[] parts = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private List<JobRecommendation> searchJobs(String query, String numPages, String datePosted) {
        try {
            String url = baseUrl + "/search-v2?query=" + urlEncode(query)
                    + "&num_pages=" + numPages
                    + "&date_posted=" + datePosted
                    + "&country=us";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", jsearchApiKey);
            headers.set("X-RapidAPI-Host", host);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                System.err.println("JSearch HTTP " + response.getStatusCode() + " for query=" + query);
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String apiMessage = root.path("message").asText("");
            JsonNode jobsNode = extractJobsArray(root);
            if (jobsNode == null || !jobsNode.isArray() || jobsNode.isEmpty()) {
                String snippet = response.getBody();
                if (snippet.length() > 300) {
                    snippet = snippet.substring(0, 300);
                }
                System.err.println("JSearch empty jobs for query=" + query
                        + (apiMessage.isBlank() ? "" : (" message=" + apiMessage))
                        + " body=" + snippet);
                return new ArrayList<>();
            }
            List<JobRecommendation> jobs = new ArrayList<>();
            for (JsonNode job : jobsNode) {
                jobs.add(mapJob(job));
            }
            return jobs;
        } catch (Exception e) {
            System.err.println("JSearch request failed for query=" + query + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * JSearch /search-v2 returns {@code data.jobs[]}. Older /search responses used a
     * top-level {@code data[]} array. Treat both as valid so recommendations do not
     * silently come back empty.
     */
    JsonNode extractJobsArray(JsonNode root) {
        if (root == null) {
            return objectMapper.createArrayNode();
        }
        JsonNode data = root.path("data");
        if (data.isArray()) {
            return data;
        }
        JsonNode nestedJobs = data.path("jobs");
        if (nestedJobs.isArray()) {
            return nestedJobs;
        }
        JsonNode jobs = root.path("jobs");
        if (jobs.isArray()) {
            return jobs;
        }
        return objectMapper.createArrayNode();
    }

    private JobRecommendation mapJob(JsonNode job) {
        return JobRecommendation.builder()
                .jobId(job.path("job_id").asText())
                .title(job.path("job_title").asText())
                .company(job.path("employer_name").asText())
                .location(job.path("job_country").asText())
                .description(safeText(job.path("job_description").asText()))
                .url(job.path("job_apply_link").asText())
                .employmentType(job.path("job_employment_type").asText())
                .postedAt(job.path("job_posted_at_datetime_utc").asText())
                .build();
    }

    private String safeText(String text) {
        return text != null && text.length() > 3000 ? text.substring(0, 3000) : text;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

}
