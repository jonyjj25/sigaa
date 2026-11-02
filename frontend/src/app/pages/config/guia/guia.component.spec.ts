import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { GuiaComponent } from './guia.component';

describe('GuiaComponent', () => {
  let fixture: ComponentFixture<GuiaComponent>;
  const permissions = signal<string[]>([]);

  beforeEach(async () => {
    permissions.set([]);
    await TestBed.configureTestingModule({
      imports: [GuiaComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: {
        hasPermission: (module: string, action: string) => permissions().includes(`${module}:${action}`),
        hasRole: () => false,
        isSuperAdmin: () => false,
      } }],
    }).compileComponents();
    fixture = TestBed.createComponent(GuiaComponent);
    fixture.detectChanges();
  });

  it('shows only permitted module guides and keeps personal help available', () => {
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('Primeros pasos');
    expect(root.querySelector('a[href="/documentos"]')).toBeNull();
    permissions.set(['Documentos:Ver']);
    fixture.detectChanges();
    expect(root.textContent).toContain('Documentos');
    expect(root.textContent).not.toContain('Correspondencia · Memos');
    expect(root.textContent).not.toContain('disponible solo para Superadmin');
  });

  it('explains existing correspondence and automatic document versions', () => {
    permissions.set(['Notas:Ver', 'Memos:Ver', 'Documentos:Ver']);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    for (const button of Array.from(root.querySelectorAll<HTMLButtonElement>('.guide-heading'))) {
      if (button.getAttribute('aria-expanded') === 'false') button.click();
    }
    fixture.detectChanges();
    expect(root.textContent).toContain('versión 1.0');
    expect(root.textContent).toContain('firma física');
    expect(root.textContent).toContain('numeración de Memos y la de Notas son independientes');
    expect(root.querySelector('a[href="/correspondencia/memos"]')).not.toBeNull();
    expect(root.querySelector('a[href="/solicitudes"]')).toBeNull();
  });

  it('searches the current instructions', async () => {
    permissions.set(['Documentos:Ver', 'Tareas:Ver']);
    fixture.detectChanges();
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;
    const search = root.querySelector<HTMLInputElement>('.search-box input')!;
    search.value = 'contenido idéntico';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.textContent).toContain('Documentos');
    expect(root.textContent).not.toContain('Asigna trabajo');
  });

  it('uses the shared light and dark surfaces without changing its layout', () => {
    const html = document.documentElement;
    const previousTheme = html.getAttribute('data-theme');
    const root: HTMLElement = fixture.nativeElement;
    const card = root.querySelector<HTMLElement>('.guide-card')!;
    const reference = document.createElement('div');
    reference.style.backgroundColor = 'var(--surface-panel)';
    root.appendChild(reference);
    try {
      html.setAttribute('data-theme', 'light');
      const light = getComputedStyle(card).backgroundColor;
      expect(light).toBe(getComputedStyle(reference).backgroundColor);
      html.setAttribute('data-theme', 'dark');
      expect(getComputedStyle(card).backgroundColor).toBe(getComputedStyle(reference).backgroundColor);
      expect(getComputedStyle(card).backgroundColor).not.toBe(light);
    } finally {
      reference.remove();
      if (previousTheme === null) html.removeAttribute('data-theme');
      else html.setAttribute('data-theme', previousTheme);
    }
  });
});
