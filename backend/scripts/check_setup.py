from __future__ import annotations

import ipaddress
import socket

from pydantic import ValidationError
from pymongo.errors import PyMongoError

from app.config import ENV_FILE, Settings, get_settings
from app.database.mongodb import MongoConnectionError, mongo_manager
from app.services.image_storage import ensure_upload_directory


PORT = 8000


class Reporter:
    def __init__(self) -> None:
        self.failures = 0

    def ok(self, message: str) -> None:
        print(f"[OK] {message}")

    def info(self, message: str) -> None:
        print(f"[INFO] {message}")

    def warn(self, message: str) -> None:
        print(f"[WARN] {message}")

    def fail(self, message: str) -> None:
        self.failures += 1
        print(f"[FAIL] {message}")


def load_settings(reporter: Reporter) -> Settings | None:
    if ENV_FILE.exists():
        reporter.ok(f"Environment file found: {ENV_FILE}")
    else:
        reporter.fail(f"Environment file is missing: {ENV_FILE}")

    try:
        get_settings.cache_clear()
        settings = get_settings()
    except ValidationError as exc:
        reporter.fail(f"Environment configuration is invalid: {exc}")
        return None
    except Exception as exc:
        reporter.fail(f"Environment configuration could not be loaded: {exc}")
        return None

    reporter.ok("Environment configuration loaded")
    reporter.ok("JWT secret is configured")
    return settings


def check_mongodb(settings: Settings, reporter: Reporter):
    try:
        database = mongo_manager.connect(settings)
        database.command("ping")
    except MongoConnectionError as exc:
        reporter.fail(str(exc))
        return None
    except PyMongoError:
        reporter.fail("MongoDB command failed.")
        return None

    reporter.ok("MongoDB connected")
    reporter.ok(f"Database available: {settings.mongodb_database}")
    return database


def check_uploads(settings: Settings, reporter: Reporter) -> None:
    try:
        ensure_upload_directory(settings)
        probe = settings.upload_path / ".setup-check.tmp"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
    except OSError as exc:
        reporter.fail(f"Upload directory is not writable: {exc}")
        return

    reporter.ok(f"Upload directory writable: {settings.upload_path}")


def check_admin(database, settings: Settings, reporter: Reporter) -> None:
    query = {"role": "admin", "is_active": True}
    if settings.admin_email:
        query["email"] = settings.admin_email.lower().strip()

    admin = database.users.find_one(query)
    if admin is None:
        target = settings.admin_email or "an active admin user"
        reporter.fail(f"Admin account was not found: {target}")
        return

    reporter.ok(f"Admin account exists: {admin.get('email')}")


def check_port(reporter: Reporter) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        try:
            probe.bind(("0.0.0.0", PORT))
        except OSError:
            reporter.warn(f"Port {PORT} is already in use. Stop the existing process before starting Uvicorn.")
            return

    reporter.ok(f"Port {PORT} is available")


def local_ipv4_addresses() -> list[str]:
    addresses: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            addresses.add(info[4][0])
    except OSError:
        pass

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect(("8.8.8.8", 80))
            addresses.add(probe.getsockname()[0])
    except OSError:
        pass

    private_addresses = []
    for address in addresses:
        parsed = ipaddress.ip_address(address)
        if parsed.version == 4 and parsed.is_private and not parsed.is_loopback:
            private_addresses.append(address)
    return sorted(private_addresses)


def print_access_urls(reporter: Reporter) -> None:
    reporter.info("Emulator URL: http://10.0.2.2:8000/")
    addresses = local_ipv4_addresses()
    for address in addresses:
        reporter.info(f"Physical-device candidate: http://{address}:8000/")
    if not addresses:
        reporter.warn("No private IPv4 address was detected for physical-device testing.")


def main() -> int:
    reporter = Reporter()
    settings = load_settings(reporter)
    database = None

    if settings is not None:
        database = check_mongodb(settings, reporter)
        check_uploads(settings, reporter)
        check_port(reporter)
        if database is not None:
            check_admin(database, settings, reporter)

    print_access_urls(reporter)
    mongo_manager.close()
    return 1 if reporter.failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
