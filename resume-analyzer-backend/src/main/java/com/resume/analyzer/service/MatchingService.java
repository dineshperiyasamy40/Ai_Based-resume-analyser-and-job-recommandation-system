package com.resume.analyzer.service;

import com.resume.analyzer.model.JobRecommendation;
import com.resume.analyzer.model.LearningStep;
import com.resume.analyzer.model.ResumeAnalysis;
import com.resume.analyzer.model.Skill;
import com.resume.analyzer.model.SkillGapAnalysis;
import com.resume.analyzer.model.SkillGapResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    @Autowired(required = false)
    private OllamaService ollamaService;

    public List<JobRecommendation> calculateMatches(List<JobRecommendation> jobs, ResumeAnalysis analysis) {
        Set<String> resumeSkills = extractSkillSet(analysis);
        for (JobRecommendation job : jobs) {
            List<String> jobSkills = extractJobSkills(job);
            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (String js : jobSkills) {
                if (skillMatches(resumeSkills, js)) {
                    matched.add(js);
                } else {
                    missing.add(js);
                }
            }
            job.setMatchedSkills(matched);
            job.setMissingSkills(missing);
            int matchScore;
            if (jobSkills.isEmpty()) {
                matchScore = 0;
            } else {
                matchScore = (int) Math.round(100.0 * matched.size() / jobSkills.size());
            }
            job.setMatchScore(matchScore);
            job.setJobHighlights(buildHighlights(job));
        }
        return jobs.stream()
                .sorted((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()))
                .collect(Collectors.toList());
    }

    public SkillGapResult buildSkillGap(ResumeAnalysis analysis, List<JobRecommendation> matchedJobs) {
        Set<String> resumeSkills = extractSkillSet(analysis);
        Set<String> requiredSkills = new HashSet<>();
        for (JobRecommendation job : matchedJobs) {
            requiredSkills.addAll(extractJobSkills(job));
        }

        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : requiredSkills) {
            if (skillMatches(resumeSkills, skill)) {
                present.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int overallScore = 0;
        if (!matchedJobs.isEmpty()) {
            int avgJobMatch = (int) Math.round(matchedJobs.stream()
                    .mapToDouble(JobRecommendation::getMatchScore)
                    .average().orElse(0));
            double coverage = 0.0;
            if (!requiredSkills.isEmpty()) {
                coverage = 100.0 * present.size() / requiredSkills.size();
            }
            overallScore = (int) Math.round((avgJobMatch * 0.6) + (coverage * 0.4));
        }

        return SkillGapResult.builder()
                .presentSkills(present)
                .missingSkills(missing)
                .recommendedJobs(matchedJobs)
                .overallSkillMatchScore(overallScore)
                .build();
    }

    /**
     * Build skill gap analysis using Ollama with intelligent learning paths.
     * Falls back to buildSkillGap() if Ollama is unavailable.
     */
    public SkillGapAnalysis buildSkillGapAnalysis(ResumeAnalysis analysis, List<JobRecommendation> matchedJobs) {
        // Try Ollama first
        if (ollamaService != null) {
            try {
                return ollamaService.analyzeSkillGap(analysis, matchedJobs);
            } catch (Exception e) {
                // Log error and fall back to basic analysis
                System.err.println("Ollama skill gap analysis failed, falling back to basic matching: " + e.getMessage());
                return fallbackSkillGapAnalysis(analysis, matchedJobs);
            }
        }
        // If Ollama not available, use fallback
        return fallbackSkillGapAnalysis(analysis, matchedJobs);
    }

    /**
     * Fallback skill gap analysis using hard-coded skill matching
     */
    private SkillGapAnalysis fallbackSkillGapAnalysis(ResumeAnalysis analysis, List<JobRecommendation> matchedJobs) {
        SkillGapResult result = buildSkillGap(analysis, matchedJobs);
        
        // Create a simple learning path from missing skills
        List<LearningStep> learningPath = new ArrayList<>();
        for (String skill : result.getMissingSkills()) {
            learningPath.add(LearningStep.builder()
                    .skill(skill)
                    .priority(determinePriority(skill, matchedJobs))
                    .estimatedWeeks(estimateWeeks(skill))
                    .description("Learn " + skill + " to improve job match")
                    .resources(List.of())
                    .build());
        }

        return SkillGapAnalysis.builder()
                .presentSkills(result.getPresentSkills())
                .criticalGaps(result.getMissingSkills().subList(0, Math.min(3, result.getMissingSkills().size())))
                .learningPath(learningPath)
                .recommendedJobs(result.getRecommendedJobs())
                .overallSkillMatchScore(result.getOverallSkillMatchScore())
                .analysisInsights("Basic skill gap analysis (Ollama unavailable)")
                .build();
    }

    private String determinePriority(String skill, List<JobRecommendation> jobs) {
        // Count how many jobs require this skill
        int count = 0;
        for (JobRecommendation job : jobs) {
            List<String> jobSkills = extractJobSkills(job);
            if (jobSkills.stream().anyMatch(s -> skillMatches(Set.of(skill), s))) {
                count++;
            }
        }
        if (count > jobs.size() * 0.7) return "HIGH";
        if (count > jobs.size() * 0.3) return "MEDIUM";
        return "LOW";
    }

    private int estimateWeeks(String skill) {
        // Rough estimation based on skill complexity
        String lower = skill.toLowerCase();
        if (lower.contains("docker") || lower.contains("kubernetes") || 
            lower.contains("aws") || lower.contains("terraform")) {
            return 6;
        }
        if (lower.contains("python") || lower.contains("go") || 
            lower.contains("rust") || lower.contains("scala")) {
            return 8;
        }
        if (lower.contains("machine learning") || lower.contains("tensorflow") ||
            lower.contains("pytorch") || lower.contains("data")) {
            return 12;
        }
        // Default estimate for most skills
        return 4;
    }

    private Set<String> extractSkillSet(ResumeAnalysis analysis) {
        Set<String> skills = new HashSet<>();
        if (analysis.getSkills() != null) {
            for (Skill s : analysis.getSkills()) {
                if (s.getName() != null) {
                    skills.add(s.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (analysis.getFoundKeywords() != null) {
            for (String k : analysis.getFoundKeywords()) {
                skills.add(k.toLowerCase(Locale.ROOT));
            }
        }
        return canonicalSkillSet(skills);
    }

    private List<String> extractJobSkills(JobRecommendation job) {
        List<String> skillKeywords = List.of(
                "java", "spring boot", "spring", "hibernate", "jpa", "angular", "typescript",
                "javascript", "react", "vue", "node", "python", "django", "flask", "fastapi",
                "sql", "mysql", "mongodb", "postgresql", "oracle", "sql server", "sqlite",
                "docker", "kubernetes", "aws", "ec2", "s3", "lambda", "azure", "gcp",
                "microservices", "rest api", "rest", "graphql", "grpc", "ci/cd", "git",
                "github", "gitlab", "kafka", "redis", "rabbitmq", "html", "css", "sass",
                "tailwind", "bootstrap", "jenkins", "terraform", "ansible", "linux",
                "c++", "c#", ".net", "go", "rust", "scala", "kotlin", "swift", "flutter",
                "react native", "dart", "android", "ios", "spark", "hadoop", "pyspark",
                "machine learning", "tensorflow", "pytorch", "keras", "nlp", "scikit-learn",
                "data science", "data analysis", "pandas", "numpy", "etl", "airflow",
                "agile", "scrum", "jira", "selenium", "junit", "mockito", "postman",
                "redux", "webpack", "npm", "yarn", "vite", "next.js", "nuxt", "express",
                "nestjs", "php", "laravel", "ruby", "rails", "wordpress", "elasticsearch",
                "opensearch", "nginx", "apache", "bash", "powershell", "shell", "zookeeper",
                "prometheus", "grafana", "splunk", "figma", "unity", "unreal engine"
        );
        String desc = (job.getDescription() != null ? job.getDescription().toLowerCase(Locale.ROOT) : "");
        desc += " " + (job.getTitle() != null ? job.getTitle().toLowerCase(Locale.ROOT) : "");
        List<String> found = new ArrayList<>();
        for (String skill : skillKeywords) {
            if (wordBoundaryMatches(desc, skill)) {
                String canonical = canonicalSkill(skill);
                if (!canonical.isEmpty() && !found.contains(canonical)) {
                    found.add(canonical);
                }
            }
        }
        return found;
    }

    private boolean wordBoundaryMatches(String text, String keyword) {
        String escaped = Pattern.quote(keyword);
        String regex = "(?<![a-zA-Z0-9+#.])" + escaped + "(?![a-zA-Z0-9+#.])";
        return Pattern.compile(regex).matcher(text).find();
    }

    private static final Map<String, String> SKILL_ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("reactjs", "react"),
            Map.entry("react.js", "react"),
            Map.entry("nodejs", "node"),
            Map.entry("node.js", "node"),
            Map.entry("angularjs", "angular"),
            Map.entry("angular.js", "angular"),
            Map.entry("springboot", "spring boot"),
            Map.entry("boot", "spring boot"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("gcp", "google cloud"),
            Map.entry("azure devops", "azure"),
            Map.entry("aws cloud", "aws"),
            Map.entry("aws ec2", "aws"),
            Map.entry("s3", "aws"),
            Map.entry("c#", "csharp"),
            Map.entry("csharp", "c#"),
            Map.entry(".net core", ".net"),
            Map.entry(".net", "dotnet"),
            Map.entry("dotnet", ".net"),
            Map.entry("react native", "react"),
            Map.entry("mysql", "sql"),
            Map.entry("postgresql", "sql"),
            Map.entry("oracle", "sql"),
            Map.entry("sql server", "sql"),
            Map.entry("nosql", "mongodb"),
            Map.entry("j2ee", "java"),
            Map.entry("restful", "rest api"),
            Map.entry("restful api", "rest api"),
            Map.entry("restfull", "rest"),
            Map.entry("ci", "ci/cd"),
            Map.entry("cd", "ci/cd"),
            Map.entry("ml", "machine learning"),
            Map.entry("deep learning", "machine learning"),
            Map.entry("pandas", "data analysis"),
            Map.entry("numpy", "data analysis"),
            Map.entry("gitlab", "git"),
            Map.entry("github", "git"),
            Map.entry("bitbucket", "git"),
            Map.entry("python", "python"),
            Map.entry("c++", "cpp"),
            Map.entry("cpp", "c++")
    );

    private static String canonicalSkill(String skill) {
        if (skill == null) return "";
        String s = skill.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return s;
        String alias = SKILL_ALIASES.get(s);
        if (alias != null) return alias;
        return s;
    }

    private static Set<String> canonicalSkillSet(Set<String> skills) {
        Set<String> canonical = new HashSet<>();
        for (String s : skills) {
            String c = canonicalSkill(s);
            if (!c.isEmpty()) canonical.add(c);
        }
        return canonical;
    }

    private boolean skillMatches(Set<String> resumeSkills, String jobSkill) {
        String js = canonicalSkill(jobSkill);
        if (js.isEmpty()) {
            return false;
        }
        Set<String> canonical = canonicalSkillSet(resumeSkills);
        if (canonical.contains(js)) {
            return true;
        }
        for (String rs : canonical) {
            if (js.length() >= 5 && rs.length() >= 5) {
                if (js.contains(rs) || rs.contains(js)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> buildHighlights(JobRecommendation job) {
        List<String> highlights = new ArrayList<>();
        if (job.getEmploymentType() != null) {
            highlights.add("Employment: " + job.getEmploymentType());
        }
        if (job.getLocation() != null) {
            highlights.add("Location: " + job.getLocation());
        }
        if (job.getMatchedSkills() != null && !job.getMatchedSkills().isEmpty()) {
            highlights.add("Skills you have: " + String.join(", ", job.getMatchedSkills()));
        }
        if (job.getMissingSkills() != null && !job.getMissingSkills().isEmpty()) {
            highlights.add("Skills to gain: " + String.join(", ", job.getMissingSkills()));
        }
        return highlights;
    }
}
