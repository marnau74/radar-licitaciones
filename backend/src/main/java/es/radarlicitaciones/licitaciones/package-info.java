/**
 * Las licitaciones: cómo se guardan (versión a versión, desde la ingesta) y cómo se buscan (API pública y alertas).
 *
 * <p>Interfaz del módulo: {@link es.radarlicitaciones.licitaciones.RegistroDeLicitaciones} para escribir y
 * {@link es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones} para leer.
 */
@ApplicationModule(displayName = "Licitaciones")
package es.radarlicitaciones.licitaciones;

import org.springframework.modulith.ApplicationModule;
