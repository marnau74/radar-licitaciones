import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { SelectorCpv } from '../compartido/selector-cpv';
import { SelectorLugar } from '../compartido/selector-lugar';
import { ServicioDeCatalogos } from '../nucleo/catalogos';
import { CONFIGURACION } from '../nucleo/configuracion';
import { FILTRO_INICIAL, Filtro } from '../nucleo/filtro';
import { OrganoResumen } from '../nucleo/modelo';

/** Los estados en el orden de la vida de una licitación. */
const ORDEN_ESTADOS = ['PUB', 'EV', 'ADJ', 'RES', 'PRE', 'ANUL'];
/** Los tipos más habituales se enseñan siempre; el resto, en una lista desplegable. */
const TIPOS_PRINCIPALES = ['1', '2', '3'];

/** Los filtros de la búsqueda. Cada cambio se aplica al momento (salvo los números y fechas, al salir del campo). */
@Component({
  selector: 'rl-panel-filtros',
  imports: [SelectorCpv, SelectorLugar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './panel-filtros.html',
  styleUrl: './panel-filtros.css',
})
export class PanelFiltros {
  readonly filtro = input.required<Filtro>();
  readonly cambio = output<Partial<Filtro>>();

  protected readonly catalogos = inject(ServicioDeCatalogos);
  private readonly api = inject(CONFIGURACION).api;

  protected readonly estados = computed(() => {
    const todos = this.catalogos.catalogos.hasValue()
      ? this.catalogos.catalogos.value().estados
      : [];
    return [...todos].sort(
      (a, b) => ORDEN_ESTADOS.indexOf(a.codigo) - ORDEN_ESTADOS.indexOf(b.codigo),
    );
  });
  protected readonly tiposPrincipales = computed(() =>
    (this.catalogos.catalogos.hasValue() ? this.catalogos.catalogos.value().tiposContrato : [])
      .filter((t) => TIPOS_PRINCIPALES.includes(t.codigo))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')),
  );
  protected readonly otrosTipos = computed(() =>
    (this.catalogos.catalogos.hasValue()
      ? this.catalogos.catalogos.value().tiposContrato
      : []
    ).filter((t) => !TIPOS_PRINCIPALES.includes(t.codigo)),
  );
  protected readonly procedimientos = computed(() =>
    this.catalogos.catalogos.hasValue() ? this.catalogos.catalogos.value().procedimientos : [],
  );

  /** El órgano filtrado, para enseñar su nombre (en la URL solo va su número). */
  protected readonly organo = httpResource<OrganoResumen>(() => {
    const id = this.filtro().organo;
    return id === null ? undefined : `${this.api}/v1/organos/${id}`;
  });

  protected readonly hayFiltros = computed(() => {
    const f = this.filtro();
    return (
      JSON.stringify({ ...f, q: '', orden: 'relevancia', pagina: 1 }) !==
      JSON.stringify({ ...FILTRO_INICIAL })
    );
  });

  protected alternar(campo: 'estado' | 'tipo', codigo: string, marcado: boolean): void {
    const actuales = this.filtro()[campo];
    this.cambio.emit({
      [campo]: marcado ? [...actuales, codigo] : actuales.filter((c) => c !== codigo),
    });
  }

  protected numero(campo: 'importeMin' | 'importeMax', valor: string): void {
    const n = valor.trim() === '' ? null : Number(valor);
    if (n === null || (Number.isFinite(n) && n >= 0)) {
      if (n !== this.filtro()[campo]) {
        this.cambio.emit({ [campo]: n });
      }
    }
  }

  protected fecha(campo: 'plazoDesde' | 'plazoHasta', valor: string): void {
    const fecha = valor || null;
    if (fecha !== this.filtro()[campo]) {
      this.cambio.emit({ [campo]: fecha });
    }
  }

  protected limpiar(): void {
    const { q } = this.filtro();
    this.cambio.emit({ ...FILTRO_INICIAL, q });
  }
}
