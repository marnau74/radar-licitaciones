"""Genera la migración de Flyway con los catálogos oficiales de CODICE.

Descarga las listas de códigos (formato Genericode) que publica la Plataforma de Contratación
del Sector Público y escribe backend/src/main/resources/db/migration/V2__catalogos.sql.
Solo hace falta volver a ejecutarlo si la Plataforma publica una versión nueva de una lista
(y entonces la migración nueva debe ser otra, V<n>__..., nunca editar la V2 ya aplicada).

Uso: python scripts/generar_catalogos.py
"""

from __future__ import annotations

import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

BASE = "https://contrataciondelestado.es/codice/cl"
LISTAS = {
    "cat_tipo_contrato": f"{BASE}/2.08/ContractCode-2.08.gc",
    "cat_procedimiento": f"{BASE}/2.07/SyndicationTenderingProcessCode-2.07.gc",
    "cat_estado": f"{BASE}/2.04/SyndicationContractFolderStatusCode-2.04.gc",
    "cat_resultado": f"{BASE}/2.09/TenderResultCode-2.09.gc",
    "cat_tipo_organo": f"{BASE}/2.10/ContractingAuthorityCode-2.10.gc",
    "cat_nuts": f"{BASE}/2.08/NUTS-2021.gc",
    "cat_cpv": f"{BASE}/2.04/CPV2008-2.04.gc",
}
SALIDA = Path(__file__).resolve().parents[1] / "backend/src/main/resources/db/migration/V2__catalogos.sql"

# Los nombres de los estados vienen en mayúsculas o con jerga interna; se dejan como se leen en la web.
ESTADOS = {
    "PRE": "Anuncio previo",
    "PUB": "Publicada",
    "EV": "Pendiente de adjudicación",
    "ADJ": "Adjudicada",
    "RES": "Resuelta",
    "ANUL": "Anulada",
}


def filas(url: str) -> list[tuple[str, str]]:
    peticion = urllib.request.Request(url, headers={"User-Agent": "radar-licitaciones"})
    with urllib.request.urlopen(peticion, timeout=60) as respuesta:
        raiz = ET.fromstring(respuesta.read())
    resultado = []
    for fila in raiz.iter("Row"):
        valores = {v.get("ColumnRef"): (v.findtext("SimpleValue") or "").strip() for v in fila.findall("Value")}
        resultado.append((valores["code"], valores.get("nombre", "").rstrip(".").strip()))
    return resultado


def literal(texto: str) -> str:
    return "'" + texto.replace("'", "''") + "'"


def main() -> None:
    lineas = [
        "-- Catálogos oficiales de CODICE (Plataforma de Contratación del Sector Público).",
        "-- Generado con scripts/generar_catalogos.py: no editar a mano.",
        "",
    ]
    for tabla, url in LISTAS.items():
        datos = filas(url)
        if tabla == "cat_estado":
            datos = [(codigo, ESTADOS.get(codigo, nombre)) for codigo, nombre in datos]
        if tabla == "cat_nuts":
            # Solo España: el país (nivel 0; hay licitaciones que no concretan más), NUTS 1 (grupos de
            # regiones), 2 (comunidades autónomas) y 3 (provincias).
            datos = [(c, n) for c, n in datos if c.startswith("ES")]
            valores = ",\n".join(f"  ({literal(c)}, {literal(n)}, {len(c) - 2})" for c, n in sorted(datos))
            lineas.append(f"INSERT INTO {tabla} (codigo, nombre, nivel) VALUES\n{valores};\n")
            continue
        valores = ",\n".join(f"  ({literal(c)}, {literal(n)})" for c, n in sorted(datos))
        lineas.append(f"INSERT INTO {tabla} (codigo, nombre) VALUES\n{valores};\n")
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text("\n".join(lineas), encoding="utf-8", newline="\n")
    print(f"{SALIDA} ({SALIDA.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
