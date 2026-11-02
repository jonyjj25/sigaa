export function canonicalRole(name: string): string {
  const value = (name || '').trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const aliases: Record<string, string> = {
    director: 'Director', directora: 'Director',
    subdirector: 'Subdirector', subdirectora: 'Subdirector',
    secretaria: 'Secretaria', secretario: 'Secretaria',
    superadmin: 'Superadmin', administrador: 'Administrador',
    'jefe de unidad': 'Jefe de Unidad', 'jefa de unidad': 'Jefe de Unidad', miembro: 'Miembro',
  };
  return aliases[value] || (name || '').trim();
}

export function isGlobalRole(name: string): boolean {
  return ['Superadmin', 'Director', 'Subdirector', 'Secretaria'].includes(canonicalRole(name));
}

export function isUnitRole(name: string): boolean {
  return ['Jefe de Unidad', 'Miembro'].includes(canonicalRole(name));
}

export function roleLabel(name: string): string {
  const labels: Record<string, string> = {
    Director: 'Director / Directora', Subdirector: 'Subdirector / Subdirectora',
    Secretaria: 'Secretaría', 'Jefe de Unidad': 'Jefe / Jefa de Unidad',
  };
  return labels[canonicalRole(name)] || name;
}
