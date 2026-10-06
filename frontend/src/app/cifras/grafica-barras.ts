import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { entero } from '../nucleo/formato';

export interface Barra {
  etiqueta: string;
  valor: number;
  /** Texto secundario (el importe, por ejemplo). */
  detalle?: string;
}

/**
 * Barras horizontales en HTML y CSS (no SVG): el texto se ajusta solo al ancho y se lee bien con lector de pantalla,
 * porque es una lista con sus cifras.
 */
@Component({
  selector: 'rl-grafica-barras',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="barras" [attr.aria-label]="titulo()">
      @for (b of barras(); track b.etiqueta) {
        <li>
          <span class="etiqueta">{{ b.etiqueta }}</span>
          <span class="pista" aria-hidden="true"
            ><span class="relleno" [style.width.%]="b.porcentaje"></span
          ></span>
          <span class="valor cifra">{{ b.texto }}</span>
          @if (b.detalle) {
            <span class="detalle suave cifra">{{ b.detalle }}</span>
          }
        </li>
      }
    </ol>
  `,
  styles: `
    .barras {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    li {
      display: grid;
      grid-template-columns: minmax(8rem, 13rem) 1fr auto;
      grid-template-areas: 'etiqueta pista valor' '. detalle detalle';
      align-items: center;
      gap: 0 0.75rem;
      padding: 0.3rem 0;
      font-size: 0.9rem;
    }
    .etiqueta {
      grid-area: etiqueta;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .pista {
      grid-area: pista;
      height: 0.75rem;
      background: var(--superficie-2);
      border-radius: 4px;
      overflow: hidden;
    }
    .relleno {
      display: block;
      height: 100%;
      background: var(--acento);
      border-radius: 4px;
      min-width: 2px;
    }
    .valor {
      grid-area: valor;
      text-align: right;
      font-weight: 600;
    }
    .detalle {
      grid-area: detalle;
      font-size: 0.8rem;
    }
    @media (max-width: 560px) {
      li {
        grid-template-columns: 1fr auto;
        grid-template-areas: 'etiqueta valor' 'pista pista' 'detalle detalle';
        gap: 0.2rem 0.5rem;
      }
    }
  `,
})
export class GraficaBarras {
  readonly datos = input.required<Barra[]>();
  readonly titulo = input.required<string>();

  protected readonly barras = computed(() => {
    const maximo = Math.max(1, ...this.datos().map((d) => d.valor));
    return this.datos().map((d) => ({
      ...d,
      porcentaje: (d.valor / maximo) * 100,
      texto: entero(d.valor),
    }));
  });
}
