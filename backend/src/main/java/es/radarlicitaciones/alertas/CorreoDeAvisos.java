package es.radarlicitaciones.alertas;

import es.radarlicitaciones.correo.BandejaDeSalida.CorreoNuevo;
import es.radarlicitaciones.correo.PropiedadesDeCorreo;
import es.radarlicitaciones.licitaciones.LicitacionResumen;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * El correo resumen de una ingesta para un usuario: una sección por alerta con sus licitaciones nuevas. En texto y en
 * HTML sencillo (tablas no, estilos en línea mínimos): se lee bien en cualquier cliente de correo.
 */
@Component
class CorreoDeAvisos {

    /** Por alerta, las que se enseñan en el correo; el resto, en la web. */
    static final int EN_EL_CORREO = 10;

    private static final Locale ES = Locale.of("es", "ES");
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter PLAZO = DateTimeFormatter.ofPattern("d/M/yyyy HH:mm", ES);

    record Seccion(String alerta, List<LicitacionResumen> licitaciones) {}

    private final PropiedadesDeCorreo propiedades;

    CorreoDeAvisos(PropiedadesDeCorreo propiedades) {
        this.propiedades = propiedades;
    }

    CorreoNuevo componer(String destinatario, List<Seccion> secciones) {
        var total = secciones.stream().mapToInt(s -> s.licitaciones().size()).sum();
        var asunto =
                total == 1 ? "1 licitación nueva para tus alertas" : total + " licitaciones nuevas para tus alertas";
        var web = propiedades.urlWeb().replaceAll("/+$", "");

        var texto = new StringBuilder();
        var html = new StringBuilder();
        html.append("<div style=\"font-family:system-ui,sans-serif;max-width:640px;color:#1f2933\">");
        html.append("<p>").append(escapar(asunto)).append(":</p>");
        texto.append(asunto).append(":\n");

        for (var seccion : secciones) {
            var lista = seccion.licitaciones();
            texto.append("\n== ")
                    .append(seccion.alerta())
                    .append(" (")
                    .append(lista.size())
                    .append(") ==\n");
            html.append("<h2 style=\"font-size:1.1em;margin:1.5em 0 .5em\">")
                    .append(escapar(seccion.alerta()))
                    .append(" (")
                    .append(lista.size())
                    .append(")</h2><ul style=\"padding-left:1.2em\">");
            for (var licitacion : lista.subList(0, Math.min(EN_EL_CORREO, lista.size()))) {
                var enlace = web + "/licitaciones/" + licitacion.id();
                var detalle = detalle(licitacion);
                texto.append("- ")
                        .append(licitacion.titulo())
                        .append("\n  ")
                        .append(detalle)
                        .append("\n  ")
                        .append(enlace)
                        .append("\n");
                html.append("<li style=\"margin-bottom:.8em\"><a href=\"")
                        .append(escapar(enlace))
                        .append("\">")
                        .append(escapar(licitacion.titulo()))
                        .append("</a><br><span style=\"color:#52606d\">")
                        .append(escapar(detalle))
                        .append("</span></li>");
            }
            html.append("</ul>");
            if (lista.size() > EN_EL_CORREO) {
                var mas = lista.size() - EN_EL_CORREO;
                texto.append("  ...y ").append(mas).append(" más: ").append(web).append("/avisos\n");
                html.append("<p><a href=\"")
                        .append(escapar(web))
                        .append("/avisos\">Y ")
                        .append(mas)
                        .append(" más</a></p>");
            }
        }

        var pie = "Recibes este correo porque tienes alertas en el Radar de licitaciones. Puedes cambiarlas o"
                + " desactivar el correo en " + web + "/alertas";
        texto.append("\n--\n").append(pie).append("\nDatos: Plataforma de Contratación del Sector Público.\n");
        html.append("<hr style=\"border:0;border-top:1px solid #d9e2ec;margin:2em 0 1em\"><p style=\"font-size:.85em;"
                        + "color:#52606d\">")
                .append(escapar(pie))
                .append("<br>Datos: Plataforma de Contratación del Sector Público.</p></div>");
        return new CorreoNuevo(destinatario, asunto, texto.toString(), html.toString());
    }

    private static String detalle(LicitacionResumen licitacion) {
        var partes = new StringBuilder(licitacion.organo());
        if (licitacion.importeSinIva() != null) {
            partes.append(" · ").append(euros(licitacion.importeSinIva())).append(" sin IVA");
        }
        if (licitacion.plazoPresentacion() != null) {
            partes.append(" · plazo hasta el ")
                    .append(PLAZO.format(licitacion.plazoPresentacion().atZoneSameInstant(ZONA)));
        }
        return partes.toString();
    }

    private static String euros(BigDecimal importe) {
        var formato = NumberFormat.getCurrencyInstance(ES);
        formato.setMaximumFractionDigits(importe.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return formato.format(importe);
    }

    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }
}
