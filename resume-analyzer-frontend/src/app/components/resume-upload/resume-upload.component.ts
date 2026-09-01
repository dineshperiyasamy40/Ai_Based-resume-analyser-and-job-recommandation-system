import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { ResumeApiService } from '../../services/resume-api.service';
import { Resume } from '../../models/models';

@Component({
  selector: 'app-resume-upload',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  templateUrl: './resume-upload.component.html',
  styleUrl: './resume-upload.component.scss'
})
export class ResumeUploadComponent {
  selectedFile: File | null = null;
  uploading = false;
  uploadProgress = 0;
  processing = false;
  error = '';
  dragActive = false;

  // Processing checklist states
  stages = [
    { label: 'Uploading resume', done: false, active: false },
    { label: 'Extracting & parsing content', done: false, active: false },
    { label: 'Analyzing with AI', done: false, active: false },
    { label: 'Calculating scores & matching jobs', done: false, active: false }
  ];

  constructor(
    private api: ResumeApiService,
    private router: Router
  ) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      this.validateFile();
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      this.selectedFile = event.dataTransfer.files[0];
      this.validateFile();
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = true;
  }

  onDragLeave(): void {
    this.dragActive = false;
  }

  private validateFile(): void {
    const name = this.selectedFile?.name.toLowerCase() ?? '';
    if (!(name.endsWith('.pdf') || name.endsWith('.docx') || name.endsWith('.doc'))) {
      this.error = 'Please upload a PDF or DOCX file.';
      this.selectedFile = null;
    } else {
      this.error = '';
    }
  }

  onSubmit(): void {
    if (!this.selectedFile) {
      this.error = 'Please select a resume file first.';
      return;
    }
    this.error = '';
    this.uploading = true;
    this.uploadProgress = 0;

    this.stages[0].active = true;
    this.api.upload(this.selectedFile).subscribe({
      next: ({ progress, resume }) => {
        this.uploadProgress = progress;
        if (progress >= 100 && resume) {
          this.completeUpload(resume);
        } else if (progress >= 100) {
          // server processing
          this.finishStages();
        }
      },
      error: (err) => {
        this.uploading = false;
        this.processing = false;
        this.error = err.error?.message || 'Upload failed. Please try again.';
        this.resetStages();
      }
    });

    // Move through the visual stages progressively
    this.animateStages();
  }

  private animateStages(): void {
    const delays = [800, 2000, 3500, 5200];
    delays.forEach((delay, i) => {
      setTimeout(() => {
        if (i > 0) this.stages[i - 1].done = true;
        if (i < this.stages.length) {
          this.stages[i].active = true;
          if (i === this.stages.length - 1) this.processing = false;
        }
      }, delay);
    });
  }

  private finishStages(): void {
    this.stages.forEach((s) => {
      s.done = true;
      s.active = false;
    });
    this.processing = false;
  }

  private completeUpload(resume: Resume): void {
    this.finishStages();
    this.uploading = false;
    this.router.navigate(['/analysis', resume.id]);
  }

  private resetStages(): void {
    this.stages.forEach((s) => {
      s.done = false;
      s.active = false;
    });
  }
}
