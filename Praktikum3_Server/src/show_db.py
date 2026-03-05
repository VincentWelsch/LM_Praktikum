import sqlite3, pathlib, json


# Ein Python-Skript, das bei Ausführung die SQLite-Datenbank in der Datei "test.db" anzeigt/ausgibt.

db_path = pathlib.Path("test.db")
with sqlite3.connect(db_path) as con:
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    cur.execute("SELECT * FROM run")
    print("Runs:", json.dumps([dict(row) for row in cur.fetchall()], indent=2))

    cur.execute("SELECT * FROM fix")
    print("Fixes:", json.dumps([dict(row) for row in cur.fetchall()], indent=2))
