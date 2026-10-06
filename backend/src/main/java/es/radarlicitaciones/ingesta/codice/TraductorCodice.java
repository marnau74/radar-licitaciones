package es.radarlicitaciones.ingesta.codice;

import es.radarlicitaciones.licitaciones.LicitacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida.DocumentoLeido;
import es.radarlicitaciones.licitaciones.LicitacionLeida.LoteLeido;
import es.radarlicitaciones.licitaciones.LicitacionLeida.ResultadoLeido;
import es.radarlicitaciones.licitaciones.LicitacionLeida.TipoDocumento;
import es.radarlicitaciones.licitaciones.OrganoLeido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Traduce una entrada ATOM con su {@code ContractFolderStatus} de CODICE a una {@link LicitacionLeida}.
 *
 * <p>Criterio: lo imprescindible (número, expediente, título, estado y órgano) tiene que estar o la entrada se descarta
 * como ilegible; todo lo demás es opcional y, si viene mal formado (un importe que no es un número, una fecha rota), se
 * deja vacío en vez de perder la licitación entera.
 */
final class TraductorCodice {

    /** Las horas de la fuente (plazos) son de la península. */
    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final Pattern NUMERO_FINAL = Pattern.compile("(\\d+)$");
    private static final Pattern CPV = Pattern.compile("^(\\d{8})");
    private static final Pattern NUTS = Pattern.compile("^[A-Z]{2}[0-9A-Z]{0,3}$");
    /** numeric(18, 2): un importe mayor no cabe y es, con seguridad, un error de la fuente. */
    private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("1E16");

    private static final int MAX_DOCUMENTOS = 30;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    LicitacionLeida traducir(Nodo entrada) {
        var id = entrada.texto("id")
                .map(NUMERO_FINAL::matcher)
                .filter(Matcher::find)
                .map(m -> Long.parseLong(m.group(1)))
                .orElseThrow(() -> new EntradaNoValida("Entrada sin número de licitación"));
        var actualizadaEn = entrada.texto("updated")
                .map(OffsetDateTime::parse)
                .orElseThrow(() -> new EntradaNoValida("Entrada sin fecha de actualización"));
        var carpeta = entrada.hijo("ContractFolderStatus")
                .orElseThrow(() -> new EntradaNoValida("Entrada sin ContractFolderStatus"));
        var proyecto = carpeta.hijo("ProcurementProject");

        var expediente = carpeta.texto("ContractFolderID")
                .orElseThrow(() -> new EntradaNoValida("Licitación sin número de expediente"));
        var estado = carpeta.texto("ContractFolderStatusCode")
                .orElseThrow(() -> new EntradaNoValida("Licitación sin estado"));
        var titulo = proyecto.flatMap(p -> p.texto("Name"))
                .or(() -> entrada.texto("title"))
                .orElseThrow(() -> new EntradaNoValida("Licitación sin título"));
        var organo = organo(carpeta.hijo("LocatedContractingParty")
                .orElseThrow(() -> new EntradaNoValida("Licitación sin órgano de contratación")));

        var lotes = lotes(carpeta);
        var cpv = new LinkedHashSet<String>(proyecto.map(TraductorCodice::cpv).orElse(List.of()));
        lotes.forEach(lote -> cpv.addAll(lote.cpv()));

        var presupuesto = proyecto.flatMap(p -> p.hijo("BudgetAmount"));
        var lugarEjecucion = proyecto.flatMap(p -> p.hijo("RealizedLocation"));

        return new LicitacionLeida(
                id,
                expediente,
                titulo,
                entrada.hijo("link").flatMap(l -> l.atributo("href")).orElse(null),
                estado,
                proyecto.flatMap(p -> p.texto("TypeCode")).orElse(null),
                proyecto.flatMap(p -> p.texto("SubTypeCode")).orElse(null),
                carpeta.texto("TenderingProcess", "ProcedureCode").orElse(null),
                organo,
                presupuesto.flatMap(p -> importe(p, "TaxExclusiveAmount")).orElse(null),
                presupuesto.flatMap(p -> importe(p, "TotalAmount")).orElse(null),
                presupuesto
                        .flatMap(p -> importe(p, "EstimatedOverallContractAmount"))
                        .orElse(null),
                List.copyOf(cpv),
                lugarEjecucion.flatMap(TraductorCodice::nuts).orElse(null),
                lugarEjecucion.flatMap(TraductorCodice::lugar).orElse(null),
                plazo(carpeta).orElse(null),
                fechaPublicacion(carpeta).orElse(null),
                proyecto.flatMap(p -> p.hijo("PlannedPeriod"))
                        .flatMap(TraductorCodice::duracion)
                        .orElse(null),
                financiacionUe(carpeta),
                lotes,
                resultados(carpeta),
                documentos(carpeta),
                actualizadaEn);
    }

    private static OrganoLeido organo(Nodo localizado) {
        var parte = localizado.hijo("Party");
        var nombre = parte.flatMap(p -> p.texto("PartyName", "Name"))
                .orElseThrow(() -> new EntradaNoValida("Órgano de contratación sin nombre"));
        var identificadores = new LinkedHashMap<String, String>();
        parte.ifPresent(p -> p.hijos("PartyIdentification")
                .forEach(i -> i.hijo("ID")
                        .ifPresent(id -> id.texto()
                                .ifPresent(valor -> identificadores.putIfAbsent(
                                        id.atributo("schemeName").orElse(""), valor)))));
        var nif = identificadores.get("NIF");
        var dir3 = identificadores.get("DIR3");
        var idPlataforma = identificadores.get("ID_PLATAFORMA");
        String clave;
        if (idPlataforma != null) {
            clave = "PLAT:" + idPlataforma;
        } else if (dir3 != null) {
            clave = "DIR3:" + dir3;
        } else if (nif != null) {
            // Un mismo NIF lo comparten a veces varios órganos (un ayuntamiento y su junta de gobierno).
            clave = "NIF:" + nif + ":" + nombre;
        } else {
            clave = "NOMBRE:" + nombre;
        }

        var jerarquia = new ArrayList<String>();
        var superior = localizado.hijo("ParentLocatedParty");
        while (superior.isPresent()) {
            superior.get().texto("PartyName", "Name").ifPresent(jerarquia::add);
            superior = superior.get().hijo("ParentLocatedParty");
        }

        return new OrganoLeido(
                clave,
                nombre,
                nif,
                dir3,
                idPlataforma,
                localizado.texto("ContractingPartyTypeCode").orElse(null),
                parte.flatMap(p -> p.texto("PostalAddress", "CityName")).orElse(null),
                parte.flatMap(p -> p.texto("PostalAddress", "PostalZone")).orElse(null),
                parte.flatMap(p -> p.texto("WebsiteURI")).orElse(null),
                localizado.texto("BuyerProfileURIID").orElse(null),
                jerarquia);
    }

    private static List<LoteLeido> lotes(Nodo carpeta) {
        // La clave de un lote en la base de datos es su número: si la fuente lo repite, vale el primero.
        var lotes = new LinkedHashMap<String, LoteLeido>();
        var posicion = 0;
        for (var lote : carpeta.hijos("ProcurementProjectLot")) {
            posicion++;
            var numero = lote.texto("ID").orElse(String.valueOf(posicion));
            var proyecto = lote.hijo("ProcurementProject");
            lotes.putIfAbsent(
                    numero,
                    new LoteLeido(
                            numero,
                            proyecto.flatMap(p -> p.texto("Name")).orElse(null),
                            proyecto.flatMap(p -> p.hijo("BudgetAmount"))
                                    .flatMap(p -> importe(p, "TaxExclusiveAmount"))
                                    .orElse(null),
                            proyecto.map(TraductorCodice::cpv).orElse(List.of()),
                            proyecto.flatMap(p -> p.hijo("RealizedLocation"))
                                    .flatMap(TraductorCodice::nuts)
                                    .orElse(null)));
        }
        return List.copyOf(lotes.values());
    }

    private static List<ResultadoLeido> resultados(Nodo carpeta) {
        return carpeta.hijos("TenderResult").stream()
                .map(resultado -> {
                    var ganadores = resultado.hijos("WinningParty");
                    // En una UTE hay varios adjudicatarios: se enseñan todos los nombres.
                    var adjudicatario = ganadores.stream()
                            .map(g -> g.texto("PartyName", "Name"))
                            .flatMap(Optional::stream)
                            .distinct()
                            .collect(Collectors.joining(" / "));
                    var proyecto = resultado.hijo("AwardedTenderedProject");
                    var total = proyecto.flatMap(p -> p.hijo("LegalMonetaryTotal"));
                    return new ResultadoLeido(
                            proyecto.flatMap(p -> p.texto("ProcurementProjectLotID"))
                                    .orElse(null),
                            resultado.texto("ResultCode").orElse(null),
                            resultado
                                    .texto("AwardDate")
                                    .flatMap(TraductorCodice::fecha)
                                    .orElse(null),
                            resultado
                                    .texto("ReceivedTenderQuantity")
                                    .flatMap(TraductorCodice::entero)
                                    .orElse(null),
                            adjudicatario.isEmpty() ? null : adjudicatario,
                            ganadores.stream()
                                    .map(g -> g.texto("PartyIdentification", "ID"))
                                    .flatMap(Optional::stream)
                                    .findFirst()
                                    .orElse(null),
                            total.flatMap(t -> importe(t, "TaxExclusiveAmount")).orElse(null),
                            total.flatMap(t -> importe(t, "PayableAmount")).orElse(null),
                            resultado
                                    .texto("SMEAwardedIndicator")
                                    .map(Boolean::parseBoolean)
                                    .orElse(null));
                })
                .toList();
    }

    private static List<DocumentoLeido> documentos(Nodo carpeta) {
        var documentos = Stream.of(
                        documentos(carpeta.hijos("LegalDocumentReference"), TipoDocumento.PLIEGO_ADMINISTRATIVO),
                        documentos(carpeta.hijos("TechnicalDocumentReference"), TipoDocumento.PLIEGO_TECNICO),
                        documentos(carpeta.hijos("AdditionalDocumentReference"), TipoDocumento.OTRO),
                        documentos(
                                carpeta.todos("GeneralDocument", "GeneralDocumentDocumentReference"),
                                TipoDocumento.OTRO))
                .flatMap(List::stream)
                .toList();
        return documentos.size() > MAX_DOCUMENTOS ? documentos.subList(0, MAX_DOCUMENTOS) : documentos;
    }

    private static List<DocumentoLeido> documentos(List<Nodo> referencias, TipoDocumento tipo) {
        return referencias.stream()
                .flatMap(referencia -> referencia.buscar("URI").flatMap(Nodo::texto).stream()
                        .map(url -> new DocumentoLeido(
                                tipo,
                                referencia
                                        .buscar("FileName")
                                        .flatMap(Nodo::texto)
                                        .or(() -> referencia.texto("ID"))
                                        .orElse(null),
                                url)))
                .toList();
    }

    /** Fin del plazo de presentación de ofertas, en hora peninsular. Sin hora, se entiende el final del día. */
    private static Optional<OffsetDateTime> plazo(Nodo carpeta) {
        var periodo = carpeta.hijo("TenderingProcess", "TenderSubmissionDeadlinePeriod");
        var dia = periodo.flatMap(p -> p.texto("EndDate")).flatMap(TraductorCodice::fecha);
        if (dia.isEmpty()) {
            return Optional.empty();
        }
        var hora = periodo.flatMap(p -> p.texto("EndTime"))
                .flatMap(TraductorCodice::hora)
                .orElse(LocalTime.of(23, 59, 59));
        return Optional.of(dia.get().atTime(hora).atZone(ZONA).toOffsetDateTime());
    }

    /**
     * Primera publicación del anuncio de licitación (DOC_CN). Si no lo hay (anuncios previos, licitaciones antiguas), la
     * primera publicación de cualquier tipo.
     */
    private static Optional<LocalDate> fechaPublicacion(Nodo carpeta) {
        var avisos = carpeta.hijos("ValidNoticeInfo");
        var deLicitacion = primeraFecha(avisos.stream()
                .filter(a -> a.texto("NoticeTypeCode").filter("DOC_CN"::equals).isPresent())
                .toList());
        return deLicitacion.isPresent() ? deLicitacion : primeraFecha(avisos);
    }

    private static Optional<LocalDate> primeraFecha(List<Nodo> avisos) {
        return avisos.stream()
                .flatMap(
                        a -> a
                                .todos(
                                        "AdditionalPublicationStatus",
                                        "AdditionalPublicationDocumentReference",
                                        "IssueDate")
                                .stream())
                .map(Nodo::texto)
                .flatMap(Optional::stream)
                .map(TraductorCodice::fecha)
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
    }

    private static boolean financiacionUe(Nodo carpeta) {
        return Stream.concat(
                        carpeta.todos("TenderingTerms", "FundingProgramCode").stream(),
                        carpeta.todos("ProcurementProjectLot", "TenderingTerms", "FundingProgramCode").stream())
                .map(Nodo::texto)
                .flatMap(Optional::stream)
                .anyMatch(codigo -> !"NO-EU".equals(codigo));
    }

    private static Optional<String> duracion(Nodo periodo) {
        var medida = periodo.hijo("DurationMeasure");
        var cantidad = medida.flatMap(Nodo::texto).flatMap(TraductorCodice::numero);
        if (cantidad.isPresent()) {
            var valor = cantidad.get().stripTrailingZeros().toPlainString();
            var uno = cantidad.get().compareTo(BigDecimal.ONE) == 0;
            var unidad = medida.flatMap(m -> m.atributo("unitCode")).orElse("");
            return Optional.of(
                    switch (unidad) {
                        case "DAY" -> valor + (uno ? " día" : " días");
                        case "WEE" -> valor + (uno ? " semana" : " semanas");
                        case "MON" -> valor + (uno ? " mes" : " meses");
                        case "ANN" -> valor + (uno ? " año" : " años");
                        default -> (valor + " " + unidad).strip();
                    });
        }
        var inicio = periodo.texto("StartDate").flatMap(TraductorCodice::fecha);
        var fin = periodo.texto("EndDate").flatMap(TraductorCodice::fecha);
        if (inicio.isPresent() && fin.isPresent()) {
            return Optional.of("Del " + DIA.format(inicio.get()) + " al " + DIA.format(fin.get()));
        }
        return fin.map(f -> "Hasta el " + DIA.format(f));
    }

    private static List<String> cpv(Nodo proyecto) {
        return proyecto.todos("RequiredCommodityClassification", "ItemClassificationCode").stream()
                .map(Nodo::texto)
                .flatMap(Optional::stream)
                .map(CPV::matcher)
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .distinct()
                .toList();
    }

    private static Optional<String> nuts(Nodo lugar) {
        return lugar.texto("CountrySubentityCode").filter(c -> NUTS.matcher(c).matches());
    }

    /** El municipio y la zona del lugar de ejecución, tal como los escribe el órgano. */
    private static Optional<String> lugar(Nodo lugar) {
        var partes = Stream.of(lugar.texto("Address", "CityName"), lugar.texto("CountrySubentity"))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        return partes.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", partes));
    }

    private static Optional<BigDecimal> importe(Nodo padre, String nombre) {
        return padre.texto(nombre)
                .flatMap(TraductorCodice::numero)
                .filter(n -> n.signum() >= 0 && n.compareTo(IMPORTE_MAXIMO) < 0);
    }

    private static Optional<BigDecimal> numero(String texto) {
        try {
            return Optional.of(new BigDecimal(texto));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> entero(String texto) {
        try {
            return Optional.of(Integer.parseInt(texto)).filter(n -> n >= 0);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Las fechas llegan como {@code 2026-07-03} y a veces con zona ({@code 2026-07-03+02:00}). */
    private static Optional<LocalDate> fecha(String texto) {
        if (texto.length() < 10) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto.substring(0, 10)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalTime> hora(String texto) {
        try {
            return Optional.of(LocalTime.parse(texto.length() > 8 ? texto.substring(0, 8) : texto));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
