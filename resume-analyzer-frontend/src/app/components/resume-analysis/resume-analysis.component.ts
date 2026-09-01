import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { ResumeApiService } from '../../services/resume-api.service';
import { Resume, ResumeAnalysis } from '../../models/models';
import { ScoreCardComponent } from '../score-card/score-card.component';

@Component({
  selector: 'app-resume-analysis',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
    ScoreCardComponent
  ],
  templateUrl: './resume-analysis.component.html',
  styleUrl: './resume-analysis.component.scss'
})
export class ResumeAnalysisComponent implements OnInit {
  resumeId: string | null = null;
  resume: Resume | null = null;
  analysis: ResumeAnalysis | null = null;
  overallSkillMatch = 0;
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
    this.resume = null;
    this.analysis = null;
    this.overallSkillMatch = 0;
    this.api.getResume(id).subscribe({
      next: (resume) => {
        this.resume = resume;
        this.analysis = resume.analysis;
        this.loading = false;
      },
      error: (err) => {
        this.resume = null;
        this.analysis = null;
        this.error = err.error?.message || 'Failed to load resume analysis.';
        this.loading = false;
      }
    });
    this.api.getSkillGap(id).subscribe({
      next: (gap) => {
        this.overallSkillMatch = gap.overallSkillMatchScore;
      },
      error: () => {
        this.overallSkillMatch = 0;
      }
    });
  }
}
