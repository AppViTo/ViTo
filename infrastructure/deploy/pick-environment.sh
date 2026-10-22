#!/usr/bin/env bash
# Qué tipo de release es <sha> y a qué Environment va (feature 001, T055, research R5, R8).
# Corre en el runner; lo usan cd.yml y deploy-frontend.yml.
#
#   pick-environment.sh <sha> <deploy|preflight>
#
# Imprime, para $GITHUB_OUTPUT:
#   kind=<release|hotfix>
#   environment=<production|production-hotfix|production-preflight>
#
# Hotfix = el commit entró a main por un PR mergeado desde una rama hotfix/*. Cualquier otro
# caso (PR desde develop, sin PR, API caída) va a production, que exige la aprobación del
# otro lead (prevent self-review).
set -euo pipefail

(($# == 2)) || { sed -n '5p' "$0" | sed 's/^# \{0,3\}//' >&2; exit 2; }
sha=$1
mode=$2
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo "SHA inválido: $sha" >&2; exit 2; }

case "$mode" in
  preflight)
    printf 'kind=release\nenvironment=production-preflight\n'
    exit 0
    ;;
  deploy) ;;
  *) echo "mode inválido: $mode" >&2; exit 2 ;;
esac

head_ref=''
if prs=$(gh api "repos/${GITHUB_REPOSITORY:?}/commits/$sha/pulls"); then
  head_ref=$(jq -r '[.[] | select(.base.ref == "main" and .merged_at != null)][0].head.ref // ""' <<<"$prs")
else
  echo "::warning::no se pudo consultar el PR de $sha; se usa production" >&2
fi

if [[ "$head_ref" == hotfix/* ]]; then
  printf 'kind=hotfix\nenvironment=production-hotfix\n'
else
  printf 'kind=release\nenvironment=production\n'
fi
