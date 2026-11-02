import { routes } from './app.routes';

describe('application routes', () => {
  it('defines a fallback and keeps private modules behind AuthGuard', () => {
    const fallback = routes.find(route => route.path === '**');
    expect(fallback?.redirectTo).toBe('home');

    for (const path of ['dashboard', 'asistencia-qr', 'actas', 'inventario', 'reuniones', 'grupos', 'grupos/:id', 'tareas', 'correspondencia/notas', 'correspondencia/memos', 'reporte', 'documentos']) {
      const route = routes.find(item => item.path === path);
      expect(route?.canActivate?.length).withContext(path).toBeGreaterThan(0);
      expect(route?.loadComponent).withContext(path).toBeDefined();
    }

    expect(routes.find(item => item.path === 'notas')?.redirectTo).toBe('/correspondencia/notas');
    expect(routes.find(item => item.path === 'memos')?.redirectTo).toBe('/correspondencia/memos');

    const qrRoute = routes.find(item => item.path === 'asistencia-qr');
    expect(qrRoute?.data?.['permissionModule']).toBe('Configuracion QR');
    expect(qrRoute?.data?.['permissionAction']).toBe('Ver');

    const notaDetalle = routes.find(item => item.path === 'correspondencia/notas/:id');
    expect(notaDetalle?.data?.['permissionModule']).toBe('Notas');
    expect(routes.find(item => item.path === 'notas/:id')?.redirectTo).toBe('correspondencia/notas/:id');
  });
});
