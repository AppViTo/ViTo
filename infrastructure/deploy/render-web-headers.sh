#!/usr/bin/env bash
# Completa los orígenes de la CSP en el _headers de la web (T-I11; feature 001, T031 y T032).
# Lo corre deploy-frontend.yml sobre build/web/_headers: el dominio no va en el repo, sale de
# las variables del Environment.
#
#   VITO_API_BASE_URL=… VITO_WS_BASE_URL=… VITO_MEDIA_ORIGIN=… render-web-headers.sh <_headers>
#
# Salida: 0 OK · 1 variable vacía, con otro esquema o con orígenes sin completar · 2 uso incorrecto
set -euo pipefail

(($# == 1)) || { sed -n '6p' "$0" | sed 's/^# \{0,3\}//' >&2; exit 2; }
file=$1
[[ -f "$file" ]] || { echo "No existe: $file" >&2; exit 2; }

# origin <url> <esquema>: imprime esquema://host[:puerto] sin la ruta. El host solo admite
# letras, dígitos, puntos y guiones, así que el valor es seguro como reemplazo de sed.
origin() {
  [[ "$1" =~ ^$2://([A-Za-z0-9.-]+(:[0-9]+)?)(/.*)?$ ]] || return 1
  printf '%s://%s' "$2" "${BASH_REMATCH[1]}"
}

api=$(origin "${VITO_API_BASE_URL:-}" https) || { echo "VITO_API_BASE_URL debe ser https://<host>[/ruta]" >&2; exit 1; }
ws=$(origin "${VITO_WS_BASE_URL:-}" wss) || { echo "VITO_WS_BASE_URL debe ser wss://<host>[/ruta]" >&2; exit 1; }
media=$(origin "${VITO_MEDIA_ORIGIN:-}" https) || { echo "VITO_MEDIA_ORIGIN debe ser https://<host>" >&2; exit 1; }

sed -i -e "s|__API_ORIGIN__|$api|g" -e "s|__WS_ORIGIN__|$ws|g" -e "s|__MEDIA_ORIGIN__|$media|g" "$file"

if grep -q '__[A-Z_]*_ORIGIN__' "$file"; then
  echo "Quedan orígenes sin completar en $file:" >&2
  grep -o '__[A-Z_]*_ORIGIN__' "$file" | sort -u >&2
  exit 1
fi
grep -m1 'Content-Security-Policy' "$file"
