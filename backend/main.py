from __future__ import annotations

import sys
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app.config import Settings, get_settings
from app.database.mongodb import MongoConnectionError, ensure_indexes, mongo_manager
from app.routes import artifacts, auth
from app.services.image_storage import ensure_upload_directory


API_PREFIX = "/api/v1"


def create_app(settings: Settings | None = None, database=None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(
        title=settings.app_name,
        description="Admin artifact management API for the Museum Guide System.",
        version="1.0.0",
        docs_url="/docs",
        redoc_url="/redoc",
    )
    app.state.settings = settings
    app.state.database = database
    app.state.external_database = database is not None

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.parsed_cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
    )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(_: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={"detail": "Invalid request data.", "errors": exc.errors()},
        )

    @app.exception_handler(MongoConnectionError)
    async def mongo_exception_handler(_: Request, exc: MongoConnectionError):
        return JSONResponse(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, content={"detail": str(exc)})

    @app.on_event("startup")
    def startup() -> None:
        ensure_upload_directory(settings)
        if app.state.database is None:
            app.state.database = mongo_manager.connect(settings)
        else:
            ensure_indexes(app.state.database)

    @app.on_event("shutdown")
    def shutdown() -> None:
        if not app.state.external_database:
            mongo_manager.close()

    @app.get(f"{API_PREFIX}/health", tags=["System"])
    def health() -> dict:
        database_status = "connected" if app.state.database is not None else "unavailable"
        try:
            if app.state.database is not None:
                app.state.database.command("ping")
                database_status = "connected"
        except Exception:
            database_status = "connected" if app.state.external_database else "unavailable"
        uploads_status = "available" if settings.upload_path.exists() and settings.upload_path.is_dir() else "unavailable"
        return {
            "status": "healthy" if database_status == "connected" and uploads_status == "available" else "degraded",
            "database": database_status,
            "uploads_directory": uploads_status,
        }

    app.include_router(auth.router, prefix=API_PREFIX)
    app.include_router(artifacts.router, prefix=API_PREFIX)

    ensure_upload_directory(settings)
    app.mount("/uploads", StaticFiles(directory=str(settings.upload_root_path), check_dir=False), name="uploads")
    return app


app = create_app()
