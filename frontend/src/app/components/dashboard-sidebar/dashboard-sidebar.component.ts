import { Component, input, output, inject, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { SidebarItem } from '../../models/dashboard.models';
import { AuthService } from '../../services/auth.service';
import { addIcons } from 'ionicons';
import { App } from '@capacitor/app';
import { Capacitor, PluginListenerHandle } from '@capacitor/core';
import {
  gridOutline, peopleOutline, clipboardOutline,
  cubeOutline, barChartOutline, settingsOutline, logOutOutline,
  documentTextOutline, qrCodeOutline, folderOutline, calendarOutline, personAddOutline,
  lockClosedOutline, shieldOutline, informationCircleOutline,
  gitPullRequestOutline, businessOutline, readerOutline, mailOutline,
} from 'ionicons/icons';

@Component({
  selector: 'app-dashboard-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, IonIcon],
  templateUrl: './dashboard-sidebar.component.html',
  styleUrls: ['./dashboard-sidebar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardSidebarComponent {
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private touchStartX: number | null = null;
  private backButtonHandle: PluginListenerHandle | null = null;

  readonly items = input.required<SidebarItem[]>();
  readonly collapsed = input(false);
  readonly logout = output<void>();

  protected closeOnMobileNavigation(): void {
    if (typeof window !== 'undefined' && window.innerWidth <= 768) {
      this.authService.sidebarCollapsed.set(true);
    }
  }

  protected closeMobileSidebar(): void {
    this.closeOnMobileNavigation();
  }

  constructor() {
    addIcons({
      gridOutline, peopleOutline, clipboardOutline,
      cubeOutline, barChartOutline, settingsOutline, logOutOutline,
      documentTextOutline, qrCodeOutline, folderOutline, calendarOutline,
      lockClosedOutline, shieldOutline, informationCircleOutline, personAddOutline,
      gitPullRequestOutline, businessOutline, readerOutline, mailOutline,
    });

    if (typeof document !== 'undefined') {
      const onTouchStart = (event: TouchEvent) => {
        this.touchStartX = event.touches.item(0)?.clientX ?? null;
      };
      const onTouchEnd = (event: TouchEvent) => {
        const startX = this.touchStartX;
        const endX = event.changedTouches.item(0)?.clientX;
        this.touchStartX = null;
        if (startX === null || endX === undefined || typeof window === 'undefined' || window.innerWidth > 768) return;

        const distance = endX - startX;
        if (this.collapsed() && startX <= 28 && distance >= 64) {
          this.authService.sidebarCollapsed.set(false);
        } else if (!this.collapsed() && startX <= 300 && distance <= -64) {
          this.authService.sidebarCollapsed.set(true);
        }
      };
      document.addEventListener('touchstart', onTouchStart, { passive: true });
      document.addEventListener('touchend', onTouchEnd, { passive: true });
      this.destroyRef.onDestroy(() => {
        document.removeEventListener('touchstart', onTouchStart);
        document.removeEventListener('touchend', onTouchEnd);
      });
    }

    if (Capacitor.isNativePlatform()) {
      void App.addListener('backButton', () => {
        if (!this.collapsed()) this.authService.sidebarCollapsed.set(true);
      }).then(handle => {
        this.backButtonHandle = handle;
        this.destroyRef.onDestroy(() => void this.backButtonHandle?.remove());
      });
    }
  }
}
