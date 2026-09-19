import { Injectable } from '@angular/core';
import { HttpClient, HttpEventType, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  AuthResponse,
  AnswerFeedback,
  InterviewSession,
  InterviewSetupPayload,
  JobRecommendation,
  Resume,
  ResumeAnalysis,
  ResumeImprovement,
  SkillGapAnalysis,
  SkillGapResult
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class ResumeApiService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  signup(name: string, email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/signup`, { name, email, password });
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/login`, { email, password });
  }

  upload(file: File): Observable<{ progress: number; resume?: Resume }> {
    const formData = new FormData();
    formData.append('file', file);
    const req = new HttpRequest('POST', `${this.baseUrl}/resume/upload`, formData, {
      reportProgress: true
    });
    return this.http.request(req).pipe(
      map((event) => {
        if (event.type === HttpEventType.UploadProgress) {
          const progress = event.total ? Math.round((100 * event.loaded) / event.total) : 0;
          return { progress };
        } else if (event instanceof HttpResponse) {
          return { progress: 100, resume: event.body as Resume };
        }
        return { progress: 0 };
      })
    );
  }

  listResumes(): Observable<Resume[]> {
    return this.http.get<Resume[]>(`${this.baseUrl}/resume/list`);
  }

  getResume(id: string): Observable<Resume> {
    return this.http.get<Resume>(`${this.baseUrl}/resume/${id}`);
  }

  getAnalysis(id: string): Observable<ResumeAnalysis> {
    return this.http.get<ResumeAnalysis>(`${this.baseUrl}/resume/${id}/analysis`);
  }

  getSkillGap(id: string): Observable<SkillGapAnalysis> {
    return this.http.get<SkillGapAnalysis>(`${this.baseUrl}/resume/${id}/skill-gap`);
  }

  getJobs(id: string): Observable<JobRecommendation[]> {
    return this.http.get<JobRecommendation[]>(`${this.baseUrl}/resume/${id}/jobs`);
  }

  getImprovement(id: string): Observable<ResumeImprovement> {
    return this.http.get<ResumeImprovement>(`${this.baseUrl}/resume/${id}/improvement`);
  }

  generateInterview(payload: InterviewSetupPayload): Observable<InterviewSession> {
    return this.http.post<InterviewSession>(`${this.baseUrl}/interview/generate`, payload);
  }

  submitInterviewAnswer(sessionId: string, questionIndex: number, answer: string): Observable<AnswerFeedback> {
    return this.http.post<AnswerFeedback>(`${this.baseUrl}/interview/${sessionId}/answer`, { questionIndex, answer });
  }

  getInterviewSession(sessionId: string): Observable<InterviewSession> {
    return this.http.get<InterviewSession>(`${this.baseUrl}/interview/${sessionId}`);
  }

  listInterviewSessions(): Observable<InterviewSession[]> {
    return this.http.get<InterviewSession[]>(`${this.baseUrl}/interview/list`);
  }
}
