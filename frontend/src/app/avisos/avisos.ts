import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Paginacion } from '../compartido/paginacion';
import { TarjetaLicitacion } from '../compartido/tarjeta-licitacion';
import { Api, mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import { FechaPipe } from '../nucleo/formato';
import { Aviso, Pagina } from '../nucleo/modelo';

/** Lo que han encontrado mis alertas. */
@Component({
  selector: 'rl-avisos',
  imports: [TarjetaLicitacion, Paginacion, RouterLink, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenedor estrecho">
      <div class="cabeza">
        <h1>Mis avisos</h1>
        <div class="herramientas">
          <label class="casilla">
            <input
              type="checkbox"
              [checked]="soloNoLeidos()"
              (change)="filtrar($any($event.target).checked)"
            />
            Solo sin leer
          </label>
          <button type="button" class="boton" [disabled]="marcando()" (click)="marcarLeidos()">
            Marcar todo como leído
          </button>
        </div>
      </div>

      @if (mensaje(); as m) {
        <p class="panel" role="alert">{{ m }}</p>
      }

      @if (avisos.hasValue()) {
        <ul class="lista">
          @for (a of avisos.value().elementos; track a.id) {
            <li [class.nuevo]="!a.leido">
              <p class="meta">
                @if (!a.leido) {
                  <span class="punto" aria-hidden="true"></span
                  ><span class="oculto">Sin leer. </span>
                }
                Por tu alerta <a [routerLink]="['/alertas', a.alertaId]">{{ a.alerta }}</a> ·
                {{ a.creadoEn | fecha: true }}
              </p>
              <rl-tarjeta-licitacion [licitacion]="a.licitacion" />
            </li>
          } @empty {
            <li class="panel aviso-vacio">
              <p>{{ soloNoLeidos() ? 'No tienes avisos sin leer.' : 'Aún no tienes avisos.' }}</p>
              <p>
                Llegan cada mañana, cuando hay licitaciones nuevas que encajan con tus
                <a routerLink="/alertas">alertas</a>.
              </p>
            </li>
          }
        </ul>
        <rl-paginacion
          [pagina]="avisos.value().pagina"
          [paginas]="avisos.value().paginas"
          (cambiar)="pagina.set($event)"
        />
      } @else if (avisos.error()) {
        <div class="panel" role="alert">
          <p>{{ error() }}</p>
          <button type="button" class="boton" (click)="avisos.reload()">Reintentar</button>
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
      margin-bottom: 1rem;
    }
    .herramientas {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .lista {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .meta {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0 0 0.35rem;
      font-size: 0.85rem;
      color: var(--texto-suave);
      flex-wrap: wrap;
    }
    .punto {
      width: 0.6rem;
      height: 0.6rem;
      border-radius: 999px;
      background: var(--acento);
    }
  `,
})
export class Avisos {
  private readonly api = inject(Api);
  private readonly base = inject(CONFIGURACION).api;

  protected readonly pagina = signal(1);
  protected readonly soloNoLeidos = signal(false);
  protected readonly marcando = signal(false);
  protected readonly mensaje = signal<string | null>(null);

  protected readonly avisos = httpResource<Pagina<Aviso>>(() => ({
    url: `${this.base}/v1/avisos`,
    params: { pagina: this.pagina(), tamano: 20, soloNoLeidos: this.soloNoLeidos() },
  }));
  protected readonly error = computed(() =>
    this.avisos.error() ? mensajeDeError(this.avisos.error()) : '',
  );

  protected filtrar(soloNoLeidos: boolean): void {
    this.soloNoLeidos.set(soloNoLeidos);
    this.pagina.set(1);
  }

  protected async marcarLeidos(): Promise<void> {
    this.marcando.set(true);
    this.mensaje.set(null);
    try {
      await this.api.marcarAvisosLeidos();
      this.avisos.reload();
    } catch (e) {
      this.mensaje.set(mensajeDeError(e));
    } finally {
      this.marcando.set(false);
    }
  }
}
