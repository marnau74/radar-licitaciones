import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Api, mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import { EnteroPipe, FechaPipe } from '../nucleo/formato';
import { EstadoDeIngesta } from '../nucleo/modelo';
import { Sesion } from '../nucleo/sesion';

/** De dónde salen los datos, cómo se tratan y sus limitaciones. */
@Component({
  selector: 'rl-datos',
  imports: [EnteroPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenedor texto">
      <h1>Los datos</h1>

      <h2>De dónde salen</h2>
      <p>
        Del conjunto de datos abiertos
        <em>Licitaciones publicadas en la Plataforma de Contratación del Sector Público</em> del
        Ministerio de Hacienda: los anuncios de los perfiles de contratante alojados en la
        Plataforma, sin los contratos menores. Se publican en formato ATOM con el estándar CODICE y
        se pueden reutilizar citando la fuente.
      </p>
      <p>
        Las licitaciones de las comunidades autónomas que tienen plataforma propia se publican en
        otro conjunto de datos (el de plataformas agregadas), que este proyecto todavía no carga: lo
        que hay aquí es lo que se publica directamente en la Plataforma estatal.
      </p>

      <h2>Cómo se cargan</h2>
      <ul>
        <li>Cada mañana se lee lo publicado desde la carga anterior.</li>
        <li>
          La Plataforma publica una entrada nueva cada vez que una licitación cambia (se adjudica,
          se corrige el plazo, se añade un pliego): aquí se guarda siempre la versión más reciente.
        </li>
        <li>
          Las licitaciones que la Plataforma retira se marcan como anuladas y dejan de salir en la
          búsqueda.
        </li>
        <li>
          Los códigos (tipos de contrato, procedimientos, CPV, NUTS) se enseñan con su nombre según
          las listas oficiales de CODICE.
        </li>
      </ul>

      <h2>Limitaciones</h2>
      <ul>
        <li>
          Los importes son los publicados por cada órgano: alguno tiene errores evidentes en origen
          y no se corrigen.
        </li>
        <li>
          El plazo que vale es el de la Plataforma y los pliegos: compruébalo siempre allí antes de
          presentarte.
        </li>
      </ul>

      <h2>Estado de la carga</h2>
      @if (estado.hasValue()) {
        @let e = estado.value();
        <p>
          Hay <strong>{{ e.licitaciones | entero }}</strong> licitaciones en el radar.
          @if (e.enCurso) {
            Ahora mismo se están cargando datos nuevos.
          }
        </p>
        @if (e.ultimas.length) {
          <div class="tabla-desplazable">
            <table>
              <caption class="oculto">
                Últimas cargas de datos
              </caption>
              <thead>
                <tr>
                  <th scope="col">Terminó</th>
                  <th scope="col">Origen</th>
                  <th scope="col" class="num">Leídas</th>
                  <th scope="col" class="num">Nuevas</th>
                  <th scope="col" class="num">Con cambios</th>
                  <th scope="col">Resultado</th>
                </tr>
              </thead>
              <tbody>
                @for (i of e.ultimas; track i.id) {
                  <tr>
                    <td>{{ i.fin | fecha: true }}</td>
                    <td>{{ i.descripcion }}</td>
                    <td class="num cifra">{{ i.leidas | entero }}</td>
                    <td class="num cifra">{{ i.nuevas | entero }}</td>
                    <td class="num cifra">{{ i.actualizadas | entero }}</td>
                    <td>{{ i.estado === 'COMPLETED' ? 'Bien' : 'Con errores' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      } @else {
        <p class="suave">Consultando…</p>
      }

      @if (sesion.usuario()?.administrador) {
        <section class="panel administracion" aria-labelledby="t-admin">
          <h2 id="t-admin">Cargar datos ahora</h2>
          <p class="suave">
            La carga diaria es automática. Aquí se puede adelantar o cargar el histórico: un año
            (AAAA) o un mes del año en curso (AAAAMM). Un mes son unos 300 MB y tarda un par de
            minutos.
          </p>
          <form class="lanzar" (submit)="lanzar($event, periodo.value)">
            <div class="campo">
              <label for="periodo">Paquete histórico (vacío: lo nuevo del feed)</label>
              <input
                id="periodo"
                #periodo
                type="text"
                inputmode="numeric"
                pattern="\\d{4}(\\d{2})?"
                placeholder="202609"
              />
            </div>
            <button type="submit" class="boton principal" [disabled]="lanzando()">Lanzar</button>
          </form>
          @if (mensaje(); as m) {
            <p role="status">{{ m }}</p>
          }
        </section>
      }

      <h2>La API</h2>
      <p>
        Todo lo que se ve aquí sale de una API pública documentada:
        <a href="/api/docs" rel="noopener">documentación de la API</a>.
      </p>
    </div>
  `,
  styles: `
    .texto {
      max-width: 820px;
    }
    h2 {
      margin-top: 2rem;
    }
    li {
      margin-bottom: 0.4rem;
    }
    .tabla-desplazable {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.9rem;
    }
    th,
    td {
      padding: 0.45rem 0.5rem;
      border-bottom: 1px solid var(--borde);
      text-align: left;
      white-space: nowrap;
    }
    th {
      color: var(--texto-suave);
      font-weight: 600;
    }
    .num {
      text-align: right;
    }
    .administracion {
      margin-top: 2rem;
    }
    .administracion h2 {
      margin-top: 0;
    }
    .lanzar {
      display: flex;
      gap: 0.75rem;
      align-items: flex-end;
      flex-wrap: wrap;
    }
    .lanzar .campo {
      margin: 0;
      flex: 1 1 16rem;
    }
  `,
})
export class Datos {
  private readonly base = inject(CONFIGURACION).api;
  private readonly api = inject(Api);
  protected readonly sesion = inject(Sesion);
  protected readonly estado = httpResource<EstadoDeIngesta>(() => `${this.base}/v1/ingesta/estado`);
  protected readonly lanzando = signal(false);
  protected readonly mensaje = signal<string | null>(null);

  protected async lanzar(evento: Event, periodo: string): Promise<void> {
    evento.preventDefault();
    this.lanzando.set(true);
    this.mensaje.set(null);
    try {
      const valor = periodo.trim();
      await this.api.lanzarIngesta(valor ? 'paquete' : 'feed', valor || null);
      this.mensaje.set('Carga lanzada. Su avance aparece en la tabla de arriba al terminar.');
      this.estado.reload();
    } catch (e) {
      this.mensaje.set(mensajeDeError(e));
    } finally {
      this.lanzando.set(false);
    }
  }
}
