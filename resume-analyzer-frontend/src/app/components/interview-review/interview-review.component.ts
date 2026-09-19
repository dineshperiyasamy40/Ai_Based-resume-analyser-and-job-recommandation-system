import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { ResumeApiService } from '../../services/resume-api.service';
import { AnswerResult, InterviewSession } from '../../models/models';

@Component({
  selector: 'app-interview-review',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule
  ],
  templateUrl: './interview-review.component.html',
  styleUrl: './interview-review.component.scss'
})
export class InterviewReviewComponent implements OnInit {
  sessionId: string | null = null;
  session: InterviewSession | null = null;
  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private api: ResumeApiService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.sessionId = params.get('id');
      if (this.sessionId) {
        this.api.getInterviewSession(this.sessionId).subscribe({
          next: (session) => {
            this.session = session;
            this.loading = false;
          },
          error: (err) => {
            this.loading = false;
            this.error = (err as { error?: { message?: string } }).error?.message || 'Failed to load results.';
          }
        });
      }
    });
  }

  get answered(): AnswerResult[] {
    return this.session?.answerResults ?? [];
  }

  get scoreClass(): string {
    if (!this.session) return '';
    if (this.session.overallScore >= 70) return 'good';
    if (this.session.overallScore >= 40) return 'medium';
    return 'low';
  }
}