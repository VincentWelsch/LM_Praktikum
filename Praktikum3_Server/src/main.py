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
@app.get("/run/get") # return all run names with IDs
async def get_run_ids():
    return None # TODO

@app.post("/run/create")
async def create_run(runName: str):
    return None # TODO

@app.post("/run/report")
async def create_run(fixReport: FixReport):
    return None # TODO

# --- intention ---
# 1. POST to /run/create to create a run by name
# 2. GET to /run/get to get all run names and IDs
# 3. POST to /run/report with runId to report fix

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

@app.get("/run/remove/{runId}") # remove a run and all its fixes
async def remove_run(runId: str):
    return None # TODO


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
