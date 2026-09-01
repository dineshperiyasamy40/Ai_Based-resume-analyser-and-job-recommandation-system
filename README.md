# AI-Based Resume Analyzer & Job Recommendation System

Full-stack application that analyzes resumes using AI and recommends jobs based on skills, experience, and qualifications.

## Architecture

| Layer | Tech | Location |
|-------|------|----------|
| Frontend | Angular 18 + TypeScript + Material UI | `resume-analyzer-frontend/` |
| Backend | Spring Boot 3 + Java 21 | `resume-analyzer-backend/` |
| Database | MongoDB | localhost:27017 |
| AI Analysis | Ollama llama3.2 | Local REST API |
| Job Data | JSearch (RapidAPI) | REST API |

## Prerequisites

- **Java 21** - `java -version`
- **Node 20+** - `node --version`
- **Angular CLI 18+** - `ng version`
- **MongoDB** running on `localhost:27017`
- **Ollama** with the `llama3.2` model
- **JSearch API key** for live job recommendations

## Ollama Setup

Start Ollama and make sure the model is available:

```powershell
ollama serve
ollama pull llama3.2
ollama list
```

The backend connects to `http://localhost:11434` by default. You can change it with
`OLLAMA_BASE_URL` or `OLLAMA_MODEL` if required. No OpenAI API key is needed.

## Setup

### 1. Start MongoDB

```powershell
mongod --dbpath C:\data\db
```

### 2. Start Backend

```powershell
cd resume-analyzer-backend

# Use the Maven Wrapper (bundled, no global install needed):
.\mvnw -DskipTests spring-boot:run
```

Backend runs at: **http://localhost:8080**

### 3. Start Frontend

```powershell
cd resume-analyzer-frontend
ng serve
```

Frontend runs at: **http://localhost:4200**

## Usage Flow

1. **Sign Up** → Create an account (email + password)
2. **Login** → JWT token stored in localStorage
3. **Upload Resume** → Drop PDF/DOCX → drag & drop zone
4. **Scores appear immediately** (4 score cards):
   - Resume Quality Score (0-100)
   - ATS Compatibility Score (0-100)
   - Keyword Score (0-100)
   - Skill Match Score (0-100)
5. **Skill Gap Analysis** → See which skills you have vs. what's missing
6. **Job Recommendations** → See jobs matched to your profile with match percentage
7. **Dashboard** → View all past uploads and their scores

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/signup` | No | Register user |
| POST | `/api/auth/login` | No | Login, returns JWT |
| POST | `/api/resume/upload` | Yes | Upload PDF/DOCX |
| GET | `/api/resume/list` | Yes | List user's resumes |
| GET | `/api/resume/{id}` | Yes | Get resume + analysis |
| GET | `/api/resume/{id}/analysis` | Yes | Get detailed analysis |
| GET | `/api/resume/{id}/skill-gap` | Yes | Skill gap analysis |
| GET | `/api/resume/{id}/jobs` | Yes | Job recommendations |

## Project Structure

```
resume-analyzer-backend/
└── src/main/java/com/resume/analyzer/
    ├── ResumeAnalyzerApplication.java
    ├── config/           SecurityConfig, GlobalExceptionHandler
    ├── controller/       AuthController, ResumeController
    ├── dto/              SignupRequest, LoginRequest, AuthResponse
    ├── model/            User, Resume, ResumeAnalysis, Skill, JobRecommendation
    ├── repository/       UserRepository, ResumeRepository
    ├── security/         JwtService, JwtFilter, CustomUserDetailsService
    └── service/          AuthService, ResumeService, OpenAIService, JSearchService, MatchingService

resume-analyzer-frontend/
└── src/app/
    ├── app.component.ts/html    Navbar + router-outlet
    ├── app.routes.ts            All routes
    ├── guards/auth.guard.ts     JWT route guard
    ├── services/                auth.service, resume-api.service, auth.interceptor
    ├── models/models.ts         All TypeScript interfaces
    └── components/
        ├── auth/login/          Login form
        ├── auth/signup/         Signup form
        ├── dashboard/           Resume list + quick actions
        ├── resume-upload/       Drag-drop upload + processing animation
        ├── resume-analysis/     4 score cards + detailed breakdown
        ├── skill-gap/           Present vs missing skills
        ├── score-card/          Reusable circular score indicator
        └── job-recommendations/ Matched jobs with match %
```

## How Scores Work

### Resume Quality (0-100)
AI evaluates content completeness, structure, action verbs, quantification, and impact statements.

### ATS Compatibility (0-100)
Checks standard section headers, keyword density, clean formatting, and parseability by Applicant Tracking Systems.

### Keyword Score (0-100)
Compares found keywords (relevant terms in your resume) vs. missing keywords (terms expected in your industry).

### Skill Match (0-100)
Calculated by comparing your detected skills against requirements from job postings found via JSearch API.

## Tech Details

- **Resume Parsing:** Apache Tika extracts text from PDF/DOCX files
- **AI Analysis:** OpenAI GPT-4o-mini returns structured JSON with all analysis data
- **Job Matching:** Skills extracted from resume are compared against JSearch job postings
- **Auth:** JWT tokens with 24h expiry; BCrypt password hashing
- **CORS:** Configured to allow `http://localhost:4200` (Angular dev server)
