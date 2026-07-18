from __future__ import annotations

import argparse
import getpass
import secrets
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = ROOT_DIR / "backend"
VENV_DIR = BACKEND_DIR / ".venv"
VENV_PYTHON = VENV_DIR / "Scripts" / "python.exe"
REQUIREMENTS_FILE = BACKEND_DIR / "requirements.txt"
ENV_EXAMPLE_FILE = BACKEND_DIR / ".env.example"
ENV_FILE = BACKEND_DIR / ".env"
COMPOSE_FILE = ROOT_DIR / "compose.yaml"

MONGODB_PORT = 27017
FASTAPI_PORT = 8000
MONGODB_SERVICE = "mongodb"
LOCAL_MONGODB_URI = "mongodb://localhost:27017"


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


def ensure_port_available(port: int, purpose: str) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        try:
            probe.bind(("0.0.0.0", port))
        except OSError as exc:
            raise LauncherError(f"Port {port} is already in use. Stop the existing {purpose}, then try again.") from exc


def get_container_id(compose_command: list[str]) -> str:
    process = run_process(compose_args(compose_command, "ps", "-q", MONGODB_SERVICE), cwd=ROOT_DIR, capture=True)
    if process.returncode != 0:
        return ""
    return process.stdout.strip()


def is_compose_mongodb_running(compose_command: list[str]) -> bool:
    container_id = get_container_id(compose_command)
    if not container_id:
        return False
    process = run_process(
        ["docker", "inspect", "--format", "{{.State.Running}}", container_id],
        capture=True,
    )
    return process.returncode == 0 and process.stdout.strip().lower() == "true"


def wait_for_mongodb_health(
    compose_command: list[str],
    timeout_seconds: int = 120,
    *,
    ready_message: str = "MongoDB started through Docker",
) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_status = "unknown"

    while time.monotonic() < deadline:
        container_id = get_container_id(compose_command)
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

    raise LauncherError(f"MongoDB startup failure. Health status did not become healthy; last status: {last_status}.")


def start_docker_mongodb(compose_command: list[str]) -> None:
    info("Starting MongoDB with Docker Compose")
    process = run_process(compose_args(compose_command, "up", "-d", MONGODB_SERVICE), cwd=ROOT_DIR, capture=True)
    if process.returncode != 0:
        print_process_output(process)
        if "port is already allocated" in (process.stderr or "").lower():
            raise LauncherError("Port 27017 is already in use. Stop the existing MongoDB instance, then try again.")
        raise LauncherError("MongoDB startup failure. Docker Compose could not start the MongoDB service.")

    wait_for_mongodb_health(compose_command)


def ensure_mongodb_ready() -> str:
    if is_port_in_use(MONGODB_PORT):
        compose_command = find_compose_command_if_running()
        if compose_command is not None and is_compose_mongodb_running(compose_command):
            print("MongoDB mode: Docker Compose", flush=True)
            wait_for_mongodb_health(compose_command, ready_message="MongoDB is running through Docker")
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


def stop_mongodb() -> int:
    if is_port_in_use(MONGODB_PORT) and local_mongodb_responds():
        info("Local MongoDB was not stopped")

    compose_command = find_compose_command_if_running()
    if compose_command is None:
        return 0

    process = run_process(compose_args(compose_command, "down"), cwd=ROOT_DIR, capture=True)
    print_process_output(process)
    if process.returncode != 0:
        error("Docker Compose down failed.")
        return process.returncode
    ok("Docker Compose services stopped. Named MongoDB volume data was preserved.")
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Start the Museum Guide Phase 1 backend.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--test", action="store_true", help="Start MongoDB if needed and run backend tests.")
    mode.add_argument("--check", action="store_true", help="Run the existing backend setup check.")
    mode.add_argument("--stop", action="store_true", help="Stop Docker Compose without deleting MongoDB data.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        ensure_backend_layout()
        ensure_python_available()

        create_virtual_environment()

        if args.stop:
            return stop_mongodb()

        if args.check:
            ensure_environment_configuration()
            return run_check()

        if args.test:
            return run_tests()

        ensure_environment_configuration()
        ensure_mongodb_ready()
        ensure_admin_account()
        return start_fastapi()
    except LauncherError as exc:
        error(str(exc))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
