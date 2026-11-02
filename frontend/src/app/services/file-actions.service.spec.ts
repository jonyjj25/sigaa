import { TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular';
import { FileActionsService, NATIVE_FILE_PORT, NativeFilePort } from './file-actions.service';

describe('FileActionsService', () => {
  let service: FileActionsService;
  let port: jasmine.SpyObj<NativeFilePort>;
  let present: jasmine.Spy;
  beforeEach(() => {
    port = jasmine.createSpyObj('NativeFilePort', ['native', 'write', 'share']);
    port.native.and.returnValue(true);
    port.write.and.resolveTo({ uri: 'file:///cache/private.pdf' });
    port.share.and.resolveTo({}); present = jasmine.createSpy().and.resolveTo();
    TestBed.configureTestingModule({ providers: [
      { provide: NATIVE_FILE_PORT, useValue: port },
      { provide: ToastController, useValue: { create: jasmine.createSpy().and.resolveTo({ present }) } },
    ] });
    service = TestBed.inject(FileActionsService);
  });
  it('writes actual bytes to a private cache and shares the resulting native URI', async () => {
    expect(await service.save(new Blob(['PDF']), '../acta.pdf')).toBeTrue();
    expect(port.write).toHaveBeenCalledWith(jasmine.stringMatching(/^sigaa-exports\/[^/]+\/\.\._acta\.pdf$/), btoa('PDF'));
    expect(port.share).toHaveBeenCalledWith('file:///cache/private.pdf', '.._acta.pdf');
  });
  it('reports storage failure without trying to share a missing file', async () => {
    port.write.and.rejectWith(new Error('storage unavailable'));
    expect(await service.open(new Blob(['PDF']), 'acta.pdf')).toBeFalse();
    expect(port.share).not.toHaveBeenCalled(); expect(present).toHaveBeenCalled();
  });
  it('does not report a user cancellation as a storage failure', async () => {
    port.share.and.rejectWith(new Error('Share cancelled'));
    expect(await service.save(new Blob(['PDF']), 'acta.pdf')).toBeFalse(); expect(present).not.toHaveBeenCalled();
  });

  it('bounds Unicode filenames in bytes and neutralizes dot-only paths', async () => {
    expect(await service.save(new Blob(['PDF']), `${'😀'.repeat(100)}.pdf`)).toBeTrue();
    const parts = port.write.calls.mostRecent().args[0].split('/');
    const name = parts[parts.length - 1];
    expect(new TextEncoder().encode(name).length).toBeLessThanOrEqual(180);
    expect(name.endsWith('.pdf')).toBeTrue();
    await service.save(new Blob(['PDF']), '..');
    expect(port.share.calls.mostRecent().args[1]).toBe('archivo');
  });
});
