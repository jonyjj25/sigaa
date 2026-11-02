import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonIcon } from '@ionic/angular/standalone';
import { Notification } from '../../models/dashboard.models';
import { INSTITUTIONAL_BLUE } from '../../shared/ui-colors';

@Component({
  selector: 'app-dashboard-notifications',
  standalone: true,
  imports: [CommonModule, IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonIcon],
  templateUrl: './dashboard-notifications.component.html',
  styleUrls: ['./dashboard-notifications.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardNotificationsComponent {
  readonly notifications = input.required<Notification[]>();
  readonly notificationClick = output<Notification>();

  protected readonly typeIcons: Record<string, string> = {
    info: 'information-circle-outline',
    success: 'checkmark-circle-outline',
    warning: 'alert-circle-outline',
    error: 'close-circle-outline',
  };

  protected readonly typeColors: Record<string, string> = {
    info: INSTITUTIONAL_BLUE,
    success: '#10b981',
    warning: '#f59e0b',
    error: '#ef4444',
  };

  protected onOpen(notification: Notification): void {
    this.notificationClick.emit(notification);
  }
}
