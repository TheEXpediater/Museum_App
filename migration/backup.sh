#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/compose.yaml"
MONGODB_BACKUP_DIR="${SCRIPT_DIR}/mongodb-backup"
VOLUME_BACKUP_DIR="${SCRIPT_DIR}/docker-volume-backup"
UPLOAD_BACKUP_DIR="${SCRIPT_DIR}/uploads"
OPENCLIP_BACKUP_DIR="${SCRIPT_DIR}/openclip-backup"
INCLUDE_SECRETS=0

for arg in "$@"; do
  case "${arg}" in
    --include-secrets) INCLUDE_SECRETS=1 ;;
    -h|--help)
      echo "Usage: ./backup.sh [--include-secrets]"
      echo "Creates MongoDB, Docker volume, upload, and OpenCLIP backups under migration/."
      exit 0
      ;;
    *) echo "Unknown argument: ${arg}" >&2; exit 2 ;;
  esac
done

log() { printf '[INFO] %s\n' "$*"; }
ok() { printf '[OK] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }

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

safe_name() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9_.-' '_'
}

ensure_dirs() {
  mkdir -p "${MONGODB_BACKUP_DIR}" "${VOLUME_BACKUP_DIR}" "${UPLOAD_BACKUP_DIR}" "${OPENCLIP_BACKUP_DIR}"
  mkdir -p "${OPENCLIP_BACKUP_DIR}/config" "${OPENCLIP_BACKUP_DIR}/detected-files" "${OPENCLIP_BACKUP_DIR}/vector-indexes"
}

docker_ready() {
  command_exists docker && docker info >/dev/null 2>&1
}

compose() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

detect_mongo_container() {
  if docker_ready && [[ -f "${COMPOSE_FILE}" ]]; then
    local cid
    cid="$(compose ps -q mongodb 2>/dev/null | head -n 1 || true)"
    if [[ -n "${cid}" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "${cid}" 2>/dev/null || true)" == "true" ]]; then
      docker inspect -f '{{.Name}}' "${cid}" | sed 's#^/##'
      return 0
    fi
  fi
  docker ps --filter "name=museum-guide-mongodb" --format '{{.Names}}' 2>/dev/null | head -n 1
}

backup_mongodb() {
  log "Backing up MongoDB with mongodump"
  local env_file="${ROOT_DIR}/backend/.env"
  local mongo_url mongo_db mongo_container archive db_archive
  mongo_url="${MONGODB_URL:-$(read_env_value "${env_file}" "MONGODB_URL")}"
  mongo_url="${mongo_url:-mongodb://localhost:27018}"
  mongo_db="${MONGODB_DATABASE:-$(read_env_value "${env_file}" "MONGODB_DATABASE")}"
  mongo_container="$(detect_mongo_container || true)"
  archive="${MONGODB_BACKUP_DIR}/all-databases.archive.gz"

  if command_exists mongodump; then
    mongodump "--uri=${mongo_url}" "--archive=${archive}" --gzip
    ok "MongoDB all-database dump written to migration/mongodb-backup/all-databases.archive.gz"
    if [[ -n "${mongo_db}" ]]; then
      db_archive="${MONGODB_BACKUP_DIR}/${mongo_db}-with-users-and-roles.archive.gz"
      if mongodump "--uri=${mongo_url}" "--db=${mongo_db}" "--archive=${db_archive}" --gzip --dumpDbUsersAndRoles; then
        ok "MongoDB database users/roles dump written for configured database"
      else
        warn "Configured database user/role dump failed; all-database dump was still created."
        rm -f "${db_archive}"
      fi
    fi
  elif [[ -n "${mongo_container}" ]] && docker exec "${mongo_container}" sh -lc 'command -v mongodump >/dev/null 2>&1'; then
    docker exec "${mongo_container}" sh -lc 'if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then mongodump -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip; else mongodump --archive --gzip; fi' > "${archive}"
    ok "MongoDB dump written from Docker container"
  else
    warn "mongodump was not found and no MongoDB container with mongodump was available. Install MongoDB Database Tools and re-run this script."
  fi

  {
    echo "{"
    echo "  \"created_at_utc\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\","
    echo "  \"mongo_container_detected\": \"${mongo_container}\","
    echo "  \"mongodb_url_present\": $(if [[ -n "${mongo_url}" ]]; then echo true; else echo false; fi),"
    echo "  \"mongodb_database_present\": $(if [[ -n "${mongo_db}" ]]; then echo true; else echo false; fi)"
    echo "}"
  } > "${MONGODB_BACKUP_DIR}/manifest.json"
}

volume_exists() {
  docker volume inspect "$1" >/dev/null 2>&1
}

backup_one_volume() {
  local volume="$1"
  local output_name
  output_name="$(safe_name "${volume}").tar.gz"
  log "Backing up Docker volume ${volume}"
  docker run --rm \
    -v "${volume}:/volume:ro" \
    -v "${VOLUME_BACKUP_DIR}:/backup" \
    alpine sh -lc "cd /volume && tar -czf /backup/${output_name} ."
  printf '%s\t%s\n' "${volume}" "${output_name}" >> "${VOLUME_BACKUP_DIR}/manifest.tsv"
}

backup_docker_volumes() {
  if ! docker_ready; then
    warn "Docker is not available; skipping Docker volume backup."
    return 0
  fi

  : > "${VOLUME_BACKUP_DIR}/manifest.tsv"
  local required=(
    "museum_app_museum_mongodb_data"
    "museum_app_museum_qdrant_data"
    "museum_app_museum_backend_uploads"
    "museum_app_museum_openclip_models"
    "museum_app_museum_openclip_embeddings"
    "museum_app_museum_frontend_gradle_cache"
  )
  local discovered=()
  while IFS= read -r volume; do
    [[ -n "${volume}" ]] && discovered+=("${volume}")
  done < <(docker volume ls --filter "label=com.docker.compose.project=museum_app" --format '{{.Name}}' 2>/dev/null || true)

  local all=("${required[@]}" "${discovered[@]}")
  local seen=" "
  local volume
  for volume in "${all[@]}"; do
    [[ "${seen}" == *" ${volume} "* ]] && continue
    seen="${seen}${volume} "
    if volume_exists "${volume}"; then
      backup_one_volume "${volume}"
    else
      warn "Docker volume not found, skipped: ${volume}"
    fi
  done
  ok "Docker volume backup pass complete"
}

backup_path_tar() {
  local relative_path="$1"
  local output_name="$2"
  if [[ -e "${ROOT_DIR}/${relative_path}" ]]; then
    log "Backing up ${relative_path}"
    tar -czf "${UPLOAD_BACKUP_DIR}/${output_name}.tar.gz" -C "${ROOT_DIR}" "${relative_path}"
  else
    warn "Path not found, skipped: ${relative_path}"
  fi
}

backup_uploaded_and_static_files() {
  backup_path_tar "backend/uploads" "backend-uploads"
  backup_path_tar "android/app/src/main/assets" "android-assets"
  backup_path_tar "visitor_ui" "visitor-ui-source"
  backup_path_tar "visitor_images_source" "visitor-images-source"
  backup_path_tar "artifact_image_source" "artifact-image-source-zips"
  backup_path_tar "postman" "postman"
  for file in "ASSET_MANIFEST.json" "ANDROID_IMAGE_COPY_INSTRUCTIONS.txt" "museum_visitor_entry_assets.zip" "PSAU_Museum_Visitor_UI_Production_Assets.zip" "visitor_entry.png"; do
    if [[ -f "${ROOT_DIR}/${file}" ]]; then
      tar -czf "${UPLOAD_BACKUP_DIR}/$(safe_name "${file}").tar.gz" -C "${ROOT_DIR}" "${file}"
    fi
  done
  ok "Uploaded/static file backup pass complete"
}

backup_openclip_data() {
  log "Backing up OpenCLIP and AI data"
  cp -f "${ROOT_DIR}/backend/requirements-ai.txt" "${OPENCLIP_BACKUP_DIR}/config/requirements-ai.txt" 2>/dev/null || true
  cp -f "${ROOT_DIR}/backend/.env.example" "${OPENCLIP_BACKUP_DIR}/config/backend.env.example" 2>/dev/null || true

  {
    echo "Environment variable names detected in backend/.env:"
    if [[ -f "${ROOT_DIR}/backend/.env" ]]; then
      grep -E '^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=' "${ROOT_DIR}/backend/.env" | sed -E 's/[[:space:]]*=.*$//' | sed -E 's/^[[:space:]]*//'
    else
      echo "backend/.env not found"
    fi
    echo
    echo "Environment variable names detected in local.properties:"
    if [[ -f "${ROOT_DIR}/local.properties" ]]; then
      grep -E '^[[:space:]]*[A-Za-z_][A-Za-z0-9_.-]*[[:space:]]*=' "${ROOT_DIR}/local.properties" | sed -E 's/[[:space:]]*=.*$//' | sed -E 's/^[[:space:]]*//'
    else
      echo "local.properties not found"
    fi
  } > "${OPENCLIP_BACKUP_DIR}/config/environment-variable-names.txt"

  if [[ "${INCLUDE_SECRETS}" == "1" ]]; then
    local secret_paths=()
    [[ -f "${ROOT_DIR}/backend/.env" ]] && secret_paths+=("backend/.env")
    [[ -f "${ROOT_DIR}/local.properties" ]] && secret_paths+=("local.properties")
    if [[ "${#secret_paths[@]}" -gt 0 ]]; then
      tar -czf "${OPENCLIP_BACKUP_DIR}/config/env-secret-files.tar.gz" -C "${ROOT_DIR}" "${secret_paths[@]}"
      warn "Secret-bearing env files were copied because --include-secrets was provided. Protect this backup."
    fi
  fi

  local detected="${OPENCLIP_BACKUP_DIR}/detected-files/project-ai-files.txt"
  find "${ROOT_DIR}" \
    \( -path "${ROOT_DIR}/.git" -o -path "${ROOT_DIR}/.gradle" -o -path "${ROOT_DIR}/android/app/build" -o -path "${ROOT_DIR}/backend/.venv" -o -path "${SCRIPT_DIR}" \) -prune \
    -o -type f \( -iname '*.pt' -o -iname '*.pth' -o -iname '*.bin' -o -iname '*.onnx' -o -iname '*.safetensors' -o -iname '*.npy' -o -iname '*.npz' -o -iname '*.pkl' -o -iname '*.faiss' \) -print \
    | sed "s#^${ROOT_DIR}/##" > "${detected}"
  if [[ -s "${detected}" ]]; then
    tar -czf "${OPENCLIP_BACKUP_DIR}/project-ai-files.tar.gz" -C "${ROOT_DIR}" -T "${detected}"
  fi

  local cache_candidates=()
  [[ -n "${HF_HOME:-}" ]] && cache_candidates+=("${HF_HOME}")
  [[ -n "${TORCH_HOME:-}" ]] && cache_candidates+=("${TORCH_HOME}")
  [[ -n "${XDG_CACHE_HOME:-}" ]] && cache_candidates+=("${XDG_CACHE_HOME}/huggingface")
  cache_candidates+=("${HOME}/.cache/huggingface" "${HOME}/.cache/clip" "${HOME}/.cache/torch")

  local cache seen_caches=" " base parent
  for cache in "${cache_candidates[@]}"; do
    [[ -d "${cache}" ]] || continue
    [[ "${seen_caches}" == *" ${cache} "* ]] && continue
    seen_caches="${seen_caches}${cache} "
    base="$(basename "${cache}")"
    parent="$(cd -- "$(dirname -- "${cache}")" && pwd)"
    tar -czf "${OPENCLIP_BACKUP_DIR}/$(safe_name "${base}")-cache.tar.gz" -C "${parent}" "${base}"
  done

  if [[ -f "${VOLUME_BACKUP_DIR}/museum_app_museum_qdrant_data.tar.gz" ]]; then
    cp -f "${VOLUME_BACKUP_DIR}/museum_app_museum_qdrant_data.tar.gz" "${OPENCLIP_BACKUP_DIR}/vector-indexes/qdrant-volume.tar.gz"
  fi

  {
    echo "Created at UTC: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "Detected model/vector file list: detected-files/project-ai-files.txt"
    echo "Qdrant vector data is also backed up through Docker volume museum_app_museum_qdrant_data when present."
    echo "OpenCLIP defaults: model ViT-B-32, pretrained laion2b_s34b_b79k, Qdrant collection artifact_images."
  } > "${OPENCLIP_BACKUP_DIR}/manifest.txt"
  ok "OpenCLIP/AI backup pass complete"
}

main() {
  ensure_dirs
  backup_mongodb
  backup_docker_volumes
  backup_uploaded_and_static_files
  backup_openclip_data
  ok "Backup complete. Review migration/README.md before committing or moving backup artifacts."
}

main "$@"
