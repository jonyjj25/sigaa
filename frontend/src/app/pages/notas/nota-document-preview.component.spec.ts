import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { NotaDocumentPreviewComponent, NotaPreviewData } from './nota-document-preview.component';
import { insertarSaltoPagina } from './notas.models';

describe('Vista paginada institucional de Notas', () => {
  let fixture: ComponentFixture<NotaDocumentPreviewComponent>;
  let api: jasmine.SpyObj<ApiService>;
  const page = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5sUAAAAASUVORK5CYII=';
  const filename = 'NOTA-OAT-015-2026-COORDINACION-INSTITUCIONAL.DOCX';
  const data: NotaPreviewData = {
    notaId: '10', numeroId: '15', institucionId: '3', fechaOficial: '2026-10-06',
    estado: 'BORRADOR', fecha: '6 de octubre del 2026', numero: 'OAT-015-2026',
    asunto: 'Coordinación institucional', destinatarioNombre: 'Juan Pérez',
    destinatarioCargo: 'Jefe de operaciones', institucion: 'Institución',
    contenido: 'Texto editado antes de oficializar.\nSegundo párrafo.',
    firmanteNombre: 'Firma del cliente', firmanteCargo: 'Cargo del cliente', firmanteInstitucion: 'Institución del cliente',
  };

  beforeEach(() => {
    api = jasmine.createSpyObj('ApiService', ['post', 'get', 'getBlob']);
    api.post.and.returnValue(of({ paginas: [page], totalPaginas: 1, nombreArchivo: filename }));
    api.get.and.returnValue(of({ paginas: [page], totalPaginas: 1, nombreArchivo: 'MEMO-PRUEBA.DOCX' }));
    TestBed.configureTestingModule({
      imports: [NotaDocumentPreviewComponent],
      providers: [{ provide: ApiService, useValue: api }],
    });
    fixture = TestBed.createComponent(NotaDocumentPreviewComponent);
  });

  it('envía las ediciones actuales y deja al backend obtener la firma y los datos oficiales', () => {
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    expect(api.post).toHaveBeenCalledWith('/api/notas/vista-previa', {
      notaId: '10', numeroId: '15', institucionId: '3', fechaOficial: '2026-10-06',
      asunto: data.asunto, destinatarioNombre: data.destinatarioNombre,
      destinatarioCargo: data.destinatarioCargo, contenido: data.contenido,
    }, { notifyChanges: false });
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('.document-sheet').length).toBe(1);
    expect(root.querySelector('.page-count')?.textContent).toContain('1 página');
    expect(root.querySelector('footer')?.textContent).toContain(filename);
    expect(root.querySelector('.preview-scroll')?.textContent).not.toContain(data.asunto);
  });

  it('reutiliza la vista paginada para Memos mediante su endpoint autorizado', () => {
    fixture.componentRef.setInput('data', { tipoDocumento: 'memo', memoId: '7', numero: 'M-007', estado: 'OFICIALIZADA' });
    fixture.detectChanges();
    expect(api.get).toHaveBeenCalledOnceWith('/api/memos/7/vista-previa'); expect(api.post).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('h2').textContent).toContain('memo');
    expect(fixture.nativeElement.querySelectorAll('.document-sheet').length).toBe(1);
  });

  it('no ofrece exportación sin URL autorizada ni para borradores o documentos anulados', () => {
    fixture.componentRef.setInput('data', data); fixture.componentRef.setInput('pdfUrl', '/api/notas/10/documento.pdf');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.preview-actions button').length).toBe(1);
    expect(api.getBlob).not.toHaveBeenCalled();
  });

  it('descarga el PDF del mismo documento sin generar una versión HTML diferente', fakeAsync(() => {
    api.getBlob.and.returnValue(of(new Blob(['%PDF-prueba'], { type: 'application/pdf' })));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test-only'); const revocar = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    fixture.componentRef.setInput('data', { ...data, estado: 'OFICIALIZADA' });
    fixture.componentRef.setInput('pdfUrl', '/api/notas/10/documento.pdf'); fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('.preview-actions button')[0].click();
    expect(api.getBlob).toHaveBeenCalledOnceWith('/api/notas/10/documento.pdf'); expect(click).toHaveBeenCalledTimes(1);
    tick(60_000); expect(revocar).toHaveBeenCalledWith('blob:test-only');
  }));

  it('abre para imprimir el PDF del memo y conserva el formato del servidor', fakeAsync(() => {
    api.getBlob.and.returnValue(of(new Blob(['%PDF-prueba'], { type: 'application/pdf' })));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test-only'); spyOn(URL, 'revokeObjectURL');
    const ventana = { opener: {} as object | null, closed: false, print: jasmine.createSpy('print'),
      close: jasmine.createSpy('close'), addEventListener: jasmine.createSpy('addEventListener'), location: { replace: jasmine.createSpy('replace') } };
    spyOn(window, 'open').and.returnValue(ventana as unknown as Window);
    fixture.componentRef.setInput('data', { tipoDocumento: 'memo', memoId: '7', numero: 'M-007', estado: 'OFICIALIZADA' });
    fixture.componentRef.setInput('pdfUrl', '/api/memos/7/documento.pdf'); fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('.preview-actions button')[1].click();
    expect(api.getBlob).toHaveBeenCalledOnceWith('/api/memos/7/documento.pdf');
    expect(ventana.opener).toBeNull(); expect(ventana.location.replace).toHaveBeenCalledWith('blob:test-only');
    const cargar = ventana.addEventListener.calls.mostRecent().args[1] as EventListener; cargar(new Event('load'));
    expect(ventana.print).toHaveBeenCalledTimes(1); tick(60_000);
  }));

  it('conserva la vista y muestra el rechazo real de una descarga', () => {
    api.getBlob.and.returnValue(throwError(() => ({ status: 403, error: { message: 'Sin acceso al documento' } })));
    fixture.componentRef.setInput('data', { ...data, estado: 'OFICIALIZADA' });
    fixture.componentRef.setInput('pdfUrl', '/api/notas/10/documento.pdf'); fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('.preview-actions button')[0].click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.document-sheet').length).toBe(1);
    expect(fixture.nativeElement.querySelector('footer [role=alert]').textContent).toContain('Sin acceso al documento');
  });

  it('muestra páginas Carta separadas con el total retornado por el PDF real', () => {
    api.post.and.returnValue(of({ paginas: [page, page, page], totalPaginas: 3, nombreArchivo: filename }));
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('figure.document-page').length).toBe(3);
    expect(root.querySelector('.page-count')?.textContent).toContain('3 páginas');
    expect(root.querySelector('.page-count')?.textContent).toContain('8½ × 11');
    expect(root.querySelectorAll('figcaption')[2].textContent).toContain('Página 3 de 3');
    expect(root.querySelector('img')?.getAttribute('src')).toBe(page);
  });

  it('cancela la vista anterior cuando cambian los datos para no mostrar contenido desactualizado', fakeAsync(() => {
    const previous = new Subject<unknown>();
    const current = new Subject<unknown>();
    api.post.and.returnValues(previous, current);
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    fixture.componentRef.setInput('data', { ...data, contenido: 'Texto nuevo' }); fixture.detectChanges();
    tick(200);
    previous.next({ paginas: [page, page], totalPaginas: 2, nombreArchivo: 'Anterior.docx' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('img').length).toBe(0);
    current.next({ paginas: [page], totalPaginas: 1, nombreArchivo: filename });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('img').length).toBe(1);
    expect(api.post.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ contenido: 'Texto nuevo' }));
  }));

  it('evita solicitudes por referencias nuevas o por campos de presentación que no alteran el documento', () => {
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    const image = fixture.nativeElement.querySelector('img');
    fixture.componentRef.setInput('data', { ...data, firmanteNombre: 'Otra etiqueta' }); fixture.detectChanges();
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('img')).toBe(image);
  });

  it('agrupa cambios rápidos y envía solamente el contenido más reciente', fakeAsync(() => {
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    fixture.componentRef.setInput('data', { ...data, contenido: 'Primer cambio' }); fixture.detectChanges();
    tick(100);
    fixture.componentRef.setInput('data', { ...data, contenido: 'Segundo cambio' }); fixture.detectChanges();
    tick(199);
    expect(api.post).toHaveBeenCalledTimes(1);
    tick(1); fixture.detectChanges();
    expect(api.post).toHaveBeenCalledTimes(2);
    expect(api.post.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ contenido: 'Segundo cambio' }));
  }));

  it('identifica una nota anulada y vuelve a consultar al cambiar el estado', fakeAsync(() => {
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    fixture.componentRef.setInput('data', { ...data, estado: 'ANULADA' }); fixture.detectChanges();
    tick(200); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.preview-status-anulada')?.textContent).toBe('NOTA ANULADA');
    expect(api.post).toHaveBeenCalledTimes(2);
    fixture.destroy();
    fixture = TestBed.createComponent(NotaDocumentPreviewComponent);
    fixture.componentRef.setInput('data', { ...data, estado: 'ANULADA' }); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.preview-status-anulada')?.textContent).toBe('NOTA ANULADA');
  }));

  it('muestra el fallo real y permite reintentar sin producir una vista diferente', () => {
    api.post.and.returnValue(throwError(() => ({ status: 503, error: { message: 'LibreOffice no disponible' } })));
    fixture.componentRef.setInput('data', data); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('[role=alert]')?.textContent).toContain('LibreOffice no disponible');
    expect(root.querySelectorAll('img').length).toBe(0);
    api.post.and.returnValue(of({ paginas: [page], totalPaginas: 1, nombreArchivo: filename }));
    root.querySelector<HTMLButtonElement>('[role=alert] button')?.click(); fixture.detectChanges();
    expect(root.querySelectorAll('img').length).toBe(1);
  });

  it('permite insertar un salto de página en la selección conservando el resto del texto editable', () => {
    const editor = document.createElement('textarea');
    editor.value = 'Antes. Reemplazar Después.';
    editor.setSelectionRange(7, 18);
    expect(insertarSaltoPagina(editor)).toBe('Antes. \n\f\nDespués.');
  });
});
