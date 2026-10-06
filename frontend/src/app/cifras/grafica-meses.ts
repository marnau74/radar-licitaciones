import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { entero, mes } from '../nucleo/formato';

/** Columnas por mes en SVG, con una tabla equivalente para lectores de pantalla. */
@Component({
  selector: 'rl-grafica-meses',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure>
      <svg
        [attr.viewBox]="'0 0 ' + ancho + ' ' + alto"
        role="img"
        [attr.aria-label]="titulo()"
        preserveAspectRatio="none"
      >
        @for (linea of guias(); track linea.y) {
          <line
            [attr.x1]="0"
            [attr.x2]="ancho"
            [attr.y1]="linea.y"
            [attr.y2]="linea.y"
            class="guia"
          />
        }
        @for (c of columnas(); track c.mes) {
          <rect
            [attr.x]="c.x"
            [attr.y]="c.y"
            [attr.width]="c.ancho"
            [attr.height]="c.alto"
            rx="2"
            class="columna"
          >
            <title>{{ c.etiqueta }}: {{ c.texto }}</title>
          </rect>
        }
      </svg>
      <div class="ejes" aria-hidden="true">
        @for (c of columnas(); track c.mes) {
          <span>{{ c.etiqueta }}</span>
        }
      </div>
      <figcaption class="oculto">
        <table>
          <caption>
            {{
              titulo()
            }}
          </caption>
          <tr>
            <th scope="col">Mes</th>
            <th scope="col">Licitaciones</th>
          </tr>
          @for (c of columnas(); track c.mes) {
            <tr>
              <td>{{ c.etiqueta }}</td>
              <td>{{ c.texto }}</td>
            </tr>
          }
        </table>
      </figcaption>
    </figure>
  `,
  styles: `
    figure {
      margin: 0;
    }
    svg {
      display: block;
      width: 100%;
      height: 180px;
    }
    .columna {
      fill: var(--acento);
    }
    .columna:hover {
      opacity: 0.8;
    }
    .guia {
      stroke: var(--borde);
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
    }
    .ejes {
      display: grid;
      grid-auto-flow: column;
      grid-auto-columns: 1fr;
      margin-top: 0.3rem;
      font-size: 0.75rem;
      color: var(--texto-suave);
      text-align: center;
    }
    @media (max-width: 560px) {
      .ejes span:nth-child(even) {
        visibility: hidden;
      }
    }
  `,
})
export class GraficaMeses {
  readonly datos = input.required<{ mes: string; licitaciones: number }[]>();
  readonly titulo = input.required<string>();

  protected readonly ancho = 600;
  protected readonly alto = 180;

  private readonly maximo = computed(() => Math.max(1, ...this.datos().map((d) => d.licitaciones)));

  protected readonly columnas = computed(() => {
    const n = Math.max(1, this.datos().length);
    const hueco = this.ancho / n;
    return this.datos().map((d, i) => {
      const alto = (d.licitaciones / this.maximo()) * (this.alto - 4);
      return {
        mes: d.mes,
        etiqueta: mes(d.mes),
        texto: entero(d.licitaciones),
        x: i * hueco + hueco * 0.15,
        ancho: hueco * 0.7,
        y: this.alto - alto,
        alto,
      };
    });
  });

  protected readonly guias = computed(() => [0.25, 0.5, 0.75].map((f) => ({ y: this.alto * f })));
}
