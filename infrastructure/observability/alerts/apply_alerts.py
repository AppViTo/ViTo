"""Crea en Grafana Cloud las alertas de ViTo (T-I13/T041) desde vito-alerts.json.

vito-alerts.json se genera desde vito-alerts.yaml (ver ../README.md) y no se versiona.

Uso (PowerShell, desde esta carpeta):
  $env:GRAFANA_URL = "https://<stack>.grafana.net"
  $env:GRAFANA_TOKEN = "<token de service account>"
  python apply_alerts.py --dry-run        # muestra lo que haría, no cambia nada
  python apply_alerts.py                  # crea o actualiza el grupo (sin backup-missing)
  python apply_alerts.py --with-backup    # incluye backup-missing (cuando ya lleguen logs)
"""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DRY = "--dry-run" in sys.argv
WITH_BACKUP = "--with-backup" in sys.argv

URL = os.environ.get("GRAFANA_URL", "").rstrip("/")
TOKEN = os.environ.get("GRAFANA_TOKEN", "")
if not URL or not TOKEN:
    sys.exit("Faltan GRAFANA_URL o GRAFANA_TOKEN en el entorno.")


def api(method, path, body=None):
    req = urllib.request.Request(URL + path, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/json")
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        req.add_header("Content-Type", "application/json")
        # Deja las reglas editables desde la interfaz.
        req.add_header("X-Disable-Provenance", "true")
    try:
        with urllib.request.urlopen(req, data=data, timeout=30) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:500]}")


# 1) Fuentes de datos del stack
sources = api("GET", "/api/datasources")
def pick(kind, suffix):
    found = [d for d in sources if d.get("type") == kind and re.fullmatch(rf"grafanacloud-.+-{suffix}", d.get("name", ""))]
    if len(found) != 1:
        sys.exit(f"No encuentro una sola fuente {kind} grafanacloud-*-{suffix}: {[d.get('name') for d in found]}")
    return found[0]
prom, logs = pick("prometheus", "prom"), pick("loki", "logs")
print(f"Prometheus: {prom['name']} (uid {prom['uid']})")
print(f"Loki:       {logs['name']} (uid {logs['uid']})")
uid_map = {"grafanacloud-prom": prom["uid"], "grafanacloud-logs": logs["uid"]}

# 2) Plantilla
tpl = json.load(open(os.path.join(HERE, "vito-alerts.json"), encoding="utf-8"))
group = tpl["groups"][0]

# 3) Carpeta
folders = api("GET", "/api/folders")
folder = next((f for f in folders if f["title"] == group["folder"]), None)
if folder is None:
    print(f"Carpeta '{group['folder']}': se crea")
    folder = {"uid": "(nueva)"} if DRY else api("POST", "/api/folders", {"title": group["folder"]})
else:
    print(f"Carpeta '{group['folder']}': existe (uid {folder['uid']})")

# 4) Reglas
rules = []
for r in group["rules"]:
    if r["uid"] == "vito-backup-missing" and not WITH_BACKUP:
        print(f"  omitida: {r['title']} (usa --with-backup cuando lleguen los logs)")
        continue
    data = json.loads(json.dumps(r["data"]))
    for q in data:
        q["datasourceUid"] = uid_map.get(q["datasourceUid"], q["datasourceUid"])
    rules.append({
        "uid": r["uid"], "title": r["title"], "condition": r["condition"], "data": data,
        "noDataState": r["noDataState"], "execErrState": r["execErrState"], "for": r["for"],
        "annotations": r.get("annotations", {}), "labels": r.get("labels", {}),
        "folderUID": folder["uid"], "ruleGroup": group["name"], "orgID": group.get("orgId", 1),
    })
    print(f"  regla: {r['title']}")

interval = int(re.fullmatch(r"(\d+)m", group["interval"]).group(1)) * 60
body = {"title": group["name"], "folderUid": folder["uid"], "interval": interval, "rules": rules}

if DRY:
    print(f"\n--dry-run: no se envió nada ({len(rules)} reglas, intervalo {interval}s).")
    sys.exit(0)

path = f"/api/v1/provisioning/folder/{urllib.parse.quote(folder['uid'])}/rule-groups/{urllib.parse.quote(group['name'])}"
api("PUT", path, body)
print(f"\nOK: grupo '{group['name']}' con {len(rules)} reglas en la carpeta '{group['folder']}'.")
