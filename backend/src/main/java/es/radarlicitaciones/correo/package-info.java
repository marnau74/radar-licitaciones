/**
 * Envío de correo con bandeja de salida: quien quiere mandar un correo lo encola en su propia transacción ({@link
 * es.radarlicitaciones.correo.BandejaDeSalida}) y un proceso aparte lo envía con reintentos.
 */
@ApplicationModule(displayName = "Correo")
package es.radarlicitaciones.correo;

import org.springframework.modulith.ApplicationModule;
