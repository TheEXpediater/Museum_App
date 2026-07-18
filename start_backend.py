from __future__ import annotations

import argparse
import getpass
import secrets
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = ROOT_DIR / "backend"
VENV_DIR = BACKEND_DIR / ".venv"
VENV_PYTHON = VENV_DIR / "Scripts" / "python.exe"
REQUIREMENTS_FILE = BACKEND_DIR / "requirements.txt"
AI_REQUIREMENTS_FILE = BACKEND_DIR / "requirements-ai.txt"
ENV_EXAMPLE_FILE = BACKEND_DIR / ".env.example"
ENV_FILE = BACKEND_DIR / ".env"
COMPOSE_FILE = ROOT_DIR / "compose.yaml"

MONGODB_PORT = 27017
QDRANT_REST_PORT = 6333
QDRANT_GRPC_PORT = 6334
FASTAPI_PORT = 8000
MONGODB_SERVICE = "mongodb"
QDRANT_SERVICE = "qdrant"
LOCAL_MONGODB_URI = "mongodb://localhost:27017"
DEFAULT_QDRANT_URL = "http://localhost:6333"
AI_REQUIREMENTS = [
    "torch==2.13.0",
    "torchvision==0.28.0",
    "open_clip_torch==3.3.0",
    "qdrant-client==1.18.0",
]
AI_ENV_DEFAULTS = {
    "AI_ENABLED": "true",
    "QDRANT_URL": DEFAULT_QDRANT_URL,
    "QDRANT_API_KEY": "",
    "QDRANT_COLLECTION": "artifact_images",
    "QDRANT_DISTANCE": "cosine",
    "OPENCLIP_MODEL_NAME": "ViT-B-32",
    "OPENCLIP_PRETRAINED": "laion2b_s34b_b79k",
    "OPENCLIP_DEVICE": "auto",
    "AI_MODEL_DOWNLOAD_ALLOWED": "true",
}


class LauncherError(Exception):
    pass


def ok(message: str) -> None:
    print(f"[OK] {message}", flush=True)


def info(message: str) -> None:
    print(f"[INFO] {message}", flush=True)


def error(message: str) -> None:
    print(f"[ERROR] {message}", file=sys.stderr, flush=True)


def run_process(
    args: list[str],
    *,
    cwd: Path | None = None,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            args,
            cwd=str(cwd) if cwd else None,
            text=True,
            capture_output=capture,
        )
    except FileNotFoundError as exc:
        raise LauncherError(f"Command was not found: {args[0]}") from exc


def print_process_output(process: subprocess.CompletedProcess[str]) -> None:
    if process.stdout:
        print(process.stdout, end="" if process.stdout.endswith("\n") else "\n", flush=True)
    if process.stderr:
        print(process.stderr, end="" if process.stderr.endswith("\n") else "\n", file=sys.stderr, flush=True)


def ensure_backend_layout() -> None:
    if not BACKEND_DIR.is_dir():
        raise LauncherError(f"Backend directory was not found: {BACKEND_DIR}")
    if not REQUIREMENTS_FILE.is_file():
        raise LauncherError(f"Requirements file was not found: {REQUIREMENTS_FILE}")
    if not COMPOSE_FILE.is_file():
        raise LauncherError(f"Docker Compose file was not found: {COMPOSE_FILE}")


def ensure_ai_layout() -> None:
    if not AI_REQUIREMENTS_FILE.is_file():
        raise LauncherError(f"AI requirements file was not found: {AI_REQUIREMENTS_FILE}")


def ensure_python_available() -> None:
    if not Path(sys.executable).exists():
        raise LauncherError("Python is not available.")
    ok(f"Python available: {sys.version.split()[0]}")


def find_compose_command() -> list[str]:
    docker = shutil.which("docker")
    if not docker:
        raise LauncherError("Docker is not available. Install Docker Desktop, then try again.")

    docker_version = run_process([docker, "--version"], capture=True)
    if docker_version.returncode != 0:
        print_process_output(docker_version)
        raise LauncherError("Docker is not available.")

    docker_info = run_process([docker, "info"], capture=True)
    if docker_info.returncode != 0:
        raise LauncherError("Docker is installed, but it is not running. Start Docker Desktop, then try again.")

    docker_compose = run_process([docker, "compose", "version"], capture=True)
    if docker_compose.returncode == 0:
        ok("Docker is running")
        return [docker, "compose"]

    legacy_compose = shutil.which("docker-compose")
    if legacy_compose:
        ok("Docker is running")
        return [legacy_compose]

    raise LauncherError("Docker Compose is not available. Install or update Docker Desktop, then try again.")


def find_compose_command_if_running() -> list[str] | None:
    try:
        return find_compose_command()
    except LauncherError as exc:
        info(str(exc))
        return None


def compose_args(compose_command: list[str], *args: str) -> list[str]:
    return [*compose_command, "-f", str(COMPOSE_FILE), *args]


def is_port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(1)
        return probe.connect_ex(("127.0.0.1", port)) == 0


def port_pids(port: int) -> list[str]:
    process = run_process(["netstat", "-ano"], capture=True)
    if process.returncode != 0:
        return []

    pids: set[str] = set()
    marker = f":{port}"
    for line in process.stdout.splitlines():
        parts = line.split()
        if len(parts) >= 5 and parts[0].upper() == "TCP" and marker in parts[1]:
            pids.add(parts[-1])
    return sorted(pids)


def local_mongodb_responds() -> bool:
    if not VENV_PYTHON.exists():
        return False

    ping_code = (
        "from pymongo import MongoClient\n"
        f"client = MongoClient({LOCAL_MONGODB_URI!r}, serverSelectionTimeoutMS=3000)\n"
        "client.admin.command('ping')\n"
        "client.close()\n"
    )
    process = run_process([str(VENV_PYTHON), "-c", ping_code], cwd=BACKEND_DIR, capture=True)
    return process.returncode == 0


def raise_non_mongodb_port_error() -> None:
    pids = port_pids(MONGODB_PORT)
    lines = [
        "Port 27017 is occupied, but the process is not responding as MongoDB.",
        "Diagnostic commands:",
        "netstat -ano | findstr :27017",
    ]
    if pids:
        for pid in pids:
            lines.append(f"Get-Process -Id {pid}")
    else:
        lines.append("Get-Process -Id <PID>")
    raise LauncherError("\n".join(lines))


def read_env_values() -> dict[str, str]:
    if not ENV_FILE.exists():
        return {}
    values: dict[str, str] = {}
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def append_missing_env_values(defaults: dict[str, str]) -> list[str]:
    existing = read_env_values()
    missing = [key for key in defaults if key not in existing]
    if not missing:
        return []

    with ENV_FILE.open("a", encoding="utf-8") as env_file:
        env_file.write("\n# Phase 2 AI foundation\n")
        for key in missing:
            env_file.write(f"{key}={env_value(defaults[key])}\n")
    return missing


def parse_bool(value: str | None, *, default: bool) -> bool:
    if value is None or not value.strip():
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def ai_enabled_from_env() -> bool:
    return parse_bool(read_env_values().get("AI_ENABLED"), default=True)


def qdrant_url_from_env() -> str:
    return read_env_values().get("QDRANT_URL") or DEFAULT_QDRANT_URL


def qdrant_responds(url: str) -> bool:
    health_url = f"{url.rstrip('/')}/healthz"
    try:
        with urllib.request.urlopen(health_url, timeout=3) as response:
            return 200 <= response.status < 300
    except (OSError, urllib.error.URLError, TimeoutError):
        return False


def raise_non_qdrant_port_error() -> None:
    pids = port_pids(QDRANT_REST_PORT)
    lines = [
        "Port 6333 is occupied, but the process is not responding as Qdrant.",
        "Diagnostic commands:",
        "netstat -ano | findstr :6333",
    ]
    if pids:
        for pid in pids:
            lines.append(f"Get-Process -Id {pid}")
    else:
        lines.append("Get-Process -Id <PID>")
    raise LauncherError("\n".join(lines))


def ensure_port_available(port: int, purpose: str) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        try:
            probe.bind(("0.0.0.0", port))
        except OSError as exc:
            raise LauncherError(f"Port {port} is already in use. Stop the existing {purpose}, then try again.") from exc


def get_container_id(compose_command: list[str], service: str) -> str:
    process = run_process(compose_args(compose_command, "ps", "-q", service), cwd=ROOT_DIR, capture=True)
    if process.returncode != 0:
        return ""
    return process.stdout.strip()


def is_compose_service_running(compose_command: list[str], service: str) -> bool:
    container_id = get_container_id(compose_command, service)
    if not container_id:
        return False
    process = run_process(
        ["docker", "inspect", "--format", "{{.State.Running}}", container_id],
        capture=True,
    )
    return process.returncode == 0 and process.stdout.strip().lower() == "true"


def is_compose_mongodb_running(compose_command: list[str]) -> bool:
    return is_compose_service_running(compose_command, MONGODB_SERVICE)


def is_compose_qdrant_running(compose_command: list[str]) -> bool:
    return is_compose_service_running(compose_command, QDRANT_SERVICE)


def wait_for_compose_health(
    compose_command: list[str],
    service: str,
    timeout_seconds: int = 120,
    *,
    ready_message: str,
) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_status = "unknown"

    while time.monotonic() < deadline:
        container_id = get_container_id(compose_command, service)
        if container_id:
            status = run_process(
                [
                    "docker",
                    "inspect",
                    "--format",
                    "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}",
                    container_id,
                ],
                capture=True,
            )
            if status.returncode == 0:
                last_status = status.stdout.strip()
                if last_status == "healthy":
                    ok(ready_message)
                    return
        time.sleep(2)

    raise LauncherError(f"{service} startup failure. Health status did not become healthy; last status: {last_status}.")


def start_docker_mongodb(compose_command: list[str]) -> None:
    info("Starting MongoDB with Docker Compose")
    process = run_process(compose_args(compose_command, "up", "-d", MONGODB_SERVICE), cwd=ROOT_DIR, capture=True)
    if process.returncode != 0:
        print_process_output(process)
        if "port is already allocated" in (process.stderr or "").lower():
            raise LauncherError("Port 27017 is already in use. Stop the existing MongoDB instance, then try again.")
        raise LauncherError("MongoDB startup failure. Docker Compose could not start the MongoDB service.")

    wait_for_compose_health(compose_command, MONGODB_SERVICE, ready_message="MongoDB started through Docker")


def wait_for_qdrant_ready(url: str, timeout_seconds: int = 120, *, ready_message: str = "Qdrant connected") -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if qdrant_responds(url):
            ok(ready_message)
            return
        time.sleep(2)
    raise LauncherError("Qdrant startup failure. The service did not respond to its health endpoint.")


def start_docker_qdrant(compose_command: list[str]) -> None:
    info("Starting Qdrant with Docker Compose")
    process = run_process(compose_args(compose_command, "up", "-d", QDRANT_SERVICE), cwd=ROOT_DIR, capture=True)
    if process.returncode != 0:
        print_process_output(process)
        if "port is already allocated" in (process.stderr or "").lower():
            raise LauncherError("Port 6333 is already in use. Stop the conflicting process, then try again.")
        raise LauncherError("Qdrant startup failure. Docker Compose could not start the Qdrant service.")

    wait_for_qdrant_ready(qdrant_url_from_env(), ready_message="Qdrant started through Docker")


def ensure_mongodb_ready() -> str:
    if is_port_in_use(MONGODB_PORT):
        compose_command = find_compose_command_if_running()
        if compose_command is not None and is_compose_mongodb_running(compose_command):
            print("MongoDB mode: Docker Compose", flush=True)
            wait_for_compose_health(
                compose_command,
                MONGODB_SERVICE,
                ready_message="MongoDB is running through Docker",
            )
            return "docker"

        if local_mongodb_responds():
            ok("Existing local MongoDB detected")
            info("Docker MongoDB will not be started")
            print("MongoDB mode: Local Windows service", flush=True)
            return "local"
        raise_non_mongodb_port_error()

    compose_command = find_compose_command()
    print("MongoDB mode: Docker Compose", flush=True)
    start_docker_mongodb(compose_command)
    return "docker"


def ensure_qdrant_ready() -> str:
    url = qdrant_url_from_env()
    if is_port_in_use(QDRANT_REST_PORT):
        compose_command = find_compose_command_if_running()
        if compose_command is not None and is_compose_qdrant_running(compose_command):
            print("Qdrant mode: Docker Compose", flush=True)
            wait_for_qdrant_ready(url, ready_message="Qdrant connected")
            return "docker"
        if qdrant_responds(url):
            ok("Existing Qdrant service detected")
            print("Qdrant mode: Existing service", flush=True)
            ok("Qdrant connected")
            return "existing"
        raise_non_qdrant_port_error()

    compose_command = find_compose_command()
    print("Qdrant mode: Docker Compose", flush=True)
    start_docker_qdrant(compose_command)
    ok("Qdrant connected")
    return "docker"


def stop_mongodb() -> int:
    if is_port_in_use(MONGODB_PORT) and local_mongodb_responds():
        info("Local MongoDB was not stopped")

    compose_command = find_compose_command_if_running()
    if compose_command is None:
        return 0

    if is_port_in_use(QDRANT_REST_PORT) and qdrant_responds(qdrant_url_from_env()) and not is_compose_qdrant_running(compose_command):
        info("Existing Qdrant service was not stopped")

    process = run_process(compose_args(compose_command, "down"), cwd=ROOT_DIR, capture=True)
    print_process_output(process)
    if process.returncode != 0:
        error("Docker Compose down failed.")
        return process.returncode
    ok("Docker Compose services stopped. Named MongoDB and Qdrant volumes were preserved.")
    return 0


def create_virtual_environment() -> None:
    created = False
    if not VENV_PYTHON.exists():
        info(f"Creating virtual environment: {VENV_DIR}")
        process = run_process([sys.executable, "-m", "venv", str(VENV_DIR)], cwd=ROOT_DIR, capture=True)
        if process.returncode != 0:
            print_process_output(process)
            raise LauncherError("Virtual environment creation failed.")
        created = True

    ok("Virtual environment ready")

    if created:
        info("Installing backend dependencies")
        process = run_process(
            [str(VENV_PYTHON), "-m", "pip", "install", "-r", str(REQUIREMENTS_FILE)],
            cwd=BACKEND_DIR,
            capture=False,
        )
        if process.returncode != 0:
            raise LauncherError("Dependency installation failure. pip install did not complete successfully.")
        ok("Dependencies installed")


def env_value(value: str) -> str:
    safe_characters = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-/:@")
    if value and all(character in safe_characters for character in value):
        return value
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def update_env_file(values: dict[str, str]) -> None:
    lines = ENV_FILE.read_text(encoding="utf-8").splitlines()
    updated_lines: list[str] = []
    seen: set[str] = set()

    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            updated_lines.append(line)
            continue

        key = line.split("=", 1)[0].strip()
        if key in values:
            updated_lines.append(f"{key}={env_value(values[key])}")
            seen.add(key)
        else:
            updated_lines.append(line)

    for key, value in values.items():
        if key not in seen:
            updated_lines.append(f"{key}={env_value(value)}")

    ENV_FILE.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")


def prompt_admin_email() -> str:
    while True:
        email = input("Admin email: ").strip()
        if "@" in email and "." in email.rsplit("@", 1)[-1]:
            return email
        error("Please enter a valid admin email address.")


def prompt_admin_password() -> str:
    while True:
        password = getpass.getpass("Admin password (at least 12 characters): ")
        if len(password) < 12:
            error("Admin password must be at least 12 characters.")
            continue
        confirmation = getpass.getpass("Confirm admin password: ")
        if password != confirmation:
            error("Passwords did not match.")
            continue
        return password


def ensure_environment_configuration() -> None:
    if ENV_FILE.exists():
        ok("Environment configuration ready")
        return

    if not ENV_EXAMPLE_FILE.is_file():
        raise LauncherError(f"Environment example file was not found: {ENV_EXAMPLE_FILE}")

    info("Creating backend/.env from backend/.env.example")
    shutil.copyfile(ENV_EXAMPLE_FILE, ENV_FILE)

    admin_email = prompt_admin_email()
    admin_password = prompt_admin_password()
    update_env_file(
        {
            "MONGODB_URL": "mongodb://localhost:27017",
            "MONGODB_DATABASE": "museum_guide",
            "JWT_SECRET_KEY": secrets.token_urlsafe(48),
            "UPLOAD_DIRECTORY": "uploads/images",
            "ADMIN_EMAIL": admin_email,
            "ADMIN_PASSWORD": admin_password,
            "ADMIN_FULL_NAME": "Museum Administrator",
        }
    )
    ok("Environment configuration ready")


def run_setup_check() -> tuple[int, list[str]]:
    process = run_process([str(VENV_PYTHON), "-m", "scripts.check_setup"], cwd=BACKEND_DIR, capture=True)
    print_process_output(process)
    output = f"{process.stdout or ''}\n{process.stderr or ''}"
    failures = [line.strip() for line in output.splitlines() if line.strip().startswith("[FAIL]")]
    return process.returncode, failures


def setup_failure_message(failures: list[str]) -> str:
    joined = " ".join(failures)
    if "Environment configuration" in joined or "Environment file" in joined:
        return "Invalid .env. Fix backend/.env, then run the launcher again."
    if "MongoDB" in joined:
        return "MongoDB startup failure. The backend could not connect to MongoDB."
    return "Backend setup check failed. Fix the reported issue, then run the launcher again."


def ensure_admin_account() -> None:
    check_code, failures = run_setup_check()
    missing_admin_only = (
        check_code != 0
        and len(failures) == 1
        and "Admin account was not found" in failures[0]
    )

    if check_code != 0 and not missing_admin_only:
        raise LauncherError(setup_failure_message(failures))

    if missing_admin_only:
        info("Admin account is missing; creating it now")
        process = run_process([str(VENV_PYTHON), "-m", "scripts.create_admin"], cwd=BACKEND_DIR, capture=False)
        if process.returncode != 0:
            raise LauncherError("Admin creation failure. Check ADMIN_EMAIL and ADMIN_PASSWORD in backend/.env.")

        check_code, failures = run_setup_check()
        if check_code != 0:
            raise LauncherError(setup_failure_message(failures))

    ok("Admin account ready")


def print_server_urls() -> None:
    print("Swagger UI: http://localhost:8000/docs", flush=True)
    print("Health check: http://localhost:8000/api/v1/health", flush=True)
    print("Android emulator URL: http://10.0.2.2:8000/", flush=True)


def start_fastapi() -> int:
    ensure_port_available(FASTAPI_PORT, "FastAPI server")
    ok("Starting FastAPI")
    print_server_urls()
    try:
        process = run_process(
            [
                str(VENV_PYTHON),
                "-m",
                "uvicorn",
                "main:app",
                "--reload",
                "--host",
                "0.0.0.0",
                "--port",
                str(FASTAPI_PORT),
            ],
            cwd=BACKEND_DIR,
            capture=False,
        )
        if process.returncode != 0:
            error("FastAPI stopped with an error.")
        return process.returncode
    except KeyboardInterrupt:
        print()
        ok("FastAPI stopped. MongoDB data was preserved.")
        return 0


def run_tests() -> int:
    ensure_mongodb_ready()
    process = run_process([str(VENV_PYTHON), "-m", "pytest", "-q"], cwd=BACKEND_DIR, capture=False)
    if process.returncode == 0:
        ok("Backend tests passed")
    else:
        error("Backend tests failed.")
    return process.returncode


def run_check() -> int:
    check_code, _ = run_setup_check()
    return check_code


def backend_python_version() -> str:
    process = run_process([str(VENV_PYTHON), "--version"], capture=True)
    if process.returncode != 0:
        return "unknown"
    return (process.stdout or process.stderr).strip().replace("Python ", "")


def verify_ai_python_compatibility() -> None:
    ensure_ai_layout()
    version = backend_python_version()
    info(f"Backend Python version: {version}")
    process = run_process([str(VENV_PYTHON), "-m", "pip", "install", "--dry-run", "-r", str(AI_REQUIREMENTS_FILE)], cwd=ROOT_DIR, capture=True)
    if process.returncode != 0:
        print_process_output(process)
        raise LauncherError(
            "The selected AI dependencies are not compatible with this Python environment. "
            "Install Python 3.12 or 3.13, create a new backend virtual environment with that Python, "
            "and keep the existing project files and data in place."
        )
    ok("AI dependency versions are compatible with this Python environment")


def install_ai_dependencies() -> None:
    ensure_ai_layout()
    info("Installing AI dependencies")
    process = run_process([str(VENV_PYTHON), "-m", "pip", "install", "-r", str(AI_REQUIREMENTS_FILE)], cwd=ROOT_DIR, capture=False)
    if process.returncode != 0:
        raise LauncherError("AI dependency installation failed.")
    ok("AI dependencies installed")


def ensure_ai_environment_configuration() -> None:
    ensure_environment_configuration()
    missing = append_missing_env_values(AI_ENV_DEFAULTS)
    if missing:
        ok(f"AI environment keys added: {', '.join(missing)}")
    else:
        ok("AI configuration available")


def run_ai_setup() -> int:
    ensure_ai_environment_configuration()
    verify_ai_python_compatibility()
    install_ai_dependencies()
    ensure_qdrant_ready()
    return run_backend_module("scripts.check_ai_setup")


def run_backend_module(module: str) -> int:
    process = run_process([str(VENV_PYTHON), "-m", module], cwd=BACKEND_DIR, capture=False)
    return process.returncode


def run_ai_check() -> int:
    ensure_environment_configuration()
    if ai_enabled_from_env():
        ensure_qdrant_ready()
    else:
        info("AI is disabled; Qdrant will not be started")
    return run_backend_module("scripts.check_ai_setup")


def run_ai_tests() -> int:
    ensure_environment_configuration()
    if ai_enabled_from_env():
        ensure_qdrant_ready()
    test_embedding = run_backend_module("scripts.test_embedding")
    if test_embedding != 0:
        return test_embedding
    process = run_process(
        [
            str(VENV_PYTHON),
            "-m",
            "pytest",
            "-q",
            "tests/test_ai_config.py",
            "tests/test_embedding_service.py",
            "tests/test_qdrant_manager.py",
            "tests/test_ai_health.py",
        ],
        cwd=BACKEND_DIR,
        capture=False,
    )
    if process.returncode == 0:
        ok("AI tests passed")
    else:
        error("AI tests failed.")
    return process.returncode


def dependency_status(package: str, module_name: str | None = None) -> str:
    module = module_name or package
    code = f"import importlib.util; raise SystemExit(0 if importlib.util.find_spec({module!r}) else 1)"
    process = run_process([str(VENV_PYTHON), "-c", code], cwd=BACKEND_DIR, capture=True)
    if process.returncode != 0:
        return "not installed"
    version_code = (
        "import importlib.metadata as m\n"
        f"print(m.version({package!r}))\n"
    )
    version = run_process([str(VENV_PYTHON), "-c", version_code], cwd=BACKEND_DIR, capture=True)
    if version.returncode == 0:
        return version.stdout.strip()
    return "installed"


def qdrant_collection_status() -> str:
    code = (
        "from app.config import get_settings\n"
        "from app.vector.qdrant_manager import QdrantSetupError, get_qdrant_manager\n"
        "settings = get_settings()\n"
        "try:\n"
        "    status = get_qdrant_manager(settings).get_collection_status()\n"
        "    print(status.status)\n"
        "except QdrantSetupError as exc:\n"
        "    print(str(exc))\n"
        "    raise SystemExit(1)\n"
    )
    process = run_process([str(VENV_PYTHON), "-c", code], cwd=BACKEND_DIR, capture=True)
    if process.returncode == 0:
        return process.stdout.strip() or "unknown"
    return "unavailable"


def report_status() -> int:
    ensure_environment_configuration()
    env_values = read_env_values()
    print(f"Python version: {backend_python_version()}")
    print(f"Backend virtual environment: {'ready' if VENV_PYTHON.exists() else 'missing'}")
    print(f"FastAPI port {FASTAPI_PORT}: {'in use' if is_port_in_use(FASTAPI_PORT) else 'available'}")

    if is_port_in_use(MONGODB_PORT):
        if local_mongodb_responds():
            print("MongoDB mode and status: Local Windows service, connected")
        else:
            print("MongoDB mode and status: Port occupied, not responding as MongoDB")
    else:
        print("MongoDB mode and status: Docker Compose available when started, port free")

    qdrant_url = env_values.get("QDRANT_URL") or DEFAULT_QDRANT_URL
    if is_port_in_use(QDRANT_REST_PORT):
        if qdrant_responds(qdrant_url):
            print("Qdrant mode and status: service detected, connected")
        else:
            print("Qdrant mode and status: port occupied, not responding as Qdrant")
    else:
        print("Qdrant mode and status: Docker Compose available when started, port free")

    ai_enabled = ai_enabled_from_env()
    print(f"AI enabled: {str(ai_enabled).lower()}")
    print(f"torch: {dependency_status('torch')}")
    print(f"torchvision: {dependency_status('torchvision')}")
    print(f"open_clip_torch: {dependency_status('open_clip_torch', 'open_clip')}")
    print(f"qdrant-client: {dependency_status('qdrant-client', 'qdrant_client')}")
    print(f"OpenCLIP model: {env_values.get('OPENCLIP_MODEL_NAME') or AI_ENV_DEFAULTS['OPENCLIP_MODEL_NAME']}")
    print(f"OpenCLIP pretrained: {env_values.get('OPENCLIP_PRETRAINED') or AI_ENV_DEFAULTS['OPENCLIP_PRETRAINED']}")
    print(f"OpenCLIP device: {env_values.get('OPENCLIP_DEVICE') or AI_ENV_DEFAULTS['OPENCLIP_DEVICE']}")
    print(f"Qdrant collection: {env_values.get('QDRANT_COLLECTION') or AI_ENV_DEFAULTS['QDRANT_COLLECTION']}")
    print(f"Qdrant collection status: {qdrant_collection_status() if ai_enabled else 'disabled'}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Start the Museum Guide backend.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--test", action="store_true", help="Start MongoDB if needed and run backend tests.")
    mode.add_argument("--check", action="store_true", help="Run the existing backend setup check.")
    mode.add_argument("--stop", action="store_true", help="Stop Docker Compose without deleting MongoDB data.")
    mode.add_argument("--setup-ai", action="store_true", help="Install AI dependencies, start Qdrant, and check AI setup.")
    mode.add_argument("--check-ai", action="store_true", help="Start or detect Qdrant and run the AI setup check.")
    mode.add_argument("--test-ai", action="store_true", help="Run the embedding script and AI-focused tests.")
    mode.add_argument("--status", action="store_true", help="Report backend, MongoDB, Qdrant, and AI status.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        ensure_backend_layout()
        ensure_python_available()

        create_virtual_environment()

        if args.stop:
            return stop_mongodb()

        if args.status:
            return report_status()

        if args.check:
            ensure_environment_configuration()
            return run_check()

        if args.setup_ai:
            return run_ai_setup()

        if args.check_ai:
            return run_ai_check()

        if args.test_ai:
            return run_ai_tests()

        if args.test:
            return run_tests()

        ensure_environment_configuration()
        ensure_mongodb_ready()
        if ai_enabled_from_env():
            ensure_qdrant_ready()
            ok("AI configuration available")
        else:
            info("AI is disabled; Qdrant will not be started")
        ensure_admin_account()
        return start_fastapi()
    except LauncherError as exc:
        error(str(exc))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
