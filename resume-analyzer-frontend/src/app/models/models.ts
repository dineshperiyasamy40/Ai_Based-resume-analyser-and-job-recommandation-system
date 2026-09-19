export interface AuthResponse {
  token: string;
  tokenType: string;
  email: string;
  name: string;
  userId: string;
}

export interface Skill {
  name: string;
  level: string;
}

export interface Education {
  degree: string;
  institution: string;
  field: string;
  years: string;
}

export interface Experience {
  role: string;
  company: string;
  duration: string;
  responsibilities: string;
}

export interface ResumeAnalysis {
  resumeScore: number;
  resumeFeedback: string;
  atsScore: number;
  atsIssues: string[];
  atsSuggestions: string[];
  foundKeywords: string[];
  missingKeywords: string[];
  keywordScore: number;
  skills: Skill[];
  education: Education[];
  experience: Experience[];
  summary: string;
  createdAt?: string;
}

export interface Resume {
  id: string;
  userId: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  content: string;
  analysis: ResumeAnalysis;
  createdAt: string;
}

export interface JobRecommendation {
  jobId: string;
  title: string;
  company: string;
  location: string;
  description: string;
  url: string;
  employmentType: string;
  postedAt: string;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  jobHighlights: string[];
  seniority?: string; // junior, mid, senior, lead (from Ollama)
  requiredSkills?: string[]; // prioritized list of required skills
  careerJustification?: string; // why this role fits
}

export interface SkillGapResult {
  presentSkills: string[];
  missingSkills: string[];
  recommendedJobs: JobRecommendation[];
  overallSkillMatchScore: number;
}

export interface LearningStep {
  skill: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  estimatedWeeks: number;
  description: string;
  resources: string[];
}

export interface SkillGapAnalysis {
  presentSkills: string[];
  criticalGaps: string[]; // Most important skills to learn
  learningPath: LearningStep[]; // Ordered steps to close gaps
  recommendedJobs: JobRecommendation[];
  overallSkillMatchScore: number;
  analysisInsights?: string; // AI-generated summary from Ollama
}

export interface RecommendedRole {
  roleName: string;
  seniority: 'junior' | 'mid' | 'senior' | 'lead';
  matchPercentage: number;
  requiredSkills: string[];
  niceToHaveSkills: string[];
  careerJustification: string;
}

export interface ResumeImprovement {
  improvedResume: string;
  keywordInsertions: string[];
  actionVerbs: string[];
  atsFormattingTips: string[];
  rationale: string;
}

export interface InterviewQuestion {
  question: string;
  category: string;
  difficulty: string;
  tips: string;
  modelAnswer: string;
}

export interface AnswerFeedback {
  score: number;
  strengths: string[];
  improvements: string[];
  suggestedAnswer: string;
}

export interface AnswerResult {
  question: InterviewQuestion;
  userAnswer: string;
  feedback: AnswerFeedback;
}

export interface InterviewSession {
  id: string;
  userId: string;
  resumeId: string;
  resumeFileName: string;
  targetRole: string;
  difficulty: string;
  questions: InterviewQuestion[];
  answerResults: AnswerResult[];
  completed: boolean;
  overallScore: number;
  summaryFeedback?: string;
  createdAt?: string;
}

export interface InterviewSetupPayload {
  resumeId: string;
  targetRole: string;
  difficulty: string;
  questionCount: number;
}
