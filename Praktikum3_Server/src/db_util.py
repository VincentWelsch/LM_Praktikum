# #!/usr/bin/python3
# import psycopg2
# import os

# def connect_db():
#     try:
#         conn = psycopg2.connect(
#             dbname = os.getenv("DB_NAME"),
#             user = os.getenv("DB_USER"),
#             password = os.getenv("DB_PASSWORD"),
#             host = os.getenv("DB_HOST"),
#             port = os.getenv("DB_PORT"))
#         print("Connected to db successfully")
#         return conn
#     except Exception as e:
#         print("Connection to db failed:", e)
#         return None


# Test mit sqlite

#!/usr/bin/python3
import sqlite3
import logging

logger = logging.getLogger(__name__)

def connect_db():
    """
    Öffnet (oder erstellt) die SQLite‑Datenbank test.db.
    Gibt ein sqlite3.Connection‑Objekt zurück oder None bei Fehler.
    """
    try:
        conn = sqlite3.connect("test.db")
        conn.row_factory = sqlite3.Row   # ermöglicht dict‑ähnlichen Zugriff
        return conn
    except sqlite3.Error as e:
        logger.error(f"DB connection error: {e}")
        return None
