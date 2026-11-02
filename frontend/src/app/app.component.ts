import { Component, OnDestroy } from '@angular/core';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { Capacitor } from '@capacitor/core';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  imports: [IonApp, IonRouterOutlet],
})
export class AppComponent implements OnDestroy {
  private readonly nativeShell = Capacitor.isNativePlatform();
  private readonly mobileShell = this.nativeShell || window.matchMedia('(max-width: 768px)').matches;
  private horizontalScroller: HTMLElement | null = null;
  private startX = 0;
  private startY = 0;
  private startScrollLeft = 0;
  private horizontalGesture = false;
  private readonly onTouchStart = (event: TouchEvent) => {
    const touch = event.touches[0];
    if (!touch) return;
    this.horizontalScroller = this.findHorizontalScroller(event.target);
    this.startX = touch.clientX;
    this.startY = touch.clientY;
    this.startScrollLeft = this.horizontalScroller?.scrollLeft ?? 0;
    this.horizontalGesture = false;
  };
  private readonly onTouchMove = (event: TouchEvent) => {
    if (!this.horizontalScroller) return;
    const touch = event.touches[0];
    if (!touch) return;
    const deltaX = touch.clientX - this.startX;
    const deltaY = touch.clientY - this.startY;
    if (!this.horizontalGesture && Math.abs(deltaX) > 8 && Math.abs(deltaX) > Math.abs(deltaY)) {
      this.horizontalGesture = true;
    }
    if (!this.horizontalGesture) return;
    event.preventDefault();
    this.horizontalScroller.scrollLeft = this.startScrollLeft - deltaX;
  };
  private readonly onTouchEnd = () => {
    this.horizontalScroller = null;
    this.horizontalGesture = false;
  };

  constructor() {
    if (this.mobileShell && typeof document !== 'undefined') {
      document.documentElement.classList.add('native-shell');
      document.addEventListener('touchstart', this.onTouchStart, { passive: true });
      document.addEventListener('touchmove', this.onTouchMove, { passive: false });
      document.addEventListener('touchend', this.onTouchEnd, { passive: true });
      document.addEventListener('touchcancel', this.onTouchEnd, { passive: true });
    }
  }

  ngOnDestroy(): void {
    if (this.mobileShell && typeof document !== 'undefined') {
      document.documentElement.classList.remove('native-shell');
      document.removeEventListener('touchstart', this.onTouchStart);
      document.removeEventListener('touchmove', this.onTouchMove);
      document.removeEventListener('touchend', this.onTouchEnd);
      document.removeEventListener('touchcancel', this.onTouchEnd);
    }
  }

  private findHorizontalScroller(target: EventTarget | null): HTMLElement | null {
    let element = target instanceof Element ? target.closest<HTMLElement>('*') : null;
    while (element) {
      const styles = window.getComputedStyle(element);
      const canScroll = element.scrollWidth > element.clientWidth
        && (styles.overflowX === 'auto' || styles.overflowX === 'scroll');
      if (canScroll) return element;
      element = element.parentElement;
    }
    return null;
  }
}
