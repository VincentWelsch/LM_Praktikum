FROM python:3.11-slim

WORKDIR /src

COPY ./src /src
RUN pip3 install --no-cache-dir -r /src/requirements.txt

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
