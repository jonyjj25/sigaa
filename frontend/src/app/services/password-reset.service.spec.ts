import { PasswordResetService } from './password-reset.service';

describe('PasswordResetService', () => {
  let service: PasswordResetService;

  beforeEach(() => service = new PasswordResetService());

  it('sends every password recovery step with credentials', async () => {
    const fetchSpy = spyOn(window, 'fetch').and.callFake(() => Promise.resolve(new Response(JSON.stringify({
      message: 'ok', resetToken: 'reset-token',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })));

    expect((await service.requestPasswordReset('ana@example.test')).ok).toBeTrue();
    await service.resendPasswordResetCode('ana@example.test');
    await service.verifyPasswordResetCode('ana@example.test', '123456');
    const reset = await service.resetPassword('reset-token', 'NuevaClave123!');

    expect(reset.resetToken).toBe('reset-token');
    expect(fetchSpy).toHaveBeenCalledTimes(4);
    const options = fetchSpy.calls.mostRecent().args[1] as RequestInit;
    expect(options.credentials).toBe('include');
    expect(options.method).toBe('POST');
  });

  it('returns the API error and handles an unreachable server', async () => {
    const fetchSpy = spyOn(window, 'fetch');
    fetchSpy.and.resolveTo(new Response(JSON.stringify({ error: 'Código inválido' }), {
      status: 400, headers: { 'Content-Type': 'application/json' },
    }));
    const rejected = await service.verifyPasswordResetCode('ana@example.test', 'bad');
    expect(rejected).toEqual(jasmine.objectContaining({ ok: false, status: 400, message: 'Código inválido' }));

    fetchSpy.and.rejectWith(new Error('offline'));
    const offline = await service.requestPasswordReset('ana@example.test');
    expect(offline).toEqual({ ok: false, status: 0, message: 'No se pudo conectar con el servidor.' });
  });
});
