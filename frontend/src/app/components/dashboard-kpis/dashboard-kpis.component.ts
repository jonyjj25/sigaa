import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonCard, IonCardHeader, IonCardContent, IonIcon } from '@ionic/angular/standalone';
import { KpiCard } from '../../models/dashboard.models';
import { addIcons } from 'ionicons';
import { checkboxOutline } from 'ionicons/icons';

@Component({
  selector: 'app-dashboard-kpis',
  standalone: true,
  imports: [CommonModule, RouterLink, IonCard, IonCardHeader, IonCardContent, IonIcon],
  templateUrl: './dashboard-kpis.component.html',
  styleUrls: ['./dashboard-kpis.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardKpisComponent {
  readonly kpis = input.required<KpiCard[]>();

  constructor() {
    addIcons({ checkboxOutline });
  }
}
