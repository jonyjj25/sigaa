import { FileActionsService } from '../../services/file-actions.service';
import { ChangeDetectionStrategy, Component, DestroyRef, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { Subscription, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService, mensajeErrorApi } from '../../services/api.service';

export interface NotaPreviewData {
  tipoDocumento?: 'nota';
  notaId?: string;
  numeroId?: string;
  institucionId?: string;
  institucionDefinida?: boolean;
  fechaOficial?: string;
  estado: 'BORRADOR' | 'OFICIALIZADA' | 'ANULADA';
  fecha: string;
  numero: string;
  asunto: string;
  destinatarioNombre: string;
  destinatarioCargo: string;
  institucion: string;
  contenido: string;
  firmanteNombre: string;
  firmanteCargo: string;
  firmanteInstitucion: string;
}

export interface MemoPreviewData {
  tipoDocumento: 'memo';
  memoId: string;
  estado: 'OFICIALIZADA' | 'ANULADA';
  numero: string;
}

@Component({
  selector: 'app-nota-document-preview',
  standalone: true,
  templateUrl: './nota-document-preview.component.html',
  styleUrls: ['./nota-document-preview.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotaDocumentPreviewComponent implements OnChanges {
  private readonly fileActions = inject(FileActionsService);
  @Input({ required: true }) data!: NotaPreviewData | MemoPreviewData;
  @Input() pdfUrl?: string;
  @Output() closed = new EventEmitter<void>();
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private requestKey?: string;
  protected readonly paginas = signal<string[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly nombreArchivo = signal('');
  protected readonly preparandoDocumento = signal(false);
  protected readonly errorDocumento = signal('');
  protected get esMemo(): boolean { return this.data.tipoDocumento === 'memo'; }

  protected obtenerPdf(imprimir = false): void {
    if (!this.pdfUrl || this.data.estado !== 'OFICIALIZADA' || this.preparandoDocumento()) return;
    this.errorDocumento.set('');
    const nombre = this.nombreArchivo().replace(/\.[^.]+$/, '.PDF') || 'DOCUMENTO.PDF';
    const ventana = imprimir && !this.fileActions.native ? window.open('', '_blank') : null;
    if (imprimir && !this.fileActions.native && !ventana) { this.errorDocumento.set('Permite abrir ventanas para imprimir el documento.'); return; }
    if (ventana) ventana.opener = null;
    this.preparandoDocumento.set(true);
    this.api.getBlob(this.pdfUrl).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: archivo => {
        this.preparandoDocumento.set(false);
        if (this.fileActions.native) { void this.fileActions.save(archivo, nombre); return; }
        const url = URL.createObjectURL(archivo);
        if (ventana) {
          if (ventana.closed) { URL.revokeObjectURL(url); return; }
          ventana.addEventListener('load', () => {
            try { ventana.print(); }
            catch { this.errorDocumento.set('El PDF está abierto. Usa Imprimir en esa ventana.'); }
          }, { once: true });
          ventana.location.replace(url);
        } else {
          const enlace = document.createElement('a'); enlace.href = url;
          enlace.download = nombre;
          enlace.click();
        }
        window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: error => {
        ventana?.close(); this.preparandoDocumento.set(false);
        this.errorDocumento.set(mensajeErrorApi(error, 'No se pudo obtener el PDF. Intenta nuevamente.'));
      },
    });
  }

  ngOnChanges(): void { this.solicitarVistaPrevia(); }

  protected cargar(): void { this.solicitarVistaPrevia(true); }

  private solicitarVistaPrevia(reintentar = false): void {
    const data = this.data;
    const memo = data.tipoDocumento === 'memo';
    const payload = memo ? { memoId: data.memoId } : {
      notaId: data.notaId || null, numeroId: data.numeroId || null,
      institucionId: data.institucionId || null, fechaOficial: data.fechaOficial || null,
      asunto: data.asunto, destinatarioNombre: data.destinatarioNombre,
      destinatarioCargo: data.destinatarioCargo, contenido: data.contenido,
      ...(data.institucionDefinida ? { institucionDefinida: true } : {}),
    };
    // Ignorar cambios de referencia o de campos de presentación que no cambian el documento.
    const key = JSON.stringify([payload, this.data.estado]);
    if (!reintentar && key === this.requestKey) return;
    const aplazar = !reintentar && this.requestKey !== undefined;
    this.requestKey = key;
    this.request?.unsubscribe();
    this.error.set(''); this.loading.set(true);
    type Paginas = { paginas: string[]; totalPaginas: number; nombreArchivo: string };
    const consultar = () => memo
      ? this.api.get<Paginas>(`/api/memos/${encodeURIComponent(data.memoId)}/vista-previa`)
      : this.api.post<Paginas>('/api/notas/vista-previa', payload, { notifyChanges: false });
    // La primera apertura es inmediata; agrupar únicamente cambios rápidos posteriores.
    const source = aplazar ? timer(200).pipe(switchMap(consultar)) : consultar();
    this.request = source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: preview => {
        this.paginas.set(preview.paginas); this.nombreArchivo.set(preview.nombreArchivo); this.loading.set(false);
      },
      error: error => {
        this.paginas.set([]); this.nombreArchivo.set('');
        this.error.set(mensajeErrorApi(error, 'No se pudo generar la vista previa. Intenta nuevamente.'));
        this.loading.set(false);
      },
    });
  }
}
