#!/usr/bin/python3
import psycopg2
import os

def connect_db():
    try:
        conn = psycopg2.connect(
            dbname = os.getenv("DB_NAME"),
            user = os.getenv("DB_USER"),
            password = os.getenv("DB_PASSWORD"),
            host = os.getenv("DB_HOST"),
            port = os.getenv("DB_PORT"))
        print("Connected to db successfully")
        return conn
    except Exception as e:
        print("Connection to db failed:", e)
        return None
