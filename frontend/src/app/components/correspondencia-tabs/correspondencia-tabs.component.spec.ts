import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { CorrespondenciaTabsComponent } from './correspondencia-tabs.component';

describe('CorrespondenciaTabsComponent', () => {
  let fixture: ComponentFixture<CorrespondenciaTabsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CorrespondenciaTabsComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { hasPermission: () => true } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CorrespondenciaTabsComponent);
    fixture.componentRef.setInput('active', 'notas');
    fixture.detectChanges();
  });

  it('shows both sections and marks the current one', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];

    expect(links.length).toBe(2);
    expect(links[0].classList.contains('active')).toBeTrue();
    expect(links[0].getAttribute('aria-current')).toBe('page');
    expect(links[1].classList.contains('active')).toBeFalse();
  });
});
