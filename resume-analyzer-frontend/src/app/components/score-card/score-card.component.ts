import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-score-card',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './score-card.component.html',
  styleUrl: './score-card.component.scss'
})
export class ScoreCardComponent {
  @Input() title = '';
  @Input() score = 0;
  @Input() icon = '';

  get colorClass(): string {
    if (this.score >= 85) return 'excellent';
    if (this.score >= 70) return 'good';
    if (this.score >= 50) return 'average';
    return 'poor';
  }

  get statusIcon(): string {
    if (this.score >= 85) return 'sentiment_very_satisfied';
    if (this.score >= 70) return 'sentiment_satisfied';
    if (this.score >= 50) return 'sentiment_neutral';
    return 'sentiment_dissatisfied';
  }

  get statusText(): string {
    if (this.score >= 85) return 'Excellent';
    if (this.score >= 70) return 'Good';
    if (this.score >= 50) return 'Needs Improvement';
    return 'Poor';
  }

  get ringStyle(): { [key: string]: string } {
    const circumference = 2 * Math.PI * 52;
    const offset = circumference - (this.score / 100) * circumference;
    return {
      strokeDasharray: `${circumference}`,
      strokeDashoffset: `${offset}`
    };
  }
}
