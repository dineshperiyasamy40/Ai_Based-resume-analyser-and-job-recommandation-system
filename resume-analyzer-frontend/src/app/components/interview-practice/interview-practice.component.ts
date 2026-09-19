import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { FormsModule } from '@angular/forms';
import { ResumeApiService } from '../../services/resume-api.service';
import { AnswerFeedback, InterviewQuestion, InterviewSession } from '../../models/models';

@Component({
  selector: 'app-interview-practice',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule
  ],
  templateUrl: './interview-practice.component.html',
  styleUrl: './interview-practice.component.scss'
})
export class InterviewPracticeComponent implements OnInit {
  sessionId: string | null = null;
  session: InterviewSession | null = null;
  question: InterviewQuestion | null = null;
  currentIndex = 0;
  answer = '';
  feedback: AnswerFeedback | null = null;
  submitting = false;
  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private api: ResumeApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.sessionId = params.get('id');
      if (this.sessionId) {
        this.loadSession(this.sessionId);
      }
    });
  }

  private loadSession(id: string): void {
    this.loading = true;
    this.api.getInterviewSession(id).subscribe({
      next: (session) => {
        this.session = session;
        if (session.completed) {
          this.router.navigate(['/interview', session.id, 'review']);
          return;
        }
        this.currentIndex = session.answerResults ? session.answerResults.length : 0;
        this.setCurrentQuestion();
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = (err as { error?: { message?: string } }).error?.message || 'Failed to load interview session.';
      }
    });
  }

  private setCurrentQuestion(): void {
    if (this.session && this.session.questions && this.session.questions.length > 0) {
      this.question = this.session.questions[this.currentIndex] ?? null;
    } else {
      this.question = null;
    }
  }

  submitAnswer(): void {
    if (!this.session || !this.sessionId || !this.answer.trim()) return;
    this.submitting = true;
    this.api.submitInterviewAnswer(this.sessionId, this.currentIndex, this.answer.trim()).subscribe({
      next: (feedback) => {
        this.feedback = feedback;
        this.submitting = false;
      },
      error: (err) => {
        this.submitting = false;
        this.error = (err as { error?: { message?: string } }).error?.message || 'Failed to evaluate your answer.';
      }
    });
  }

  nextQuestion(): void {
    if (!this.session) return;
    if (this.currentIndex + 1 >= this.session.questions.length) {
      this.router.navigate(['/interview', this.session.id, 'review']);
      return;
    }
    this.currentIndex++;
    this.answer = '';
    this.feedback = null;
    this.setCurrentQuestion();
  }

  get totalQuestions(): number {
    return this.session?.questions?.length ?? 0;
  }

  get progressLabel(): string {
    return `${this.currentIndex + 1} / ${this.totalQuestions}`;
  }

  getCategoryIcon(category: string): string {
    switch (category?.toLowerCase()) {
      case 'technical':
        return 'code';
      case 'situational':
        return 'lightbulb';
      default:
        return 'people_alt';
    }
  }
}