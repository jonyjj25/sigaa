import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApiService, mensajeErrorApi } from './api.service';
import { AuthService } from './auth.service';

describe('ApiService', () => {
  let service: ApiService;
  let http: HttpTestingController;
  const auth = { getToken: jasmine.createSpy('getToken') };

  beforeEach(() => {
    auth.getToken.calls.reset();
    auth.getToken.and.returnValue('jwt-test');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ]
    });
    service = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the public QR base URL and falls back to the current origin', () => {
    http.expectOne('/api/config').flush({ qrBaseUrl: 'https://sigaa.institucion' });
    expect(service.qrBaseUrl()).toBe('https://sigaa.institucion');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ]
    });
    const fallback = TestBed.inject(ApiService);
    const fallbackHttp = TestBed.inject(HttpTestingController);
    fallbackHttp.expectOne('/api/config').flush('error', { status: 500, statusText: 'Error' });
    expect(fallback.qrBaseUrl()).toBe(window.location.origin);
    fallbackHttp.verify();
  });

  it('sends authenticated JSON requests and emits data changes for mutations', () => {
    http.expectOne('/api/config').flush({});
    let changes = 0;
    service.dataChanged$.subscribe(() => changes++);

    service.get<{ ok: boolean }>('/api/documentos').subscribe(value => expect(value.ok).toBeTrue());
    const getRequest = http.expectOne('/api/documentos');
    expect(getRequest.request.withCredentials).toBeTrue();
    expect(getRequest.request.headers.get('Authorization')).toBe('Bearer jwt-test');
    expect(getRequest.request.headers.get('Content-Type')).toBe('application/json');
    getRequest.flush({ ok: true });

    service.post('/api/actas', { titulo: 'Acta' }).subscribe();
    http.expectOne('/api/actas').flush({});
    service.put('/api/reuniones/1', { titulo: 'Reunión' }).subscribe();
    http.expectOne('/api/reuniones/1').flush({});
    service.patch('/api/documentos/1/solicitar-aprobacion', { aprobadorId: 2 }).subscribe();
    http.expectOne('/api/documentos/1/solicitar-aprobacion').flush({});
    service.delete('/api/asistencia/1').subscribe();
    http.expectOne('/api/asistencia/1').flush({});

    expect(changes).toBe(4);
  });

  it('does not force content type for multipart uploads and downloads blobs', () => {
    http.expectOne('/api/config').flush({});
    const form = new FormData();
    form.append('file', new Blob(['content']), 'documento.txt');

    service.post('/api/documentos/1/archivo', form).subscribe();
    const upload = http.expectOne('/api/documentos/1/archivo');
    expect(upload.request.headers.has('Content-Type')).toBeFalse();
    upload.flush({});

    service.put('/api/actas/1/archivo', form).subscribe();
    const replace = http.expectOne('/api/actas/1/archivo');
    expect(replace.request.headers.has('Content-Type')).toBeFalse();
    replace.flush({});

    const blob = new Blob(['backup']);
    service.getBlob('/api/backups/1/download').subscribe(value => expect(value.size).toBe(blob.size));
    const download = http.expectOne('/api/backups/1/download');
    expect(download.request.responseType).toBe('blob');
    download.flush(blob);
  });

  ['/api/documentos/10/descargar', '/api/actas/20/adjuntos/21/descargar', '/api/reportes/30/descargar-archivo/0']
    .forEach(endpoint => {
      it(`conserva Bearer y cookies en la descarga privada ${endpoint}`, () => {
        http.expectOne('/api/config').flush({});
        service.getBlob(endpoint).subscribe();
        const request = http.expectOne(endpoint);
        expect(request.request.responseType).toBe('blob');
        expect(request.request.withCredentials).toBeTrue();
        expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-test');
        request.flush(new Blob(['fixture sintético']));
      });

      it(`permite autenticación por cookie sin añadir Bearer en ${endpoint}`, () => {
        http.expectOne('/api/config').flush({});
        auth.getToken.and.returnValue(null);
        service.getBlob(endpoint).subscribe();
        const request = http.expectOne(endpoint);
        expect(request.request.withCredentials).toBeTrue();
        expect(request.request.headers.has('Authorization')).toBeFalse();
        request.flush(new Blob(['fixture sintético']));
      });
    });

  it('permite POST de lectura sin anunciar una mutación ni perder la autenticación', () => {
    http.expectOne('/api/config').flush({});
    let changes = 0;
    service.dataChanged$.subscribe(() => changes++);
    service.post('/api/notas/vista-previa', { notaId: 10 }, { notifyChanges: false }).subscribe();
    const request = http.expectOne('/api/notas/vista-previa');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-test');
    request.flush({ paginas: [] });
    expect(changes).toBe(0);
  });

  it('omits authorization when no compatibility token exists', () => {
    auth.getToken.and.returnValue(null);
    http.expectOne('/api/config').flush({});
    service.get('/api/reuniones').subscribe();
    const request = http.expectOne('/api/reuniones');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush([]);
  });
});

describe('mensajeErrorApi', () => {
  it('extracts the supported backend message formats', () => {
    expect(mensajeErrorApi({ error: { message: 'Datos inválidos' } }, 'Error')).toBe('Datos inválidos');
    expect(mensajeErrorApi({ error: { error: 'Sin permiso' } }, 'Error')).toBe('Sin permiso');
  });

  it('uses a safe fallback for network errors and unexpected payloads', () => {
    for (const response of [null, undefined, 'error', { error: null }, { error: 'HTML' },
      { error: { message: {}, error: [] } }, { error: { message: '' } }]) {
      expect(mensajeErrorApi(response, 'Intenta nuevamente')).toBe('Intenta nuevamente');
    }
  });
});
