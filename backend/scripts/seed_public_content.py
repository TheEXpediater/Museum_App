from __future__ import annotations

from pymongo.errors import PyMongoError

from app.config import get_settings
from app.database.mongodb import mongo_manager
from app.utils import utc_now


DEMO_NOTE = "Demonstration content only. Replace with official PSAU Museum content before production use."


def seed_if_empty(database) -> dict[str, int]:
    now = utc_now()
    inserted = {"news": 0, "announcements": 0, "museum_articles": 0, "museum_information": 0, "programs": 0}

    if database.news.count_documents({}) == 0:
        database.news.insert_one(
            {
                "title": "Demo Museum News",
                "summary": DEMO_NOTE,
                "body": DEMO_NOTE,
                "cover_image_url": None,
                "published_at": now,
                "is_published": True,
                "created_at": now,
                "updated_at": now,
            }
        )
        inserted["news"] = 1

    if database.announcements.count_documents({}) == 0:
        database.announcements.insert_one(
            {
                "title": "Demo Visitor Announcement",
                "message": DEMO_NOTE,
                "priority": "normal",
                "starts_at": now,
                "expires_at": None,
                "is_active": True,
                "created_at": now,
                "updated_at": now,
            }
        )
        inserted["announcements"] = 1

    if database.museum_articles.count_documents({}) == 0:
        database.museum_articles.insert_one(
            {
                "title": "Demo Museum Article",
                "summary": DEMO_NOTE,
                "body": DEMO_NOTE,
                "cover_image_url": None,
                "category": "Demo",
                "published_at": now,
                "is_published": True,
                "created_at": now,
                "updated_at": now,
            }
        )
        inserted["museum_articles"] = 1

    if database.museum_information.count_documents({}) == 0:
        database.museum_information.insert_one(
            {
                "museum_name": "To be configured.",
                "description": "To be configured.",
                "campus_location": "To be configured.",
                "opening_hours": "To be configured.",
                "contact_email": "To be configured.",
                "contact_phone": "To be configured.",
                "visitor_guidelines": "To be configured.",
                "accessibility_information": "To be configured.",
                "latitude": None,
                "longitude": None,
                "updated_at": now,
            }
        )
        inserted["museum_information"] = 1

    if database.programs.count_documents({}) == 0:
        database.programs.insert_one(
            {
                "name": "Demo Program",
                "name_normalized": "demo program",
                "active": True,
                "created_at": now,
                "updated_at": now,
                "note": DEMO_NOTE,
            }
        )
        inserted["programs"] = 1

    return inserted


def main() -> int:
    settings = get_settings()
    database = mongo_manager.connect(settings)
    try:
        inserted = seed_if_empty(database)
        print("Public content seed complete.")
        for collection, count in inserted.items():
            print(f"{collection}: inserted {count}")
        return 0
    except PyMongoError as exc:
        print(f"Public content seed failed: {exc}")
        return 1
    finally:
        mongo_manager.close()


if __name__ == "__main__":
    raise SystemExit(main())
