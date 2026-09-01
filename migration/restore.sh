#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/compose.yaml"
MONGODB_BACKUP_DIR="${SCRIPT_DIR}/mongodb-backup"
VOLUME_BACKUP_DIR="${SCRIPT_DIR}/docker-volume-backup"
UPLOAD_BACKUP_DIR="${SCRIPT_DIR}/uploads"
OPENCLIP_BACKUP_DIR="${SCRIPT_DIR}/openclip-backup"

log() { printf '[INFO] %s\n' "$*"; }
ok() { printf '[OK] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
fail() { printf '[FAIL] %s\n' "$*" >&2; exit 1; }

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

read_env_value() {
  local file="$1"
  local key="$2"
  [[ -f "${file}" ]] || return 0
  local line value
  line="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "${file}" | tail -n 1 || true)"
  [[ -n "${line}" ]] || return 0
  value="${line#*=}"
  value="$(printf '%s' "${value}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")"
  printf '%s' "${value}"
}

validate_docker() {
  command_exists docker || fail "Docker was not found. Install Docker Desktop first."
  docker info >/dev/null 2>&1 || fail "Docker is installed but not running."
  docker compose version >/dev/null 2>&1 || fail "Docker Compose was not found."
  [[ -f "${COMPOSE_FILE}" ]] || fail "compose.yaml was not found."
  ok "Docker and Compose are available"
}

create_networks() {
  if docker network inspect museum_app_default >/dev/null 2>&1; then
    ok "Docker network exists: museum_app_default"
  else
    docker network create museum_app_default >/dev/null
    ok "Docker network created: museum_app_default"
  fi
}

clear_volume() {
  find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} +
}

restore_one_volume() {
  local archive="$1"
  local file volume
  file="$(basename "${archive}")"
  volume="${file%.tar.gz}"
  log "Restoring Docker volume ${volume}"
  docker volume create "${volume}" >/dev/null
  docker run --rm \
    -v "${volume}:/volume" \
    -v "${VOLUME_BACKUP_DIR}:/backup:ro" \
    alpine sh -lc "find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xzf /backup/${file} -C /volume"
}

restore_docker_volumes() {
  shopt -s nullglob
  local archives=("${VOLUME_BACKUP_DIR}"/*.tar.gz)
  shopt -u nullglob
  if [[ "${#archives[@]}" -eq 0 ]]; then
    warn "No Docker volume archives found in migration/docker-volume-backup."
    return 0
  fi
  local archive
  for archive in "${archives[@]}"; do
    restore_one_volume "${archive}"
  done
  ok "Docker volume restore pass complete"
}

compose() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

start_infrastructure() {
  log "Starting MongoDB and Qdrant"
  compose up -d mongodb qdrant
  ok "Infrastructure containers started"
}

restore_mongodb() {
  local archive="${MONGODB_BACKUP_DIR}/all-databases.archive.gz"
  [[ -f "${archive}" ]] || { warn "No MongoDB archive found at migration/mongodb-backup/all-databases.archive.gz"; return 0; }

  local mongo_url
  mongo_url="${MONGODB_RESTORE_URI:-${MONGODB_URL:-$(read_env_value "${ROOT_DIR}/backend/.env" "MONGODB_URL")}}"
  mongo_url="${mongo_url:-mongodb://localhost:27018}"

  log "Restoring MongoDB archive with mongorestore"
  if command_exists mongorestore; then
    mongorestore "--uri=${mongo_url}" "--archive=${archive}" --gzip --drop
    ok "MongoDB restored from all-databases archive"
    return 0
  fi

  local cid
  cid="$(compose ps -q mongodb 2>/dev/null | head -n 1 || true)"
  if [[ -n "${cid}" ]] && docker exec "${cid}" sh -lc 'command -v mongorestore >/dev/null 2>&1'; then
    docker exec -i "${cid}" sh -lc 'if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then mongorestore -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip --drop; else mongorestore --archive --gzip --drop; fi' < "${archive}"
    ok "MongoDB restored through the MongoDB container"
  else
    warn "mongorestore was not found. Install MongoDB Database Tools and re-run restore."
  fi
}

restore_tar_to_root() {
  local archive="$1"
  log "Restoring $(basename "${archive}")"
  tar -xzf "${archive}" -C "${ROOT_DIR}"
}

restore_uploaded_files() {
  shopt -s nullglob
  local archives=("${UPLOAD_BACKUP_DIR}"/*.tar.gz)
  shopt -u nullglob
  if [[ "${#archives[@]}" -eq 0 ]]; then
    warn "No upload/static archives found in migration/uploads."
  else
    local archive
    for archive in "${archives[@]}"; do
      restore_tar_to_root "${archive}"
    done
  fi

  if [[ -d "${ROOT_DIR}/backend/uploads" ]]; then
    docker volume create museum_app_museum_backend_uploads >/dev/null
    docker run --rm \
      -v "museum_app_museum_backend_uploads:/volume" \
      -v "${ROOT_DIR}/backend/uploads:/source:ro" \
      alpine sh -lc "find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} + && cp -a /source/. /volume/"
    ok "Backend upload files restored to Docker volume"
  fi
}

restore_openclip_models() {
  docker volume create museum_app_museum_openclip_models >/dev/null
  docker volume create museum_app_museum_openclip_embeddings >/dev/null

  shopt -s nullglob
  local archives=("${OPENCLIP_BACKUP_DIR}"/*-cache.tar.gz)
  shopt -u nullglob
  if [[ "${#archives[@]}" -eq 0 ]]; then
    warn "No OpenCLIP model cache archives found in migration/openclip-backup."
  else
    local archive
    for archive in "${archives[@]}"; do
      log "Restoring OpenCLIP cache $(basename "${archive}")"
      docker run --rm \
        -v "museum_app_museum_openclip_models:/models" \
        -v "${OPENCLIP_BACKUP_DIR}:/backup:ro" \
        alpine sh -lc "tar -xzf /backup/$(basename "${archive}") -C /models"
    done
    ok "OpenCLIP model cache restored to Docker volume"
  fi

  if [[ -f "${OPENCLIP_BACKUP_DIR}/config/env-secret-files.tar.gz" ]]; then
    if [[ "${RESTORE_ENV_SECRETS:-0}" == "1" ]]; then
      tar -xzf "${OPENCLIP_BACKUP_DIR}/config/env-secret-files.tar.gz" -C "${ROOT_DIR}"
      ok "Secret-bearing env files restored because RESTORE_ENV_SECRETS=1 was set"
    else
      warn "Secret-bearing env backup exists but was not restored. Set RESTORE_ENV_SECRETS=1 if this is a trusted private machine."
    fi
  fi
}

start_application() {
  log "Starting application containers"
  compose up -d --build mongodb qdrant backend
  if [[ "${START_AI_TOOLS:-1}" == "1" ]]; then
    docker compose -f "${COMPOSE_FILE}" --profile ai-tools up -d --build openclip
  fi
  if [[ "${START_FRONTEND_BUILD:-0}" == "1" ]]; then
    docker compose -f "${COMPOSE_FILE}" --profile frontend run --rm frontend
  fi
  ok "Application containers started"
}

verify_system() {
  log "Verifying container state"
  compose ps
  warn "If MongoDB auth variables are set, ensure MONGODB_RESTORE_URI and MONGODB_URL include the matching credentials."
  ok "Restore sequence complete"
}

main() {
  validate_docker
  create_networks
  restore_docker_volumes
  start_infrastructure
  restore_mongodb
  restore_uploaded_files
  restore_openclip_models
  start_application
  verify_system
}

main "$@"
