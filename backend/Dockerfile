FROM python:3.11-slim

# HF Spaces runs as a non-root user; 1000:1000 is the standard UID/GID.
RUN useradd -m -u 1000 appuser

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

# HF Spaces Docker spaces must expose port 7860.
EXPOSE 7860

USER appuser

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "7860"]
