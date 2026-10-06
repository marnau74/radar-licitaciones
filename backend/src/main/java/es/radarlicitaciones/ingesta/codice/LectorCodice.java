package es.radarlicitaciones.ingesta.codice;

import es.radarlicitaciones.licitaciones.AnulacionLeida;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lee un fichero ATOM de la Plataforma de Contratación en streaming y devuelve sus elementos uno a uno. Un fichero tiene
 * hasta 500 entradas (unos 12 MB); un paquete mensual, casi 200 ficheros. Solo se construye en memoria la entrada que se
 * está leyendo.
 *
 * <p>El parser no resuelve DTD ni entidades externas: el XML viene de fuera y no se le deja leer ficheros locales ni hacer
 * peticiones (XXE).
 */
public final class LectorCodice implements Iterator<ElementoDelFeed>, AutoCloseable {

    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final String TOMBSTONES = "http://purl.org/atompub/tombstones/1.0";
    private static final Pattern NUMERO_FINAL = Pattern.compile("(\\d+)$");
    private static final XMLInputFactory FABRICA = fabricaSegura();

    private final XMLStreamReader xml;
    private final TraductorCodice traductor = new TraductorCodice();
    private ElementoDelFeed siguiente;
    private String enlaceSiguiente;
    private boolean terminado;

    public LectorCodice(InputStream entrada) {
        try {
            this.xml = FABRICA.createXMLStreamReader(entrada);
        } catch (XMLStreamException e) {
            throw new FicheroNoValido("No se puede abrir el XML del feed", e);
        }
    }

    private static XMLInputFactory fabricaSegura() {
        var fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        fabrica.setProperty(XMLInputFactory.IS_COALESCING, true);
        return fabrica;
    }

    /**
     * El fichero anterior en el tiempo ({@code <link rel="next">}), si lo hay. Se conoce en cuanto se ha leído la
     * cabecera, antes de la primera entrada.
     */
    public Optional<String> enlaceSiguiente() {
        return Optional.ofNullable(enlaceSiguiente);
    }

    @Override
    public boolean hasNext() {
        if (siguiente == null && !terminado) {
            siguiente = leerSiguiente();
            terminado = siguiente == null;
        }
        return siguiente != null;
    }

    @Override
    public ElementoDelFeed next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        var elemento = siguiente;
        siguiente = null;
        return elemento;
    }

    private ElementoDelFeed leerSiguiente() {
        try {
            while (xml.hasNext()) {
                if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                var espacio = xml.getNamespaceURI();
                var nombre = xml.getLocalName();
                // Los enlaces de cada entrada los consume leerArbol(): un <link> que llega aquí es de la cabecera.
                if (ATOM.equals(espacio) && "link".equals(nombre)) {
                    if ("next".equals(xml.getAttributeValue(null, "rel"))) {
                        enlaceSiguiente = xml.getAttributeValue(null, "href");
                    }
                } else if (TOMBSTONES.equals(espacio) && "deleted-entry".equals(nombre)) {
                    return anulacion(xml.getAttributeValue(null, "ref"), xml.getAttributeValue(null, "when"));
                } else if (ATOM.equals(espacio) && "entry".equals(nombre)) {
                    return entrada(leerArbol());
                }
            }
            return null;
        } catch (XMLStreamException e) {
            throw new FicheroNoValido(
                    "XML mal formado en la línea " + xml.getLocation().getLineNumber(), e);
        }
    }

    private ElementoDelFeed entrada(Nodo entrada) {
        var id = entrada.texto("id").orElse("(sin id)");
        try {
            return new ElementoDelFeed.Licitacion(traductor.traducir(entrada));
        } catch (EntradaNoValida | DateTimeParseException | NumberFormatException e) {
            return new ElementoDelFeed.Ilegible(id, e.getMessage());
        }
    }

    private ElementoDelFeed anulacion(String referencia, String cuando) {
        if (referencia == null || cuando == null) {
            return new ElementoDelFeed.Ilegible(String.valueOf(referencia), "Anulación sin referencia o sin fecha");
        }
        var numero = NUMERO_FINAL.matcher(referencia);
        if (!numero.find()) {
            return new ElementoDelFeed.Ilegible(referencia, "Anulación con una referencia sin número");
        }
        try {
            return new ElementoDelFeed.Anulacion(
                    new AnulacionLeida(Long.parseLong(numero.group(1)), OffsetDateTime.parse(cuando)));
        } catch (DateTimeParseException | NumberFormatException e) {
            return new ElementoDelFeed.Ilegible(referencia, "Anulación con fecha no válida: " + cuando);
        }
    }

    /** Lee el elemento actual completo (con sus descendientes) y lo devuelve como árbol. */
    private Nodo leerArbol() throws XMLStreamException {
        var pila = new ArrayDeque<Nodo>();
        var raiz = nodoActual();
        pila.push(raiz);
        while (!pila.isEmpty() && xml.hasNext()) {
            switch (xml.next()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    var hijo = nodoActual();
                    pila.peek().anadirHijo(hijo);
                    pila.push(hijo);
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                    pila.peek().anadirTexto(xml.getText());
                case XMLStreamConstants.END_ELEMENT -> pila.pop();
                default -> {
                    // Comentarios e instrucciones de proceso no interesan.
                }
            }
        }
        return raiz;
    }

    private Nodo nodoActual() {
        var atributos = new HashMap<String, String>();
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            atributos.put(xml.getAttributeLocalName(i), xml.getAttributeValue(i));
        }
        return new Nodo(xml.getLocalName(), atributos);
    }

    @Override
    public void close() {
        try {
            xml.close();
        } catch (XMLStreamException e) {
            // Cerrar un lector que ya ha fallado no aporta nada.
        }
    }
}
