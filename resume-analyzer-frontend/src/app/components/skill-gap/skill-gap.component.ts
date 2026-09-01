import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatExpansionModule } from '@angular/material/expansion';
import { ResumeApiService } from '../../services/resume-api.service';
import { SkillGapAnalysis } from '../../models/models';

@Component({
  selector: 'app-skill-gap',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatExpansionModule
  ],
  templateUrl: './skill-gap.component.html',
  styleUrl: './skill-gap.component.scss'
})
export class SkillGapComponent implements OnInit {
  resumeId: string | null = null;
  result: SkillGapAnalysis | null = null;
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
    this.result = null;
    this.api.getSkillGap(id).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err) => {
        this.result = null;
        this.error = err.error?.message || 'Failed to load skill gap analysis.';
        this.loading = false;
      }
    });
  }

  getPriorityIcon(priority: string): string {
    switch (priority?.toUpperCase()) {
      case 'HIGH':
        return 'priority_high';
      case 'MEDIUM':
        return 'schedule';
      case 'LOW':
        return 'trending_down';
      default:
        return 'info';
    }
  }

  getPriorityColor(priority: string): string {
    switch (priority?.toUpperCase()) {
      case 'HIGH':
        return 'warn';
      case 'MEDIUM':
        return 'accent';
      case 'LOW':
        return 'primary';
      default:
        return '';
    }
  }
}
