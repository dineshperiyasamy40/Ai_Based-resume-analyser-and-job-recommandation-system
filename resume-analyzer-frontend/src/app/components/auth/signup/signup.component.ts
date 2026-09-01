import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ResumeApiService } from '../../../services/resume-api.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.scss'
})
export class SignupComponent {
  name = '';
  email = '';
  password = '';
  loading = false;
  error = '';

  constructor(
    private api: ResumeApiService,
    private auth: AuthService,
    private router: Router
  ) {}

  onSubmit(): void {
    if (!this.name || !this.email || this.password.length < 6) {
      this.error = 'Please fill all fields (password must be at least 6 characters)';
      return;
    }
    this.loading = true;
    this.error = '';
    this.api.signup(this.name, this.email, this.password).subscribe({
      next: (resp) => {
        this.auth.storeAuth(resp);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 0) {
          this.error = 'Cannot connect to server. Make sure the backend is running.';
        } else {
          this.error = err.error?.message || 'Signup failed. Please try again.';
        }
      }
    });
  }
}
