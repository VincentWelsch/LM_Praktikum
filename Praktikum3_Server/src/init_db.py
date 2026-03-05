# init_db.py
import sqlite3
import pathlib


# Erzeugt eine SQLite-Datenbank zum testen (Datei test.db). Wenn diese Datei bereits vorliegt, muss das Skript hier nicht mehr ausgeführt werden.

# Pfad zur Datenbankdatei (relativ zum Skript)
DB_PATH = pathlib.Path(__file__).with_name("test.db")

def create_tables():
    # Verbindung öffnen (erstellt die Datei, falls sie noch nicht existiert)
    conn = sqlite3.connect(DB_PATH)
    try:
        cur = conn.cursor()

        # Tabelle für Runs
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS run (
                id   INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            );
            """
        )

        # Tabelle für Fix‑Punkte
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS fix (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                runId     INTEGER NOT NULL,
                longitude REAL NOT NULL,
                latitude  REAL NOT NULL,
                altitude  REAL NOT NULL,
                timestamp INTEGER,
                FOREIGN KEY (runId) REFERENCES run(id) ON DELETE CASCADE
            );
            """
        )

        conn.commit()
        print(f"✅ Datenbank erstellt/aktualisiert: {DB_PATH}")
    finally:
        conn.close()


if __name__ == "__main__":
    create_tables()
