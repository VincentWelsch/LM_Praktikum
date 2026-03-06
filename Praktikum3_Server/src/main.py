from fastapi import FastAPI
from typing import Optional
from pydantic import BaseModel
from db_util import connect_db
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class FixReport(BaseModel):
    runId: str
    longitude: float
    latitude: float
    altitude: float
    timestamp: Optional[int] = None


app = FastAPI(title="LM P3 Server")


# endpoints below

@app.get("/run/get") # return all runs with ID and name
async def get_runs():
    conn = connect_db()
    if not conn:
        return {"success": 0, "message": "DB connection failed", "runs": []}
    try:
        cur = conn.cursor()
        cur.execute("SELECT id, name FROM run")
        rows = cur.fetchall()
        runs = [{"runId": r["id"], "runName": r["name"]} for r in rows]
        return {"success": 1, "runs": runs}
    finally:
        conn.close()

@app.post("/run/create") # create a run by name
async def create_run(runName: str):
    conn = connect_db()
    if not conn:
        return {"success": 0, "message": "DB connection failed"}
    try:
        cur = conn.cursor()

        # If run already exists, send an error message
        cur.execute("SELECT id FROM run WHERE name = ?", (runName,))
        if cur.fetchone():
            return {"success": 0, "message": "runName already exists"}

        cur.execute("INSERT INTO run (name) VALUES (?)", (runName,))
        conn.commit()
        new_id = cur.lastrowid
        return {"success": 1, "runId": new_id}
    finally:
        conn.close()

@app.post("/run/report") # report a fix with runId
async def report_fix(fixReport: FixReport):
    return None # TODO

# endpoints further below are not really required by the tasks for Praktikum 3
@app.get("/run/get/{runId}") # return all fixes of a run
async def get_fixes(runId: str):
    # get db connection
    connection = connect_db()
    if connection == None:
        return {"message": "Connection failed", "success": 0, "result": {}}
    
    answer = {"message": "Error", "success": 0, "result": {}}
    with connection.cursor() as cursor: # open cursor
        try:
            # get fixes
            cursor.execute("""SELECT longitude, latitude, altitude
                           FROM fix
                           WHERE \"runId\" = %s""", (runId,))
            if not cursor.rowcount:
                answer["message"] = "No run found"
            else:
                fixes = list()
                rows = cursor.fetchall() # get rows with matching runId
                for row in rows:
                    # add fix with named fields using dict
                    fix = {"longitude": row[0], "latitude": row[1], "altitude": row[2]}
                    fixes.append(fix)
                answer["result"] = fixes
                answer["message"] = "Ok"
        except psycopg2.Error as e:
            print("SQL error:", e)
            answer["message"] = "SQL error"
        except Exception as e:
            print("Error:", e)
    connection.close()
    return answer

@app.delete("/run/remove/{runId}") # remove a run and all its fixes by its runID
async def remove_run(runId: str):
    conn = connect_db()
    if not conn:
        return {"success": 0, "message": "DB connection failed"}
    try:
        cur = conn.cursor()
        
        # If run doesnt exist, send an error message
        cur.execute("SELECT 1 FROM run WHERE id = ?", (runId,))
        if cur.fetchone() is None:
            return {
                "success": 0,
                "message": f"No run with ID {runId} found – nothing was deleted",
            }

        cur.execute("DELETE FROM fix WHERE runId = ?", (runId,))
        cur.execute("DELETE FROM run WHERE id = ?", (runId,))
        conn.commit()
        return {"success": 1, "message": f"Run {runId} removed"}
    finally:
        conn.close()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
