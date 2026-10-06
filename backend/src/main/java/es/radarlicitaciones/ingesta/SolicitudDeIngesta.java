package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.compartido.PeticionNoValida;
import java.nio.file.Path;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/** Qué se quiere ingerir. */
sealed interface SolicitudDeIngesta {

    String origen();

    /** Valor del parámetro del job que distingue esta solicitud (periodo, ruta...), o nulo. */
    String valor();

    /** El feed vivo, desde donde se quedó la última vez. */
    record Feed() implements SolicitudDeIngesta {
        @Override
        public String origen() {
            return "feed";
        }

        @Override
        public String valor() {
            return null;
        }
    }

    /**
     * Un paquete histórico: un año entero (AAAA) o un mes del año en curso (AAAAMM).
     *
     * @param periodo AAAA o AAAAMM
     */
    record Paquete(String periodo) implements SolicitudDeIngesta {

        private static final Pattern FORMATO = Pattern.compile("^\\d{4}(\\d{2})?$");
        private static final int PRIMER_ANO = 2012;

        public Paquete {
            if (periodo == null || !FORMATO.matcher(periodo).matches()) {
                throw new PeticionNoValida("El periodo es AAAA (un año) o AAAAMM (un mes)");
            }
            var ano = Integer.parseInt(periodo.substring(0, 4));
            if (ano < PRIMER_ANO || ano > Year.now().getValue()) {
                throw new PeticionNoValida("Hay paquetes desde " + PRIMER_ANO + " hasta el año en curso");
            }
            if (periodo.length() == 6) {
                var mes = Integer.parseInt(periodo.substring(4));
                if (mes < 1 || mes > 12 || YearMonth.of(ano, mes).isAfter(YearMonth.now())) {
                    throw new PeticionNoValida("Mes no válido: " + periodo);
                }
            }
        }

        static Paquete delMes(YearMonth mes) {
            return new Paquete(mes.format(DateTimeFormatter.ofPattern("yyyyMM")));
        }

        @Override
        public String origen() {
            return "paquete";
        }

        @Override
        public String valor() {
            return periodo;
        }
    }

    /** Un fichero o carpeta local (ATOM o ZIP). No se puede pedir por la API: solo por configuración. */
    record Fichero(Path ruta) implements SolicitudDeIngesta {
        public Fichero {
            Objects.requireNonNull(ruta, "ruta");
        }

        @Override
        public String origen() {
            return "fichero";
        }

        @Override
        public String valor() {
            return ruta.toAbsolutePath().normalize().toString();
        }
    }
}
