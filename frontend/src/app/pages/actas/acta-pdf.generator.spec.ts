import { normalizePdfText, splitActaPdfItems } from './acta-pdf.generator';
import { ActaAcuerdo } from './actas.models';

describe('Acta PDF generator', () => {
  it('normalizes pasted bullets and unsupported punctuation', () => {
    const pasted = '  Primer punto\n• Segundo punto — “confirmado”';

    expect(normalizePdfText(pasted)).toBe('- Primer punto\n- Segundo punto - "confirmado"');
  });

  it('separates agreements from commitments without leaking the storage prefix', () => {
    const items: ActaAcuerdo[] = [
      {
        id: '1',
        descripcion: 'Aprobar la propuesta',
        responsable: 'Ana Pérez',
        fechaLimite: '',
        estado: 'Pendiente',
      },
      {
        id: '2',
        descripcion: '[Compromiso] Preparar el informe final',
        responsable: 'Luis Gómez',
        fechaLimite: '',
        estado: 'En Progreso',
      },
    ];

    const result = splitActaPdfItems(items);

    expect(result.acuerdos.map(item => item.descripcion)).toEqual(['Aprobar la propuesta']);
    expect(result.compromisos.map(item => item.descripcion)).toEqual(['Preparar el informe final']);
  });
});
