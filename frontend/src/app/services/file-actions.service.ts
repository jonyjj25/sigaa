import { Injectable, InjectionToken, inject } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { Capacitor } from '@capacitor/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';

export interface NativeFilePort {
  native(): boolean;
  write(path: string, data: string): Promise<{ uri: string }>;
  share(uri: string, name: string): Promise<unknown>;
}
export const NATIVE_FILE_PORT = new InjectionToken<NativeFilePort>('NativeFilePort', {
  providedIn: 'root', factory: () => ({
    native: () => Capacitor.isNativePlatform(),
    write: (path, data) => Filesystem.writeFile({ path, data, directory: Directory.Cache, recursive: true }),
    share: (uri, name) => Share.share({ files: [uri], title: name, dialogTitle: 'Abrir o guardar archivo' }),
  }),
});

/** Los archivos privados nunca se abren en una URL pública ni pierden su autenticación. */
@Injectable({ providedIn: 'root' })
export class FileActionsService {
  private readonly port = inject(NATIVE_FILE_PORT);
  private readonly toast = inject(ToastController);
  get native(): boolean { return this.port.native(); }

  async save(blob: Blob, filename: string): Promise<boolean> {
    return this.deliver(blob, filename, false);
  }

  async open(blob: Blob, filename: string): Promise<boolean> {
    return this.deliver(blob, filename, true);
  }

  async saveLocalUrl(url: string, filename: string): Promise<boolean> {
    try {
      if (!/^(blob:|data:(image|application)\/)/.test(url)) throw new Error('Recurso local inválido');
      return await this.save(await (await fetch(url)).blob(), filename);
    } catch { await this.showError(); return false; }
  }

  private async deliver(blob: Blob, filename: string, open: boolean): Promise<boolean> {
    try {
      let name = filename.replace(/[\\/\u0000-\u001f<>:"|?*]/g, '_') || 'archivo';
      while (new TextEncoder().encode(name).length > 180) name = Array.from(name).slice(1).join('');
      if (name === '.' || name === '..') name = 'archivo';
      if (this.native) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result).split(',')[1]);
          reader.onerror = () => reject(new Error('No se pudo leer el archivo'));
          reader.readAsDataURL(blob);
        });
        const file = await this.port.write(`sigaa-exports/${crypto.randomUUID()}/${name}`, data);
        await this.port.share(file.uri, name);
      } else {
        const url = URL.createObjectURL(blob);
        try {
          if (open) window.open(url, '_blank', 'noopener,noreferrer');
          else {
            const link = document.createElement('a'); link.href = url; link.download = name; link.click();
          }
        } finally {
          if (open) window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
          else URL.revokeObjectURL(url);
        }
      }
      return true;
    } catch (error) {
      if (!(error instanceof Error && /cancel/i.test(error.message))) await this.showError();
      return false;
    }
  }

  private async showError(): Promise<void> {
    const notice = await this.toast.create({ message: 'No se pudo abrir o guardar el archivo. Intenta nuevamente.', duration: 5000, color: 'danger' });
    await notice.present();
  }
}
