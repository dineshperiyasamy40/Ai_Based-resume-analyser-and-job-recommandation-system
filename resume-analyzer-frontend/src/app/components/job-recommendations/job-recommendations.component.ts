import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ResumeApiService } from '../../services/resume-api.service';
import { JobRecommendation } from '../../models/models';

@Component({
  selector: 'app-job-recommendations',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './job-recommendations.component.html',
  styleUrl: './job-recommendations.component.scss'
})
export class JobRecommendationsComponent implements OnInit {
  resumeId: string | null = null;
  jobs: JobRecommendation[] = [];
  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private api: ResumeApiService
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
    this.jobs = [];
    this.api.getJobs(id).subscribe({
      next: (jobs) => {
        this.jobs = jobs;
        this.loading = false;
      },
      error: (err) => {
        this.jobs = [];
        this.error = err.error?.message || 'Failed to load job recommendations.';
        this.loading = false;
      }
    });
  }

  scoreColor(score: number): string {
    if (score >= 85) return '#4caf50';
    if (score >= 70) return '#2196f3';
    if (score >= 50) return '#ff9800';
    return '#f44336';
  }
}
