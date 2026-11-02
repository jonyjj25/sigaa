import { canonicalRole, isGlobalRole, isUnitRole, roleLabel } from './institutional-roles';

describe('Institutional roles', () => {
  it('uses the same role for masculine and feminine institutional titles', () => {
    expect(canonicalRole(' Directora ')).toBe(canonicalRole('Director'));
    expect(canonicalRole('Subdirectora')).toBe(canonicalRole('Subdirector'));
    expect(canonicalRole('Secretaría')).toBe('Secretaria');
  });

  it('keeps institutional and unit assignments separate', () => {
    for (const name of ['Director', 'Subdirectora', 'Secretaría', 'Superadmin']) {
      expect(isGlobalRole(name)).toBeTrue();
      expect(isUnitRole(name)).toBeFalse();
    }
    for (const name of ['Jefe de Unidad', 'Jefa de Unidad', 'Miembro']) {
      expect(isUnitRole(name)).toBeTrue();
      expect(isGlobalRole(name)).toBeFalse();
    }
    expect(isGlobalRole('Personalizado')).toBeFalse();
    expect(isGlobalRole('Administrador')).toBeFalse();
    expect(isUnitRole('Personalizado')).toBeFalse();
  });

  it('shows inclusive institutional titles', () => {
    expect(roleLabel('Director')).toBe('Director / Directora');
    expect(roleLabel('Subdirectora')).toBe('Subdirector / Subdirectora');
    expect(roleLabel('Secretaria')).toBe('Secretaría');
  });
});
