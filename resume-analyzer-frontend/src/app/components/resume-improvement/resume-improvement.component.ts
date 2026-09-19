import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ResumeApiService } from '../../services/resume-api.service';
import { Resume, ResumeImprovement } from '../../models/models';

@Component({
  selector: 'app-resume-improvement',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatSnackBarModule
  ],
  templateUrl: './resume-improvement.component.html',
  styleUrl: './resume-improvement.component.scss'
})
export class ResumeImprovementComponent implements OnInit {
  resumeId: string | null = null;
  resume: Resume | null = null;
  improvement: ResumeImprovement | null = null;
  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private api: ResumeApiService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.resumeId = params.get('id');
      if (this.resumeId) {
        this.loadData(this.resumeId);
      }
    });
  }

  private loadData(id: string): void {
    this.loading = true;
    this.error = '';
    this.api.getResume(id).subscribe({
      next: (resume) => {
        this.resume = resume;
        this.api.getImprovement(id).subscribe({
          next: (improvement) => {
            this.improvement = improvement;
            this.loading = false;
          },
          error: (err) => this.handleError(err)
        });
      },
      error: (err) => this.handleError(err)
    });
  }

  private handleError(err: unknown): void {
    this.loading = false;
    const error = err as { status?: number; error?: { message?: string } };
    this.error = error.error?.message || 'Failed to load resume improvement.';
  }

  copyImproved(): void {
    if (!this.improvement) return;
    navigator.clipboard.writeText(this.improvement.improvedResume).then(() => {
      this.snackBar.open('Improved resume copied to clipboard', 'Close', { duration: 3000 });
    });
  }

  downloadImproved(): void {
    if (!this.improvement) return;
    const blob = new Blob([this.improvement.improvedResume], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = this.resume ? `improved-${this.resume.fileName.replace(/\.[^/.]+$/, '')}.txt` : 'improved-resume.txt';
    a.click();
    URL.revokeObjectURL(url);
  }
}