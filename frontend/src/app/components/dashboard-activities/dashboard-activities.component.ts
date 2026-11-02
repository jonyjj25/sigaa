import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonCard, IonCardHeader, IonCardTitle, IonCardContent } from '@ionic/angular/standalone';
import { Activity } from '../../models/dashboard.models';
import { INSTITUTIONAL_BLUE } from '../../shared/ui-colors';

@Component({
  selector: 'app-dashboard-activities',
  standalone: true,
  imports: [CommonModule, IonCard, IonCardHeader, IonCardTitle, IonCardContent],
  templateUrl: './dashboard-activities.component.html',
  styleUrls: ['./dashboard-activities.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardActivitiesComponent {
  readonly activities = input.required<Activity[]>();

  protected timeAgo(date: Date): string {
    const now = Date.now();
    const diff = now - new Date(date).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'Ahora';
    if (minutes < 60) return `Hace ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `Hace ${hours}h`;
    const days = Math.floor(hours / 24);
    return `Hace ${days} día${days > 1 ? 's' : ''}`;
  }

  protected initials(name: string): string {
    return name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
  }

  protected avatarColors = [INSTITUTIONAL_BLUE, '#10b981', '#8b5cf6', '#f59e0b', '#ef4444', '#ec4899'];
  protected getColor(index: number): string {
    return this.avatarColors[index % this.avatarColors.length];
  }
}
