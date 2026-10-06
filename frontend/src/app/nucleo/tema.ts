import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

export type PreferenciaDeTema = 'sistema' | 'claro' | 'oscuro';

const CLAVE = 'radar.tema';

/** Claro, oscuro o lo que diga el sistema. La preferencia se recuerda en este navegador. */
@Injectable({ providedIn: 'root' })
export class Tema {
  private readonly documento = inject(DOCUMENT);
  readonly preferencia = signal<PreferenciaDeTema>(leer());

  constructor() {
    effect(() => {
      const preferencia = this.preferencia();
      const raiz = this.documento.documentElement;
      if (preferencia === 'sistema') {
        raiz.removeAttribute('data-theme');
      } else {
        raiz.setAttribute('data-theme', preferencia === 'oscuro' ? 'dark' : 'light');
      }
      try {
        localStorage.setItem(CLAVE, preferencia);
      } catch {
        // Sin almacenamiento (modo privado, bloqueado): se usa la preferencia solo en esta visita.
      }
    });
  }

  siguiente(): void {
    const orden: PreferenciaDeTema[] = ['sistema', 'claro', 'oscuro'];
    this.preferencia.update((actual) => orden[(orden.indexOf(actual) + 1) % orden.length]);
  }
}

function leer(): PreferenciaDeTema {
  try {
    const guardada = localStorage.getItem(CLAVE);
    return guardada === 'claro' || guardada === 'oscuro' ? guardada : 'sistema';
  } catch {
    return 'sistema';
  }
}
