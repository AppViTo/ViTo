#!/usr/bin/env bash
# Resumen de un despliegue o rollback en Markdown para $GITHUB_STEP_SUMMARY (feature 001,
# T019, FR-010). Recibe por stdin la línea JSON que deploy.sh agrega a deployments.log.
#
#   summary.sh < registro.json
set -euo pipefail

jq -r '
  def v: if . == null or . == "" then "—" else tostring end;
  def short: if type == "string" and length == 40 then .[0:12] else v end;
  ({succeeded: "✅", rolled_back: "↩️", failed: "❌", blocked: "⛔"}[.result] // "•") as $icon
  | "## \($icon) \(.type) · \(.result) (código \(.exit_code))",
    "",
    "| Campo | Valor |",
    "|-------|-------|",
    "| Release | `\(.sha | short)` |",
    "| Anterior | `\(.from_sha | short)` |",
    "| Tipo | \(.kind | v) |",
    "| Servicios reconstruidos | \(if (.changed | length) == 0 then "ninguno" else (.changed | join(", ")) end) |",
    "| Aprobó | \(.approver | v) |",
    "| Disparó | \(.triggered_by | v) |",
    "| Motivo | \(.reason | v) |",
    "| Forzado en evento en vivo | \(if .forced_live_event then "sí" else "no" end) |",
    "| Duración | \(.duration_s) s |",
    "",
    "<details><summary>Versión de cada servicio</summary>",
    "",
    "| Servicio | Versión |",
    "|----------|---------|",
    (.versions | to_entries[] | "| \(.key) | `\(.value | short)` |"),
    "",
    "</details>"
'
