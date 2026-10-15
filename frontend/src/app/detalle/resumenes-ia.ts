import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Api, mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import { FechaPipe } from '../nucleo/formato';
import { DetalleDeLicitacion, EstadoDeIa, ResumenGuardado } from '../nucleo/modelo';
import { Sesion } from '../nucleo/sesion';

type Documento = DetalleDeLicitacion['documentos'][number];

/**
 * Resúmenes de los pliegos con IA. Lo ya resumido se ve sin sesión; resumir uno nuevo necesita entrar (cuesta dinero o
 * tiempo de máquina y tiene un límite diario). Si el servidor no tiene la IA activada y no hay nada resumido, no se
 * enseña nada.
 */
@Component({
  selector: 'rl-resumenes-ia',
  imports: [FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <section class="panel" aria-labelledby="t-resumen-ia">
        <h2 id="t-resumen-ia">Resumen de los pliegos con IA</h2>
        <p class="suave aviso-ia">
          Hecho por un modelo de lenguaje a partir del PDF: puede equivocarse u omitir algo. Lo que
          vale es el pliego.
        </p>

        @for (d of pliegos(); track d.url) {
          <div class="pliego">
            <h3>{{ d.nombre || nombreDeTipo(d.tipo) }}</h3>
            @if (resumenDe(d.url); as r) {
              <dl class="resumen">
                <dt>Objeto</dt>
                <dd>{{ r.resumen.objeto }}</dd>
                <dt>Presupuesto</dt>
                <dd>{{ r.resumen.presupuesto }}</dd>
                <dt>Duración</dt>
                <dd>{{ r.resumen.duracion }}</dd>
                @if (r.resumen.solvenciaEconomica.length) {
                  <dt>Solvencia económica</dt>
                  <dd>
                    <ul>
                      @for (s of r.resumen.solvenciaEconomica; track $index) {
                        <li>{{ s }}</li>
                      }
                    </ul>
                  </dd>
                }
                @if (r.resumen.solvenciaTecnica.length) {
                  <dt>Solvencia técnica</dt>
                  <dd>
                    <ul>
                      @for (s of r.resumen.solvenciaTecnica; track $index) {
                        <li>{{ s }}</li>
                      }
                    </ul>
                  </dd>
                }
                @if (r.resumen.criteriosDeAdjudicacion.length) {
                  <dt>Criterios de adjudicación</dt>
                  <dd>
                    <ul>
                      @for (c of r.resumen.criteriosDeAdjudicacion; track $index) {
                        <li>
                          {{ c.criterio }}: <strong>{{ c.peso }}</strong>
                          <span class="suave"
                            >({{ c.automatico ? 'con fórmula' : 'juicio de valor' }})</span
                          >
                        </li>
                      }
                    </ul>
                  </dd>
                }
                @if (r.resumen.garantias.length) {
                  <dt>Garantías</dt>
                  <dd>
                    <ul>
                      @for (g of r.resumen.garantias; track $index) {
                        <li>{{ g }}</li>
                      }
                    </ul>
                  </dd>
                }
                @if (r.resumen.puntosDeAtencion.length) {
                  <dt>Puntos de atención</dt>
                  <dd>
                    <ul class="atencion">
                      @for (p of r.resumen.puntosDeAtencion; track $index) {
                        <li>{{ p }}</li>
                      }
                    </ul>
                  </dd>
                }
              </dl>
              @if (r.aviso) {
                <p class="suave" role="note">{{ r.aviso }}</p>
              }
              <p class="suave pie">{{ r.modelo }} · {{ r.creadoEn | fecha: true }}</p>
            } @else if (estado.value()?.resumenes) {
              @if (resumiendo() === d.url) {
                <p class="suave" aria-live="polite">Leyendo el pliego… puede tardar un minuto.</p>
              } @else {
                <button
                  type="button"
                  class="boton"
                  [disabled]="resumiendo() !== null"
                  (click)="resumir(d)"
                >
                  {{ sesion.iniciada() ? 'Resumir con IA' : 'Entra para resumirlo con IA' }}
                </button>
              }
            }
          </div>
        }

        @if (error(); as e) {
          <p class="panel" role="alert">{{ e }}</p>
        }
      </section>
    }
  `,
  styles: `
    .aviso-ia {
      margin-top: -0.25rem;
      font-size: 0.85rem;
    }
    .pliego + .pliego {
      margin-top: 1.25rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--borde);
    }
    h3 {
      margin-bottom: 0.5rem;
      font-size: 1rem;
      overflow-wrap: anywhere;
    }
    .resumen {
      display: grid;
      grid-template-columns: minmax(8rem, max-content) 1fr;
      gap: 0.4rem 1rem;
      margin: 0;
    }
    .resumen dt {
      font-weight: 600;
    }
    .resumen dd {
      margin: 0;
    }
    .resumen ul {
      margin: 0;
      padding-left: 1.1rem;
    }
    .atencion li::marker {
      color: var(--pendiente);
    }
    .pie {
      margin-top: 0.75rem;
      font-size: 0.8rem;
    }
    @media (max-width: 600px) {
      .resumen {
        grid-template-columns: 1fr;
      }
      .resumen dd {
        margin-bottom: 0.5rem;
      }
    }
  `,
})
export class ResumenesIa {
  readonly licitacion = input.required<number>();
  readonly documentos = input.required<Documento[]>();

  protected readonly sesion = inject(Sesion);
  private readonly api = inject(Api);
  private readonly base = inject(CONFIGURACION).api;

  protected readonly estado = httpResource<EstadoDeIa>(() => `${this.base}/v1/ia/estado`);
  protected readonly guardados = httpResource<ResumenGuardado[]>(
    () => `${this.base}/v1/licitaciones/${this.licitacion()}/resumenes`,
  );
  /** Los recién hechos en esta visita (se suman a los guardados sin recargar). */
  private readonly nuevos = signal<ResumenGuardado[]>([]);

  protected readonly resumiendo = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Solo los pliegos (los anuncios y otros documentos no se resumen). */
  protected readonly pliegos = computed(() =>
    this.documentos().filter(
      (d) => d.tipo === 'PLIEGO_ADMINISTRATIVO' || d.tipo === 'PLIEGO_TECNICO',
    ),
  );
  private readonly porDocumento = computed(() => {
    const mapa = new Map<string, ResumenGuardado>();
    for (const r of [...(this.guardados.value() ?? []), ...this.nuevos()]) {
      mapa.set(r.documentoUrl, r);
    }
    return mapa;
  });
  protected readonly visible = computed(
    () =>
      this.pliegos().length > 0 && (this.estado.value()?.resumenes || this.porDocumento().size > 0),
  );

  protected resumenDe(url: string): ResumenGuardado | undefined {
    return this.porDocumento().get(url);
  }

  protected nombreDeTipo(tipo: Documento['tipo']): string {
    return tipo === 'PLIEGO_TECNICO'
      ? 'Pliego de prescripciones técnicas'
      : 'Pliego de cláusulas administrativas';
  }

  protected async resumir(documento: Documento): Promise<void> {
    if (!this.sesion.iniciada()) {
      this.sesion.entrar();
      return;
    }
    this.error.set(null);
    this.resumiendo.set(documento.url);
    try {
      const resumen = await this.api.resumirPliego(this.licitacion(), documento.url);
      this.nuevos.update((lista) => [...lista, resumen]);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.resumiendo.set(null);
    }
  }
}
