import { HttpParams, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SelectorCpv } from '../compartido/selector-cpv';
import { SelectorLugar } from '../compartido/selector-lugar';
import { Api, mensajeDeError } from '../nucleo/api';
import { ServicioDeCatalogos } from '../nucleo/catalogos';
import { CONFIGURACION } from '../nucleo/configuracion';
import { filtroDesdeUrl } from '../nucleo/filtro';
import { EnteroPipe } from '../nucleo/formato';
import { Alerta, DatosDeAlerta, LicitacionResumen, Pagina } from '../nucleo/modelo';
import { Sesion } from '../nucleo/sesion';

const VACIA: DatosDeAlerta = {
  nombre: '',
  texto: null,
  tiposContrato: [],
  procedimientos: [],
  nuts: [],
  cpv: [],
  importeMinimo: null,
  importeMaximo: null,
  soloFondosUe: false,
  porCorreo: true,
  activa: true,
};

/** Crear o editar una alerta. Al crearla desde una búsqueda, llega rellena con sus criterios. */
@Component({
  selector: 'rl-formulario-alerta',
  imports: [SelectorCpv, SelectorLugar, RouterLink, EnteroPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './formulario-alerta.html',
  styleUrl: './formulario-alerta.css',
})
export class FormularioAlerta {
  /** Del parámetro de la ruta: vacío al crear. */
  readonly id = input<string>();

  private readonly api = inject(Api);
  private readonly base = inject(CONFIGURACION).api;
  private readonly router = inject(Router);
  protected readonly catalogos = inject(ServicioDeCatalogos);
  protected readonly sesion = inject(Sesion);

  protected readonly datos = signal<DatosDeAlerta>({ ...VACIA });
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly errorServidor = signal<string | null>(null);

  protected readonly editando = computed(() => !!this.id());
  private readonly existente = httpResource<Alerta>(() =>
    this.id() ? `${this.base}/v1/alertas/${this.id()}` : undefined,
  );
  protected readonly cargando = computed(() => this.editando() && this.existente.isLoading());
  protected readonly errorCarga = computed(() =>
    this.existente.error() ? mensajeDeError(this.existente.error()) : null,
  );

  protected readonly tipos = computed(() =>
    this.catalogos.catalogos.hasValue() ? this.catalogos.catalogos.value().tiposContrato : [],
  );
  protected readonly procedimientos = computed(() =>
    this.catalogos.catalogos.hasValue() ? this.catalogos.catalogos.value().procedimientos : [],
  );

  protected readonly errores = computed(() => {
    const d = this.datos();
    const errores: string[] = [];
    if (!d.nombre.trim()) errores.push('Ponle un nombre a la alerta.');
    const conCriterio =
      !!d.texto?.trim() ||
      d.tiposContrato.length + d.procedimientos.length + d.nuts.length + d.cpv.length > 0 ||
      d.importeMinimo !== null ||
      d.importeMaximo !== null;
    if (!conCriterio)
      errores.push(
        'Indica al menos un criterio: texto, tipo, procedimiento, lugar, CPV o importe.',
      );
    if (d.importeMinimo !== null && d.importeMaximo !== null && d.importeMinimo > d.importeMaximo) {
      errores.push('El importe mínimo no puede ser mayor que el máximo.');
    }
    return errores;
  });

  /** Los criterios, sin nombre ni opciones, y un instante después de escribir: no se pide nada en cada tecla. */
  private readonly criterios = signal<DatosDeAlerta>(
    { ...VACIA },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
  );

  /** Cuántas licitaciones en plazo encajan ahora mismo: para afinar antes de guardar. */
  protected readonly vistaPrevia = httpResource<Pagina<LicitacionResumen>>(() => {
    const d = this.criterios();
    let params = new HttpParams().set('estado', 'PUB').set('tamano', 3).set('orden', 'publicacion');
    if (d.texto?.trim()) params = params.set('q', d.texto.trim());
    if (d.tiposContrato.length) params = params.set('tipo', d.tiposContrato.join(','));
    if (d.procedimientos.length) params = params.set('procedimiento', d.procedimientos.join(','));
    if (d.nuts.length) params = params.set('nuts', d.nuts.join(','));
    if (d.cpv.length) params = params.set('cpv', d.cpv.join(','));
    if (d.importeMinimo !== null) params = params.set('importeMin', d.importeMinimo);
    if (d.importeMaximo !== null) params = params.set('importeMax', d.importeMaximo);
    if (d.soloFondosUe) params = params.set('fondosUe', true);
    return { url: `${this.base}/v1/licitaciones`, params };
  });

  constructor() {
    effect((alLimpiar) => {
      const d = this.datos();
      const espera = setTimeout(
        () => this.criterios.set({ ...d, nombre: '', porCorreo: true, activa: true }),
        300,
      );
      alLimpiar(() => clearTimeout(espera));
    });
    // Al crear desde una búsqueda, sus criterios vienen en la URL.
    const parametros = inject(ActivatedRoute).snapshot.queryParams;
    if (Object.keys(parametros).length) {
      const f = filtroDesdeUrl(parametros);
      this.datos.set({
        ...VACIA,
        texto: f.q || null,
        tiposContrato: f.tipo,
        procedimientos: f.procedimiento,
        nuts: f.nuts,
        cpv: f.cpv,
        importeMinimo: f.importeMin,
        importeMaximo: f.importeMax,
        soloFondosUe: f.fondosUe,
        nombre: f.q ? f.q.slice(0, 80) : '',
      });
    }
    effect(() => {
      if (this.existente.hasValue()) {
        const a = this.existente.value();
        untracked(() =>
          this.datos.set({
            nombre: a.nombre,
            texto: a.texto,
            tiposContrato: a.tiposContrato,
            procedimientos: a.procedimientos,
            nuts: a.nuts,
            cpv: a.cpv,
            importeMinimo: a.importeMinimo,
            importeMaximo: a.importeMaximo,
            soloFondosUe: a.soloFondosUe,
            porCorreo: a.porCorreo,
            activa: a.activa,
          }),
        );
      }
    });
  }

  protected cambiar<K extends keyof DatosDeAlerta>(campo: K, valor: DatosDeAlerta[K]): void {
    this.datos.update((d) => ({ ...d, [campo]: valor }));
  }

  protected alternar(
    campo: 'tiposContrato' | 'procedimientos',
    codigo: string,
    marcado: boolean,
  ): void {
    const actuales = this.datos()[campo];
    this.cambiar(campo, marcado ? [...actuales, codigo] : actuales.filter((c) => c !== codigo));
  }

  protected importe(campo: 'importeMinimo' | 'importeMaximo', valor: string): void {
    const n = valor.trim() === '' ? null : Number(valor);
    this.cambiar(campo, n !== null && Number.isFinite(n) && n >= 0 ? n : null);
  }

  protected async guardar(evento: Event): Promise<void> {
    evento.preventDefault();
    this.enviado.set(true);
    this.errorServidor.set(null);
    if (this.errores().length) {
      document.getElementById('errores-alerta')?.focus();
      return;
    }
    const d = this.datos();
    const datos: DatosDeAlerta = { ...d, nombre: d.nombre.trim(), texto: d.texto?.trim() || null };
    this.enviando.set(true);
    try {
      const id = this.id();
      if (id) {
        await this.api.modificarAlerta(Number(id), datos);
      } else {
        await this.api.crearAlerta(datos);
      }
      await this.router.navigateByUrl('/alertas');
    } catch (e) {
      this.errorServidor.set(mensajeDeError(e));
    } finally {
      this.enviando.set(false);
    }
  }
}
