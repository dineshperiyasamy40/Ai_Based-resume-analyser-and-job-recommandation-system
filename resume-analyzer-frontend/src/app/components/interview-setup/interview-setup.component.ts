import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatRadioModule } from '@angular/material/radio';
import { FormsModule } from '@angular/forms';
import { ResumeApiService } from '../../services/resume-api.service';
import { Resume } from '../../models/models';

@Component({
  selector: 'app-interview-setup',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatInputModule,
    MatFormFieldModule,
    MatRadioModule
  ],
  templateUrl: './interview-setup.component.html',
  styleUrl: './interview-setup.component.scss'
})
export class InterviewSetupComponent implements OnInit {
  resumes: Resume[] = [];
  loadingResumes = true;
  error = '';
  generating = false;

  selectedResumeId = '';
  targetRole = '';
  difficulty = 'medium';
  questionCount = 5;
  counts = [3, 5, 8];

  constructor(
    private api: ResumeApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.api.listResumes().subscribe({
      next: (resumes) => {
        this.resumes = resumes;
        if (resumes.length > 0) {
          this.selectedResumeId = resumes[0].id;
        }
        this.loadingResumes = false;
      },
      error: (err) => {
        this.loadingResumes = false;
        this.error = (err as { error?: { message?: string } }).error?.message || 'Failed to load resumes.';
      }
    });
  }

  startInterview(): void {
    if (!this.selectedResumeId) {
      this.error = 'Please select a resume to practice with.';
      return;
    }
    this.generating = true;
    this.error = '';
    this.api.generateInterview({
      resumeId: this.selectedResumeId,
      targetRole: this.targetRole,
      difficulty: this.difficulty,
      questionCount: this.questionCount
    }).subscribe({
      next: (session) => {
        this.generating = false;
        this.router.navigate(['/interview', session.id, 'practice']);
      },
      error: (err) => {
        this.generating = false;
        this.error = (err as { error?: { message?: string } }).error?.message || 'Failed to generate interview questions.';
      }
    });
  }
}