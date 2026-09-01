import { Routes } from '@angular/router';
import { AuthGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./components/auth/login/login.component').then(m => m.LoginComponent) },
  { path: 'signup', loadComponent: () => import('./components/auth/signup/signup.component').then(m => m.SignupComponent) },
  {
    path: 'dashboard',
    canActivate: [AuthGuard],
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'upload',
    canActivate: [AuthGuard],
    loadComponent: () => import('./components/resume-upload/resume-upload.component').then(m => m.ResumeUploadComponent)
  },
  {
    path: 'analysis/:id',
    canActivate: [AuthGuard],
    loadComponent: () => import('./components/resume-analysis/resume-analysis.component').then(m => m.ResumeAnalysisComponent)
  },
  {
    path: 'skills/:id',
    canActivate: [AuthGuard],
    loadComponent: () => import('./components/skill-gap/skill-gap.component').then(m => m.SkillGapComponent)
  },
  {
    path: 'skill-gap/:id',
    canActivate: [AuthGuard],
    loadComponent: () => import('./components/skill-gap/skill-gap.component').then(m => m.SkillGapComponent)
  },
  {
    path: 'jobs/:id',
    canActivate: [AuthGuard],
    loadComponent: () => import('./components/job-recommendations/job-recommendations.component').then(m => m.JobRecommendationsComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
