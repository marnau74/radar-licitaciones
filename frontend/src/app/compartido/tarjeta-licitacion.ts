import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ServicioDeCatalogos } from '../nucleo/catalogos';
import {
  EurosPipe,
  FechaPipe,
  etiquetaDeEstado,
  plazoRestante,
  plazoUrgente,
} from '../nucleo/formato';
import { LicitacionResumen } from '../nucleo/modelo';

/** Una licitación en una lista de resultados. */
@Component({
  selector: 'rl-tarjeta-licitacion',
  imports: [RouterLink, EurosPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let l = licitacion();
    <article class="tarjeta" [class.anulada]="l.anulada">
      <div class="cabeza">
        <span class="estado" [class]="etiqueta().clase">{{ etiqueta().texto }}</span>
        @if (l.tipoContrato) {
          <span class="suave">{{ catalogos.nombre('tipo', l.tipoContrato) }}</span>
        }
        @if (l.financiacionUe) {
          <span class="ue" title="Con financiación de la Unión Europea">Fondos UE</span>
        }
      </div>
      <h3>
        <a [routerLink]="['/licitaciones', l.id]">{{ l.titulo }}</a>
      </h3>
      <p class="organo">{{ l.organo }}</p>
      <dl class="cifras">
        <div>
          <dt>Importe sin IVA</dt>
          <dd class="cifra">{{ l.importeSinIva | euros }}</dd>
        </div>
        <div>
          <dt>Plazo</dt>
          <dd>
            @if (l.plazoPresentacion) {
              <time [attr.datetime]="l.plazoPresentacion">{{
                l.plazoPresentacion | fecha: true
              }}</time>
              @if (l.estado === 'PUB' && !l.anulada) {
                <span class="restante" [class.urgente]="urgente()">{{ restante() }}</span>
              }
            } @else {
              <span class="suave">Sin plazo publicado</span>
            }
          </dd>
        </div>
        <div>
          <dt>Lugar</dt>
          <dd>{{ l.lugar || catalogos.nombre('nuts', l.nuts) || '—' }}</dd>
        </div>
        @if (l.numLotes > 0) {
          <div>
            <dt>Lotes</dt>
            <dd class="cifra">{{ l.numLotes }}</dd>
          </div>
        }
      </dl>
    </article>
  `,
  styles: `
    .tarjeta {
      background: var(--superficie);
      border: 1px solid var(--borde);
      border-radius: var(--radio);
      padding: 1rem 1.25rem;
    }
    .tarjeta.anulada h3 a {
      text-decoration: line-through;
    }
    .cabeza {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem 0.75rem;
      font-size: 0.85rem;
      margin-bottom: 0.4rem;
    }
    .ue {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--acento);
    }
    h3 {
      margin: 0 0 0.25rem;
      font-size: 1.05rem;
      overflow-wrap: anywhere;
    }
    h3 a {
      color: var(--texto);
      text-decoration: none;
    }
    h3 a:hover {
      color: var(--acento);
      text-decoration: underline;
    }
    .organo {
      margin: 0 0 0.75rem;
      color: var(--texto-suave);
      font-size: 0.9rem;
    }
    .cifras {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 2rem;
      margin: 0;
      font-size: 0.9rem;
    }
    dt {
      color: var(--texto-suave);
      font-size: 0.8rem;
    }
    dd {
      margin: 0;
    }
    .restante {
      display: block;
      font-size: 0.8rem;
      color: var(--en-plazo);
    }
    .restante.urgente {
      color: var(--pendiente);
      font-weight: 600;
    }
  `,
})
export class TarjetaLicitacion {
  readonly licitacion = input.required<LicitacionResumen>();
  protected readonly catalogos = inject(ServicioDeCatalogos);
  protected readonly restante = computed(() => plazoRestante(this.licitacion().plazoPresentacion));
  protected readonly urgente = computed(() => plazoUrgente(this.licitacion().plazoPresentacion));
  protected readonly etiqueta = computed(() => {
    const l = this.licitacion();
    return etiquetaDeEstado(
      l.estado,
      this.catalogos.nombre('estado', l.estado),
      l.plazoPresentacion,
      l.anulada,
    );
  });
}
