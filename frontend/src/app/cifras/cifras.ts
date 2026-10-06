import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import { EnteroPipe, FechaPipe, eurosCompacto } from '../nucleo/formato';
import { EstadoDeIngesta, Estadisticas } from '../nucleo/modelo';
import { Barra, GraficaBarras } from './grafica-barras';
import { GraficaMeses } from './grafica-meses';

/** Las cifras: lo que hay en plazo ahora, por tipo y por comunidad, y la evolución de las publicaciones. */
@Component({
  selector: 'rl-cifras',
  imports: [GraficaBarras, GraficaMeses, EnteroPipe, FechaPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cifras.html',
  styleUrl: './cifras.css',
})
export class Cifras {
  private readonly api = inject(CONFIGURACION).api;

  protected readonly estadisticas = httpResource<Estadisticas>(() => `${this.api}/v1/estadisticas`);
  protected readonly ingesta = httpResource<EstadoDeIngesta>(() => `${this.api}/v1/ingesta/estado`);
  protected readonly error = computed(() =>
    this.estadisticas.error() ? mensajeDeError(this.estadisticas.error()) : null,
  );

  protected readonly importeEnPlazo = computed(() =>
    this.estadisticas.hasValue() ? eurosCompacto(this.estadisticas.value().importeEnPlazo) : '',
  );
  protected readonly porTipo = computed<Barra[]>(() =>
    this.estadisticas.hasValue()
      ? this.estadisticas.value().enPlazoPorTipo.map((g) => ({
          etiqueta: g.codigo.nombre,
          valor: g.licitaciones,
          detalle: eurosCompacto(g.importe) + ' sin IVA',
        }))
      : [],
  );
  protected readonly porComunidad = computed<Barra[]>(() =>
    this.estadisticas.hasValue()
      ? this.estadisticas.value().enPlazoPorComunidad.map((g) => ({
          etiqueta: g.codigo.nombre,
          valor: g.licitaciones,
          detalle: eurosCompacto(g.importe) + ' sin IVA',
        }))
      : [],
  );
  protected readonly ultimaIngesta = computed(() =>
    this.ingesta.hasValue()
      ? (this.ingesta.value().ultimas.find((i) => i.estado === 'COMPLETED') ?? null)
      : null,
  );
}
