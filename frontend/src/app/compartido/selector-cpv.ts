import { HttpClient, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { CONFIGURACION } from '../nucleo/configuracion';
import { Codigo } from '../nucleo/modelo';

let siguienteId = 0;

/**
 * Elige códigos CPV escribiendo palabras («limpieza») o las primeras cifras («7221»). Cuadro combinado accesible:
 * flechas para moverse, Intro para elegir, Escape para cerrar.
 */
@Component({
  selector: 'rl-selector-cpv',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="selector">
      <label [for]="id + '-texto'">{{ etiqueta() }}</label>
      @if (seleccionados().length) {
        <ul class="fichas" aria-label="CPV elegidos">
          @for (codigo of seleccionados(); track codigo) {
            <li>
              <span class="ficha">
                <span class="codigo">{{ codigo }}</span> {{ nombreDe(codigo) }}
                <button
                  type="button"
                  (click)="quitar(codigo)"
                  [attr.aria-label]="'Quitar ' + codigo"
                >
                  ×
                </button>
              </span>
            </li>
          }
        </ul>
      }
      <div class="combo">
        <input
          type="text"
          [id]="id + '-texto'"
          role="combobox"
          autocomplete="off"
          [attr.aria-expanded]="abierto()"
          [attr.aria-controls]="id + '-lista'"
          [attr.aria-activedescendant]="activo() >= 0 ? id + '-op-' + activo() : null"
          aria-autocomplete="list"
          placeholder="Palabras o cifras del código"
          [value]="texto()"
          (input)="escribir($any($event.target).value)"
          (keydown)="tecla($event)"
          (blur)="cerrarLuego()"
        />
        @if (abierto()) {
          <ul class="lista" role="listbox" [id]="id + '-lista'" aria-label="Códigos CPV">
            @for (opcion of opciones(); track opcion.codigo; let i = $index) {
              <li
                role="option"
                [id]="id + '-op-' + i"
                [attr.aria-selected]="i === activo()"
                [class.activa]="i === activo()"
                (mousedown)="$event.preventDefault(); elegir(opcion)"
              >
                <span class="codigo">{{ opcion.codigo }}</span> {{ opcion.nombre }}
              </li>
            } @empty {
              <li class="vacia" role="option" aria-disabled="true" aria-selected="false">
                {{ sugerencias.isLoading() ? 'Buscando…' : 'Sin resultados' }}
              </li>
            }
          </ul>
        }
      </div>
      <p class="ayuda">
        Un código incluye todos los que cuelgan de él: 72 son todos los servicios de TI.
      </p>
    </div>
  `,
  styles: `
    .selector {
      margin-bottom: 1rem;
    }
    label {
      display: block;
      font-weight: 600;
      font-size: 0.9rem;
      margin-bottom: 0.3rem;
    }
    .combo {
      position: relative;
    }
    .lista {
      position: absolute;
      z-index: 4;
      left: 0;
      right: 0;
      max-height: 18rem;
      overflow: auto;
      margin: 2px 0 0;
      padding: 0.25rem 0;
      list-style: none;
      background: var(--superficie);
      border: 1px solid var(--borde-fuerte);
      border-radius: 6px;
      box-shadow: var(--sombra);
    }
    .lista li {
      padding: 0.4rem 0.6rem;
      cursor: pointer;
      font-size: 0.9rem;
    }
    .lista li.activa,
    .lista li:hover {
      background: var(--acento-suave);
    }
    .lista li.vacia {
      cursor: default;
      color: var(--texto-suave);
    }
    .codigo {
      font-family: var(--mono);
      font-size: 0.85em;
      color: var(--texto-suave);
    }
    .fichas {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
      margin: 0 0 0.4rem;
      padding: 0;
      list-style: none;
    }
    .ficha {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      max-width: 100%;
      padding: 0.15rem 0.2rem 0.15rem 0.6rem;
      border-radius: 999px;
      background: var(--acento-suave);
      font-size: 0.85rem;
    }
    .ficha button {
      border: 0;
      background: none;
      color: inherit;
      font-size: 1.1rem;
      line-height: 1;
      width: 24px;
      height: 24px;
      border-radius: 999px;
      cursor: pointer;
    }
    .ficha button:hover {
      background: var(--superficie);
    }
  `,
})
export class SelectorCpv {
  readonly seleccionados = input<string[]>([]);
  readonly etiqueta = input('Actividad (CPV)');
  readonly cambio = output<string[]>();

  protected readonly id = `cpv-${siguienteId++}`;
  protected readonly texto = signal('');
  private readonly consulta = signal('');
  protected readonly activo = signal(-1);
  private readonly enfocado = signal(false);
  private readonly nombres = signal(new Map<string, string>());
  private espera: ReturnType<typeof setTimeout> | undefined;

  private readonly api = inject(CONFIGURACION).api;
  protected readonly sugerencias = httpResource<Codigo[]>(() => {
    const q = this.consulta();
    return q.length >= 2
      ? { url: `${this.api}/v1/catalogos/cpv`, params: { q, limite: 15 } }
      : undefined;
  });
  protected readonly opciones = computed(() =>
    (this.sugerencias.hasValue() ? this.sugerencias.value() : []).filter(
      (o) => !this.seleccionados().includes(o.codigo),
    ),
  );
  protected readonly abierto = computed(() => this.enfocado() && this.consulta().length >= 2);

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.espera));
    // Los códigos que llegan de la URL o de una alerta guardada vienen sin nombre: se piden.
    const http = inject(HttpClient);
    effect(() => {
      for (const codigo of this.seleccionados()) {
        if (untracked(this.nombres).has(codigo)) {
          continue;
        }
        this.nombres.update((m) => new Map(m).set(codigo, ''));
        http
          .get<Codigo[]>(`${this.api}/v1/catalogos/cpv`, { params: { q: codigo, limite: 1 } })
          .subscribe((encontrados) => {
            const nombre = encontrados[0]?.codigo.startsWith(codigo) ? encontrados[0].nombre : '';
            this.nombres.update((m) => new Map(m).set(codigo, nombre));
          });
      }
    });
  }

  protected nombreDe(codigo: string): string {
    return this.nombres().get(codigo) ?? '';
  }

  protected escribir(valor: string): void {
    this.texto.set(valor);
    this.enfocado.set(true);
    this.activo.set(-1);
    clearTimeout(this.espera);
    this.espera = setTimeout(() => this.consulta.set(valor.trim()), 250);
  }

  protected tecla(evento: KeyboardEvent): void {
    const total = this.opciones().length;
    if (evento.key === 'ArrowDown' && total) {
      evento.preventDefault();
      this.enfocado.set(true);
      this.activo.update((i) => (i + 1) % total);
    } else if (evento.key === 'ArrowUp' && total) {
      evento.preventDefault();
      this.activo.update((i) => (i <= 0 ? total - 1 : i - 1));
    } else if (evento.key === 'Enter' && this.abierto()) {
      evento.preventDefault();
      const opcion = this.opciones()[this.activo()] ?? this.opciones()[0];
      if (opcion) {
        this.elegir(opcion);
      }
    } else if (evento.key === 'Escape') {
      this.enfocado.set(false);
    }
  }

  protected elegir(opcion: Codigo): void {
    this.nombres.update((m) => new Map(m).set(opcion.codigo, opcion.nombre));
    this.cambio.emit([...this.seleccionados(), opcion.codigo]);
    this.texto.set('');
    this.consulta.set('');
    this.activo.set(-1);
  }

  protected quitar(codigo: string): void {
    this.cambio.emit(this.seleccionados().filter((c) => c !== codigo));
  }

  protected cerrarLuego(): void {
    this.espera = setTimeout(() => this.enfocado.set(false), 150);
  }
}
