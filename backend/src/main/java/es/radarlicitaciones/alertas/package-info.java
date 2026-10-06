/**
 * Alertas: búsquedas guardadas de cada usuario. Tras cada ingesta completada, las licitaciones nuevas en plazo que
 * encajan con una alerta se convierten en avisos y, si el usuario lo quiere, en un correo resumen.
 */
@ApplicationModule(displayName = "Alertas")
package es.radarlicitaciones.alertas;

import org.springframework.modulith.ApplicationModule;
