import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ResumeApiService } from '../../services/resume-api.service';
import { AuthService } from '../../services/auth.service';
import { Resume } from '../../models/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  resumes: Resume[] = [];
  loading = true;
  error = '';

  constructor(
    private api: ResumeApiService,
    public auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadResumes();
  }

  private loadResumes(): void {
    this.loading = true;
    this.api.listResumes().subscribe({
      next: (resumes) => {
        this.resumes = resumes;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.status === 0
          ? 'Cannot connect to server. Make sure the backend is running and try again.'
          : (err.error?.message || 'Failed to load resumes.');
        this.loading = false;
      }
    });
  }

  goToAnalysis(resumeId: string): void {
    this.router.navigate(['/analysis', resumeId]);
  }
}
