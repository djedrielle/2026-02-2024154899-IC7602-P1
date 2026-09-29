#!/usr/bin/env bash
#
#                               Claude Generated
# Pruebas del DNS Interceptor
# ---------------------------
# Lanza una consulta por cada tipo de registro administrado (single, multi,
# weight, geo, round-trip), prueba la resolución de dominios externos y la
# concurrencia. Requiere el interceptor corriendo y las semillas cargadas
# (ver dns_interceptor/round_trip_seeds.sql).
#
# Uso:
#   ./pruebas_interceptor.sh [HOST] [PUERTO]
#
# Por defecto: HOST=127.0.0.1  PUERTO=15353
#

set -u

HOST="${1:-127.0.0.1}"
PORT="${2:-15353}"

# Dominios de prueba (deben existir en la base de datos con las semillas).
SINGLE="google.com"
MULTI="miro.com"
WEIGHT="demo-weighted-dns"
GEO="demo-geo-dns"
RTT="rtt.example.com"
EXTERNO="api.supabase.com"

command -v dig >/dev/null || { echo "Falta 'dig' (instala dnsutils / bind-utils)."; exit 1; }

DIG=(dig @"$HOST" -p "$PORT" +short)

titulo() { printf '\n\033[1;34m== %s ==\033[0m\n' "$1"; }

echo "Interceptor: ${HOST}:${PORT}"

titulo "single  ($SINGLE)  -> una única IP"
"${DIG[@]}" "$SINGLE"

titulo "multi  ($MULTI)  -> round-robin (6 consultas, la IP rota)"
for _ in $(seq 1 6); do "${DIG[@]}" "$MULTI"; done

titulo "weight  ($WEIGHT)  -> distribución ponderada (100 consultas)"
for _ in $(seq 1 20); do "${DIG[@]}" "$WEIGHT"; done | sort | uniq -c

titulo "geo  ($GEO)  -> IP según el país del cliente"
"${DIG[@]}" "$GEO"

titulo "round-trip  ($RTT)  -> IP de menor latencia (esperado 1.1.1.1)"
"${DIG[@]}" "$RTT"

titulo "externo  ($EXTERNO)  -> reenvío al DNS remoto"
"${DIG[@]}" "$EXTERNO"

titulo "concurrencia  -> 10 consultas simultáneas (no deben serializarse)"
time ( for _ in $(seq 1 10); do "${DIG[@]}" "$GEO" & done; wait )

titulo "Fin"
