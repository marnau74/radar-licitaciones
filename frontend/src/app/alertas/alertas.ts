import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, mensajeDeError } from '../nucleo/api';
import { ServicioDeCatalogos } from '../nucleo/catalogos';
import { CONFIGURACION } from '../nucleo/configuracion';
import { euros } from '../nucleo/formato';
import { Alerta } from '../nucleo/modelo';

/** Mis alertas. */
@Component({
  selector: 'rl-alertas',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenedor estrecho">
      <div class="cabeza">
        <h1>Mis alertas</h1>
        <a class="boton principal" routerLink="/alertas/nueva">Nueva alerta</a>
      </div>
      <p class="suave">
        Cada mañana, tras cargar los datos, te avisamos de las licitaciones nuevas en plazo que
        encajan con tus alertas: en <a routerLink="/avisos">Avisos</a> y, si quieres, con un correo
        resumen.
      </p>

      @if (mensaje(); as m) {
        <p class="panel" role="alert">{{ m }}</p>
      }

      @if (alertas.hasValue()) {
        <ul class="lista">
          @for (a of alertas.value(); track a.id) {
            <li class="panel alerta" [class.pausada]="!a.activa">
              <div class="datos-alerta">
                <h2>
                  <a [routerLink]="['/alertas', a.id]">{{ a.nombre }}</a>
                  @if (!a.activa) {
                    <span class="estado">Pausada</span>
                  }
                </h2>
                <p class="criterios">{{ describir(a) }}</p>
                <p class="suave pequeno">
                  {{ a.porCorreo ? 'Con correo a ' + a.correo : 'Sin correo: solo en la web' }}
                </p>
              </div>
              <div class="acciones">
                <a class="boton" [routerLink]="['/']" [queryParams]="busquedaDe(a)"
                  >Ver resultados</a
                >
                <a class="boton" [routerLink]="['/alertas', a.id]">Editar</a>
                <button
                  type="button"
                  class="boton peligro"
                  [disabled]="borrando() === a.id"
                  (click)="borrar(a)"
                >
                  Borrar
                </button>
              </div>
            </li>
          } @empty {
            <li class="panel aviso-vacio">
              <p>Aún no tienes alertas.</p>
              <p>Haz una búsqueda y pulsa «Crear alerta», o empieza desde cero.</p>
              <a class="boton principal" routerLink="/alertas/nueva">Crear mi primera alerta</a>
            </li>
          }
        </ul>
      } @else if (alertas.error()) {
        <div class="panel" role="alert">
          <p>{{ error() }}</p>
          <button type="button" class="boton" (click)="alertas.reload()">Reintentar</button>
        </div>
      } @else {
        <p class="suave" aria-live="polite">Cargando…</p>
      }
    </div>
  `,
  styles: `
    .estrecho {
      max-width: 860px;
    }
    .cabeza {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .lista {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin: 1.5rem 0 0;
      padding: 0;
      list-style: none;
    }
    .alerta {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .alerta.pausada {
      opacity: 0.75;
    }
    .datos-alerta {
      flex: 1 1 320px;
      min-width: 0;
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.1rem;
      margin-bottom: 0.25rem;
    }
    .criterios {
      margin-bottom: 0.25rem;
      overflow-wrap: anywhere;
    }
    .pequeno {
      font-size: 0.85rem;
      margin: 0;
    }
    .acciones {
      display: flex;
      gap: 0.5rem;
      align-items: flex-start;
      flex-wrap: wrap;
    }
  `,
})
export class Alertas {
  private readonly api = inject(Api);
  private readonly base = inject(CONFIGURACION).api;
  private readonly catalogos = inject(ServicioDeCatalogos);

  protected readonly alertas = httpResource<Alerta[]>(() => `${this.base}/v1/alertas`);
  protected readonly error = computed(() =>
    this.alertas.error() ? mensajeDeError(this.alertas.error()) : '',
  );
  protected readonly borrando = signal<number | null>(null);
  protected readonly mensaje = signal<string | null>(null);

  protected describir(a: Alerta): string {
    const partes: string[] = [];
    if (a.texto) partes.push(`«${a.texto}»`);
    if (a.tiposContrato.length)
      partes.push(a.tiposContrato.map((t) => this.catalogos.nombre('tipo', t)).join(', '));
    if (a.procedimientos.length) {
      partes.push(
        'procedimiento ' +
          a.procedimientos.map((p) => this.catalogos.nombre('procedimiento', p)).join(', '),
      );
    }
    if (a.nuts.length)
      partes.push('en ' + a.nuts.map((n) => this.catalogos.nombre('nuts', n)).join(', '));
    if (a.cpv.length) partes.push('CPV ' + a.cpv.join(', '));
    if (a.importeMinimo !== null) partes.push('desde ' + euros(a.importeMinimo));
    if (a.importeMaximo !== null) partes.push('hasta ' + euros(a.importeMaximo));
    if (a.soloFondosUe) partes.push('con fondos UE');
    return partes.join(' · ');
  }

  protected busquedaDe(a: Alerta): Record<string, string> {
    const q: Record<string, string> = {};
    if (a.texto) q['q'] = a.texto;
    if (a.tiposContrato.length) q['tipo'] = a.tiposContrato.join(',');
    if (a.procedimientos.length) q['procedimiento'] = a.procedimientos.join(',');
    if (a.nuts.length) q['nuts'] = a.nuts.join(',');
    if (a.cpv.length) q['cpv'] = a.cpv.join(',');
    if (a.importeMinimo !== null) q['importeMin'] = String(a.importeMinimo);
    if (a.importeMaximo !== null) q['importeMax'] = String(a.importeMaximo);
    if (a.soloFondosUe) q['fondosUe'] = 'true';
    return q;
  }

  protected async borrar(a: Alerta): Promise<void> {
    if (!confirm(`¿Borrar la alerta «${a.nombre}» y sus avisos?`)) {
      return;
    }
    this.borrando.set(a.id);
    this.mensaje.set(null);
    try {
      await this.api.borrarAlerta(a.id);
      this.alertas.reload();
    } catch (e) {
      this.mensaje.set(mensajeDeError(e));
    } finally {
      this.borrando.set(null);
    }
  }
}
