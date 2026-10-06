import {
  entero,
  etiquetaDeEstado,
  euros,
  fecha,
  mes,
  plazoRestante,
  plazoUrgente,
} from './formato';

// Los espacios de Intl son de no separación: se normalizan para comparar.
const normal = (texto: string) => texto.replace(/\s/g, ' ');

describe('formato', () => {
  it('formatea euros al estilo español', () => {
    expect(normal(euros(4198877))).toBe('4.198.877 €');
    expect(normal(euros(15493.2, true))).toBe('15.493,20 €');
    expect(euros(null)).toBe('—');
  });

  it('formatea enteros con separador de miles', () => {
    expect(entero(42213)).toBe('42.213');
    expect(entero(undefined)).toBe('—');
  });

  it('una fecha sin hora no cambia de día por la zona horaria', () => {
    expect(fecha('2026-10-05')).toBe('5 oct 2026');
  });

  it('las horas se enseñan en hora peninsular', () => {
    expect(normal(fecha('2026-10-20T21:59:00Z', true))).toContain('20 oct 2026');
    expect(normal(fecha('2026-10-20T21:59:00Z', true))).toContain('23:59');
  });

  it('el mes de las gráficas', () => {
    expect(mes('2026-09')).toMatch(/sept?\.? 26/);
  });

  it('dice cuánto queda de plazo', () => {
    const ahora = new Date('2026-10-06T10:00:00+02:00');
    expect(plazoRestante('2026-10-26T23:59:00+02:00', ahora)).toBe('Quedan 20 días');
    expect(plazoRestante('2026-10-07T12:00:00+02:00', ahora)).toBe('Queda 1 día');
    expect(plazoRestante('2026-10-06T15:30:00+02:00', ahora)).toBe('Cierra en 5 h');
    expect(plazoRestante('2026-10-06T10:20:00+02:00', ahora)).toBe('Cierra en menos de una hora');
    expect(plazoRestante('2026-10-01T10:00:00+02:00', ahora)).toBe('Plazo cerrado');
    expect(plazoRestante(null, ahora)).toBe('Sin plazo publicado');
  });

  it('matiza el estado publicada con el plazo real', () => {
    const ahora = new Date('2026-10-06T10:00:00+02:00');
    expect(
      etiquetaDeEstado('PUB', 'Publicada', '2026-10-20T23:59:00+02:00', false, ahora).texto,
    ).toBe('En plazo');
    expect(
      etiquetaDeEstado('PUB', 'Publicada', '2026-10-01T23:59:00+02:00', false, ahora).texto,
    ).toBe('Plazo cerrado');
    expect(etiquetaDeEstado('PUB', 'Publicada', null, false, ahora).texto).toBe('Publicada');
    expect(etiquetaDeEstado('ADJ', 'Adjudicada', null, false, ahora).texto).toBe('Adjudicada');
    expect(
      etiquetaDeEstado('PUB', 'Publicada', '2026-10-20T23:59:00+02:00', true, ahora).texto,
    ).toBe('Anulada');
  });

  it('marca como urgente lo que cierra en menos de tres días', () => {
    const ahora = new Date('2026-10-06T10:00:00+02:00');
    expect(plazoUrgente('2026-10-08T10:00:00+02:00', ahora)).toBe(true);
    expect(plazoUrgente('2026-10-20T10:00:00+02:00', ahora)).toBe(false);
    expect(plazoUrgente('2026-10-01T10:00:00+02:00', ahora)).toBe(false);
  });
});
