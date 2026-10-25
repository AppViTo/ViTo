#!/usr/bin/env bash
# Prepara el VPS de producción (Fase Tracción, ADR-011). Lo corre un lead como root una
# sola vez; es idempotente: correrlo de nuevo no cambia nada (research R7, R16).
#
#   sudo ./bootstrap.sh --pubkey "<llave del pipeline>" --pubkey "<llave de un lead>"
#   sudo ./bootstrap.sh --check-only
#
# Los --pubkey son la lista completa de authorized_keys de `deploy`: una llave que no se
# pasa, se quita. Así se revoca un acceso (ADR-013, punto 7).
#
# Salida: 0 todo OK · 1 algún chequeo falló · 2 uso incorrecto · 3 sistema no soportado
#
# SC2317: las funciones de chequeo se llaman por nombre desde check(); shellcheck no lo ve
# shellcheck disable=SC2317
set -euo pipefail

VITO_ROOT=/opt/vito
DEPLOY_USER=deploy
SSHD_DROPIN=/etc/ssh/sshd_config.d/00-vito.conf
F2B_JAIL=/etc/fail2ban/jail.d/vito.local
AUTO_UPGRADES=/etc/apt/apt.conf.d/20auto-upgrades

# Lo que publica docker-compose.prod.yml: Caddy (80, 443 y 443/udp de HTTP/3) y SRS
# (1935 RTMP, 8000/udp WebRTC). Docker publica sus puertos por fuera de ufw: el
# aislamiento de Mongo, Redis y Kafka depende de que no tengan `ports:`.
UFW_ALLOW=(80/tcp 443/tcp 443/udp 1935/tcp 8000/udp)

pubkeys=()
check_only=false

usage() {
  sed -n '5,6p' "$0" | sed 's/^# \{0,3\}//' >&2
  exit 2
}

log() { printf '==> %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$1" >&2; exit "${2:-1}"; }

parse_args() {
  while (($#)); do
    case "$1" in
      --pubkey)
        (($# >= 2)) || usage
        pubkeys+=("$2")
        shift 2
        ;;
      --check-only) check_only=true; shift ;;
      -h | --help) usage ;;
      *) printf 'Opción desconocida: %s\n' "$1" >&2; usage ;;
    esac
  done
}

check_os() {
  [[ -r /etc/os-release ]] || die "no existe /etc/os-release" 3
  # shellcheck source=/dev/null
  . /etc/os-release
  if [[ "${ID:-}" != ubuntu || ! "${VERSION_ID:-}" =~ ^(22\.04|24\.04)$ ]]; then
    die "sistema no soportado: ${PRETTY_NAME:-desconocido} (se espera Ubuntu 22.04 o 24.04)" 3
  fi
}

validate_pubkey() {
  local key=$1 re='^(ssh-ed25519|ecdsa-sha2-nistp(256|384|521)|ssh-rsa) [A-Za-z0-9+/=]+( .*)?$'
  [[ "$key" =~ $re ]] \
    || die "--pubkey no parece una llave pública de OpenSSH: ${key:0:30}…" 2
}

install_packages() {
  log "Paquetes base"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg zstd ufw fail2ban python3-systemd \
    unattended-upgrades >/dev/null
}

install_docker() {
  log "Docker Engine y plugin Compose (repositorio oficial)"
  # shellcheck source=/dev/null
  . /etc/os-release
  install -m 0755 -d /etc/apt/keyrings
  if [[ ! -s /etc/apt/keyrings/docker.asc ]]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
  fi
  local arch list
  arch=$(dpkg --print-architecture)
  list="deb [arch=${arch} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable"
  if [[ "$(cat /etc/apt/sources.list.d/docker.list 2>/dev/null)" != "$list" ]]; then
    printf '%s\n' "$list" >/etc/apt/sources.list.d/docker.list
    apt-get update -qq
  fi
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin \
    docker-compose-plugin >/dev/null
  systemctl enable --now docker >/dev/null
}

configure_unattended_upgrades() {
  log "Actualizaciones de seguridad desatendidas"
  write_if_changed "$AUTO_UPGRADES" 0644 <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
}

configure_deploy_user() {
  log "Usuario ${DEPLOY_USER}"
  id "$DEPLOY_USER" &>/dev/null || useradd --create-home --shell /bin/bash "$DEPLOY_USER"
  usermod -aG docker "$DEPLOY_USER"
  local home
  home=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
  install -d -m 0700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$home/.ssh"
  printf '%s\n' "${pubkeys[@]}" | write_if_changed "$home/.ssh/authorized_keys" 0600 "$DEPLOY_USER"
}

configure_sshd() {
  log "SSH solo con llave"
  # Sin llave de root, desactivar contraseñas deja la consola de Contabo como única entrada
  [[ -s /root/.ssh/authorized_keys ]] \
    || die "root no tiene authorized_keys: instala tu llave (ssh-copy-id root@<host>) antes de seguir"
  # 00- para que gane sobre 50-cloud-init.conf: sshd se queda con el primer valor que lee
  write_if_changed "$SSHD_DROPIN" 0644 <<'EOF'
# Generado por infrastructure/vps/bootstrap.sh
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
  sshd -t
  # En 24.04 ssh arranca por socket: si el servicio no corre, la config aplica al próximo inicio
  systemctl try-reload-or-restart ssh
}

configure_fail2ban() {
  log "fail2ban en SSH"
  write_if_changed "$F2B_JAIL" 0644 <<'EOF'
[sshd]
enabled = true
backend = systemd
EOF
  systemctl enable fail2ban >/dev/null
  systemctl restart fail2ban
}

configure_ufw() {
  log "Firewall (ufw)"
  ufw default deny incoming >/dev/null
  ufw default allow outgoing >/dev/null
  ufw limit 22/tcp >/dev/null
  local rule
  for rule in "${UFW_ALLOW[@]}"; do
    ufw allow "$rule" >/dev/null
  done
  ufw --force enable >/dev/null
}

configure_vito_root() {
  log "${VITO_ROOT}"
  install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$VITO_ROOT" "$VITO_ROOT/releases"
  # El contenido lo escribe cd.yml desde DOTENV_PRODUCTION (research R15); aquí solo
  # se garantizan dueño y permisos.
  [[ -e "$VITO_ROOT/.env" ]] || : >"$VITO_ROOT/.env"
  chown "$DEPLOY_USER:$DEPLOY_USER" "$VITO_ROOT/.env"
  chmod 0600 "$VITO_ROOT/.env"
}

# write_if_changed <archivo> <modo> [dueño] < contenido: no toca el archivo si ya es igual
write_if_changed() {
  local dest=$1 mode=$2 owner=${3:-root} tmp
  tmp=$(mktemp)
  cat >"$tmp"
  if [[ -f "$dest" ]] && cmp -s "$tmp" "$dest"; then
    rm -f "$tmp"
  else
    install -m "$mode" -o "$owner" -g "$owner" "$tmp" "$dest"
    rm -f "$tmp"
  fi
}

# ---- chequeo final (también es --check-only: solo lee) ----

failures=0

check() {
  local label=$1
  shift
  if "$@" &>/dev/null; then
    printf '  [OK]    %s\n' "$label"
  else
    printf '  [FALLA] %s\n' "$label"
    failures=$((failures + 1))
  fi
}

has_docker() { command -v docker && docker compose version; }
docker_active() { systemctl is-active --quiet docker; }
ufw_active() { ufw status | grep -q '^Status: active'; }
ufw_rules() {
  local status rule
  status=$(ufw status)
  grep -Eq '^22/tcp +LIMIT' <<<"$status" || return 1
  for rule in "${UFW_ALLOW[@]}"; do
    grep -Eq "^${rule} +ALLOW" <<<"$status" || return 1
  done
}
fail2ban_active() { systemctl is-active --quiet fail2ban; }
auto_upgrades() { grep -q 'Unattended-Upgrade "1"' "$AUTO_UPGRADES"; }
deploy_in_docker() { id -nG "$DEPLOY_USER" | tr ' ' '\n' | grep -qx docker; }
deploy_has_keys() {
  local home
  home=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
  [[ -s "$home/.ssh/authorized_keys" ]]
}
sshd_no_passwords() {
  local cfg
  cfg=$(sshd -T)
  grep -qx 'passwordauthentication no' <<<"$cfg" \
    && grep -Eqx 'permitrootlogin (prohibit-password|without-password)' <<<"$cfg"
}
vito_root_ok() {
  [[ "$(stat -c '%U %a' "$VITO_ROOT/.env")" == "$DEPLOY_USER 600" ]] \
    && [[ "$(stat -c '%U' "$VITO_ROOT/releases")" == "$DEPLOY_USER" ]]
}

run_checks() {
  log "Chequeo"
  check "Docker y plugin Compose instalados" has_docker
  check "Servicio docker activo" docker_active
  check "ufw activo" ufw_active
  check "ufw: limit 22/tcp; allow ${UFW_ALLOW[*]}" ufw_rules
  check "fail2ban activo" fail2ban_active
  check "Actualizaciones desatendidas" auto_upgrades
  check "Usuario ${DEPLOY_USER} en el grupo docker" deploy_in_docker
  check "authorized_keys de ${DEPLOY_USER} con al menos una llave" deploy_has_keys
  check "SSH sin contraseñas y root solo con llave" sshd_no_passwords
  check "${VITO_ROOT}/.env 0600 de ${DEPLOY_USER} y releases/" vito_root_ok
  if ((failures)); then
    printf '%d chequeo(s) fallaron.\n' "$failures"
    return 1
  fi
  printf 'Todo OK.\n'
}

main() {
  parse_args "$@"
  check_os
  if "$check_only"; then
    run_checks
    return
  fi
  ((EUID == 0)) || die "hay que correrlo como root (sudo)" 2
  ((${#pubkeys[@]})) || die "falta al menos un --pubkey" 2
  local key
  for key in "${pubkeys[@]}"; do validate_pubkey "$key"; done

  install_packages
  install_docker
  configure_unattended_upgrades
  configure_deploy_user
  configure_sshd
  configure_fail2ban
  configure_ufw
  configure_vito_root
  run_checks
}

main "$@"
