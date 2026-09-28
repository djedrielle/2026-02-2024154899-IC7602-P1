"""
DNS API Mock
============
Simula el DNS API real (implementado en Java por otro integrante) para que el
DNS Interceptor pueda desarrollarse de forma independiente.

Responsabilidad del DNS API (según la arquitectura del equipo):
  - Entregar el REGISTRO de la base de datos si el dominio existe, o `false`
    si no existe. NO aplica la estrategia de resolución
    (single / multi / round-trip / weight / geo): esa lógica vive en el
    DNS Interceptor.
  - Reenviar paquetes DNS a un servidor DNS remoto (dns_resolver).

Endpoints:
  - GET  /api/exists        -> registro del dominio, o `false` si no existe
  - POST /api/dns_resolver  -> reenvía un paquete DNS (BASE64) a un DNS remoto

Datos:
  - records.json  (simula la colección "records" de Supabase/Firebase)

Config por variables de entorno:
  - REMOTE_DNS       IP del servidor DNS remoto (default 8.8.8.8)
  - REMOTE_DNS_PORT  puerto del DNS remoto (default 53)
  - UDP_TIMEOUT      timeout en segundos para el DNS remoto (default 5)
"""

import base64
import ipaddress
import json
import os
import socket
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

BASE_DIR = Path(__file__).parent
REMOTE_DNS = os.environ.get("REMOTE_DNS", "8.8.8.8")
REMOTE_DNS_PORT = int(os.environ.get("REMOTE_DNS_PORT", "53"))
UDP_TIMEOUT = float(os.environ.get("UDP_TIMEOUT", "5"))


# --------------------------------------------------------------------------- #
# Carga de datos (simula la colección "records" de la base de datos)
# --------------------------------------------------------------------------- #
def load_records() -> dict:
    """Devuelve los registros indexados por nombre de dominio."""
    with open(BASE_DIR / "records.json", encoding="utf-8") as f:
        return {rec["name"]: rec for rec in json.load(f)}


def load_geo() -> list:
    """Lista de rangos de la 'base' IP to Country."""
    with open(BASE_DIR / "ip_to_country.json", encoding="utf-8") as f:
        return json.load(f)


RECORDS = load_records()
GEO_DB = load_geo()


def ip_to_int(ip: str) -> int:
    """IPv4 con puntos -> entero (lanza AddressValueError si es inválida)."""
    return int(ipaddress.IPv4Address(ip))


def geo_lookup(ip: str) -> dict | None:
    """Devuelve el rango de ip_to_country que contiene la IP, o None."""
    ip_int = ip_to_int(ip)
    for entry in GEO_DB:
        if ip_to_int(entry["start_ip"]) <= ip_int <= ip_to_int(entry["end_ip"]):
            return entry
    return None

app = FastAPI(title="DNS API Mock", version="2.0")


# --------------------------------------------------------------------------- #
# Endpoints
# --------------------------------------------------------------------------- #
@app.get("/api/exists")
def exists(domain: str = Query(..., description="Dominio a verificar")):
    """Devuelve el registro completo del dominio si existe, o `false` si no.

    El DNS API NO resuelve la estrategia del registro: solo entrega el documento
    tal cual está en la base de datos. La lógica de single / multi / round-trip /
    weight / geo la aplica el DNS Interceptor con estos datos.
    """
    return RECORDS.get(domain, False)


@app.get("/api/ip_country")
def ip_country(ip: str = Query(..., description="IP a geolocalizar")):
    """Devuelve el país/ubicación de una IP según ip_to_country, o `false` si
    ningún rango la contiene. Solo entrega el dato: la lógica de geo / round-trip
    la aplica el interceptor.
    """
    try:
        loc = geo_lookup(ip)
    except ipaddress.AddressValueError:
        raise HTTPException(status_code=400, detail="invalid IPv4 address")

    if loc is None:
        return False

    # De momento solo devolvemos el codigo del pais ya que la base de datos IP to Country
    # solo trae este campo. Hay que desarrollar una estrategia para integrar la latencia y longitud
    return {
        #"ip": ip,
        "country_code": loc["country_code"],
        #"country_name": loc.get("country_name"),
        #"city": loc.get("city"),
        #"latitude": loc.get("latitude"),
        #"longitude": loc.get("longitude"),
    }


class ResolverRequest(BaseModel):
    data: str  # paquete DNS de la consulta, codificado en BASE64


@app.post("/api/dns_resolver")
def dns_resolver(req: ResolverRequest):
    """Decodifica el paquete DNS (BASE64), lo reenvía por UDP al DNS remoto y
    devuelve la respuesta también en BASE64 (reenvío real)."""
    try:
        query = base64.b64decode(req.data, validate=True)
    except (ValueError, base64.binascii.Error):
        raise HTTPException(status_code=400, detail="invalid base64 in 'data'")

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(UDP_TIMEOUT)
            sock.sendto(query, (REMOTE_DNS, REMOTE_DNS_PORT))
            response, _ = sock.recvfrom(4096)
    except socket.timeout:
        raise HTTPException(status_code=504, detail="upstream DNS timeout")
    except OSError as err:
        raise HTTPException(status_code=502, detail=f"upstream DNS error: {err}")

    return {"data": base64.b64encode(response).decode("ascii")}


@app.get("/")
def root():
    return {
        "service": "DNS API Mock",
        "remote_dns": f"{REMOTE_DNS}:{REMOTE_DNS_PORT}",
        "records": sorted(RECORDS.keys()),
        "endpoints": ["/api/exists", "/api/ip_country", "/api/dns_resolver"],
    }
