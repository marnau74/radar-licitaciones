import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TarjetaLicitacion } from '../compartido/tarjeta-licitacion';
import { mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import { EstadoDeIa, ResultadoSemantico } from '../nucleo/modelo';

/**
 * Buscar describiendo lo que se busca: «limpieza de colegios» encuentra también «higiene en centros educativos». La
 * consulta y el filtro van en la URL, como en la búsqueda normal.
 */
@Component({
  selector: 'rl-significado',
  imports: [TarjetaLicitacion, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenedor estrecho">
      <h1>Buscar por significado</h1>
      <p class="suave">
        Describe lo que buscas con tus palabras: se encuentran licitaciones parecidas aunque no usen
        los mismos términos. Para filtrar por lugar, importe o actividad, usa la
        <a routerLink="/">búsqueda normal</a>.
      </p>

      @if (estado.hasValue() && !estado.value().busqueda) {
        <p class="panel" role="status">
          La búsqueda por significado no está activada en este servidor.
        </p>
      } @else {
        <form
          role="search"
          class="formulario"
          (submit)="buscar($event, texto.value, abiertas.checked)"
        >
          <label for="consulta" class="oculto">Qué buscas</label>
          <input
            #texto
            id="consulta"
            type="search"
            maxlength="500"
            placeholder="Por ejemplo: mantenimiento de ascensores en hospitales"
            [value]="consulta()"
          />
          <label class="casilla">
            <input #abiertas type="checkbox" [checked]="soloAbiertas()" />
            Solo en plazo
          </label>
          <button type="submit" class="boton principal">Buscar</button>
        </form>

        @if (error(); as e) {
          <p class="panel" role="alert">{{ e }}</p>
        }

        @if (resultados.isLoading()) {
          <p class="suave" aria-live="polite">Buscando…</p>
        } @else if (resultados.hasValue()) {
          @if (resultados.value().length) {
            <ol class="lista" aria-label="Resultados, del más parecido al menos">
              @for (r of resultados.value(); track r.licitacion.id) {
                <li>
                  <rl-tarjeta-licitacion [licitacion]="r.licitacion" />
                  <span class="suave parecido">Parecido: {{ porcentaje(r.similitud) }}</span>
                </li>
              }
            </ol>
          } @else {
            <p class="suave">
              No hay licitaciones parecidas{{ soloAbiertas() ? ' en plazo' : '' }}.
            </p>
          }
        }
      }
    </div>
  `,
  styles: `
    .formulario {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      margin: 1rem 0 1.5rem;
    }
    .formulario input[type='search'] {
      flex: 1 1 20rem;
    }
    .lista {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .parecido {
      display: block;
      margin-top: 0.25rem;
      font-size: 0.85rem;
    }
  `,
})
export class Significado {
  private readonly router = inject(Router);
  private readonly api = inject(CONFIGURACION).api;
  private readonly parametros = toSignal(inject(ActivatedRoute).queryParams, { requireSync: true });

  protected readonly consulta = computed(() => String(this.parametros()['q'] ?? '').trim());
  protected readonly soloAbiertas = computed(() => this.parametros()['abiertas'] !== 'false');

  protected readonly estado = httpResource<EstadoDeIa>(() => `${this.api}/v1/ia/estado`);
  protected readonly resultados = httpResource<ResultadoSemantico[]>(() =>
    this.consulta()
      ? {
          url: `${this.api}/v1/licitaciones/semantica`,
          params: { q: this.consulta(), abiertas: this.soloAbiertas(), limite: 20 },
        }
      : undefined,
  );
  protected readonly error = computed(() =>
    this.resultados.error() ? mensajeDeError(this.resultados.error()) : null,
  );

  protected buscar(evento: Event, texto: string, abiertas: boolean): void {
    evento.preventDefault();
    void this.router.navigate([], {
      queryParams: { q: texto.trim() || null, abiertas: abiertas ? null : 'false' },
    });
  }

  protected porcentaje(similitud: number): string {
    return `${Math.round(Math.max(0, similitud) * 100)} %`;
  }
}
