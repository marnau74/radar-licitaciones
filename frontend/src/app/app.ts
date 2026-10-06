import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CONFIGURACION } from './nucleo/configuracion';
import { Sesion } from './nucleo/sesion';
import { Tema } from './nucleo/tema';

@Component({
  selector: 'rl-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly sesion = inject(Sesion);
  protected readonly tema = inject(Tema);
  private readonly api = inject(CONFIGURACION).api;

  /** Avisos sin leer, solo con sesión. */
  private readonly noLeidos = httpResource<{ total: number }>(() =>
    this.sesion.iniciada() ? `${this.api}/v1/avisos/no-leidos` : undefined,
  );
  protected readonly avisosSinLeer = computed(() =>
    this.noLeidos.hasValue() ? this.noLeidos.value().total : 0,
  );

  protected readonly nombreDelTema = computed(
    () =>
      ({ sistema: 'Tema del sistema', claro: 'Tema claro', oscuro: 'Tema oscuro' })[
        this.tema.preferencia()
      ],
  );
}
