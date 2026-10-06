import { httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Paginacion } from '../compartido/paginacion';
import { TarjetaLicitacion } from '../compartido/tarjeta-licitacion';
import { mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import {
  Filtro,
  ORDENES,
  Orden,
  consultaDesdeFiltro,
  criteriosActivos,
  filtroDesdeUrl,
  urlDesdeFiltro,
} from '../nucleo/filtro';
import { EnteroPipe } from '../nucleo/formato';
import { LicitacionResumen, Pagina } from '../nucleo/modelo';
import { PanelFiltros } from './panel-filtros';

/** La portada: buscar licitaciones. Todo el estado de la búsqueda está en la URL. */
@Component({
  selector: 'rl-busqueda',
  imports: [PanelFiltros, TarjetaLicitacion, Paginacion, RouterLink, EnteroPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './busqueda.html',
  styleUrl: './busqueda.css',
})
export class Busqueda {
  private readonly router = inject(Router);
  private readonly api = inject(CONFIGURACION).api;
  private readonly parametros = toSignal(inject(ActivatedRoute).queryParams, { requireSync: true });

  protected readonly ordenes = ORDENES;
  protected readonly filtro = computed(() => filtroDesdeUrl(this.parametros()));
  protected readonly criterios = computed(() => criteriosActivos(this.filtro()));

  protected readonly resultados = httpResource<Pagina<LicitacionResumen>>(() => ({
    url: `${this.api}/v1/licitaciones`,
    params: consultaDesdeFiltro(this.filtro()),
  }));
  protected readonly error = computed(() =>
    this.resultados.error() ? mensajeDeError(this.resultados.error()) : null,
  );

  /** Los parámetros para crear una alerta con esta misma búsqueda. */
  protected readonly paraAlerta = computed(() => urlDesdeFiltro({ ...this.filtro(), pagina: 1 }));

  /** En pantallas anchas los filtros están siempre a la vista; en el móvil, plegados hasta que se piden. */
  protected readonly filtrosAbiertos = signal(true);

  constructor() {
    if (typeof window.matchMedia !== 'function') {
      return;
    }
    const ancho = window.matchMedia('(min-width: 901px)');
    this.filtrosAbiertos.set(ancho.matches);
    const alCambiar = (e: MediaQueryListEvent) => this.filtrosAbiertos.set(e.matches);
    ancho.addEventListener('change', alCambiar);
    inject(DestroyRef).onDestroy(() => ancho.removeEventListener('change', alCambiar));
  }

  protected aplicar(cambios: Partial<Filtro>): void {
    const nuevo = { ...this.filtro(), pagina: 1, ...cambios };
    void this.router.navigate([], { queryParams: urlDesdeFiltro(nuevo) });
  }

  protected buscar(evento: Event, texto: string): void {
    evento.preventDefault();
    this.aplicar({ q: texto });
  }

  protected ordenar(orden: string): void {
    this.aplicar({ orden: orden as Orden });
  }

  protected irAPagina(pagina: number): void {
    this.aplicar({ pagina });
    document.getElementById('resultados')?.focus();
  }
}
