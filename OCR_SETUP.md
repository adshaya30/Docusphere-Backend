# OCR & Summarization Feature - Setup Guide

This guide explains how to set up the Python OCR and Summarization service required
for the `induwara/feature/ocr-and-summarzation` branch.

---

## Prerequisites

- **Java 17+** (for Spring Boot backend)
- **Maven** (or use the included `mvnw.cmd`)
- **Python 3.9 – 3.11** (PaddleOCR does NOT support Python 3.12+)

---

## Step 1: Create a Python Virtual Environment

Open PowerShell inside the `Docusphere-Backend` folder:

```powershell
# Create venv
python -m venv venv

# Activate venv
.\venv\Scripts\Activate.ps1
```

> **Note (Windows):** If you get an execution policy error, run:
> `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

---

## Step 2: Install Python Dependencies

```powershell
pip install -r scripts/requirements.txt
```

> ⚠️ `paddlepaddle` and `paddleocr` are large packages (~1GB). Installation may take several minutes.
> Ensure you have a stable internet connection.

---

## Step 3: Create the `.env` File

Create a file named `.env` in the root of `Docusphere-Backend/` with the following content.
**Get these values from your team lead (Induwara).**

```env
SUPABASE_URL=your_supabase_project_url
SUPABASE_KEY=your_supabase_anon_key
SUPABASE_BUCKET=your_bucket_name
DB_URL=your_postgres_connection_url
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
JWT_SECRET=your_jwt_secret_key
```

> ⚠️ This file is intentionally excluded from Git (`.gitignore`). Never commit it.

---

## Step 4: Start the Backend

Use the provided PowerShell script which starts BOTH the Python OCR server (port 5000)
and the Spring Boot backend (port 8080):

```powershell
.\run-backend.ps1
```

---

## How It Works

```
Frontend (React)
    │
    ▼
Spring Boot API (port 8080)
    │
    ├──► Supabase (file storage + database)
    │
    └──► Python OCR Server (port 5000)
             │
             ├──► PaddleOCR  (extracts text from images/PDFs)
             └──► sumy + NLTK (generates summary, tags, key points)
```

### API Endpoints (Python OCR Server)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/extract` | POST | OCR only — extracts raw text from an image |
| `/analyze` | POST | Summarization only — summarizes provided text |
| `/process` | POST | Full pipeline — OCR + Summarization in one call |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `venv\Scripts\python.exe not found` | Complete Step 1 first |
| `ModuleNotFoundError: paddleocr` | Run `pip install -r scripts/requirements.txt` inside the venv |
| `JWT_SECRET` error on startup | Ensure `.env` file exists with the correct key |
| OCR server not starting | Check `ocr_server_error.log` for details |
| Port 8080 / 5000 already in use | `run-backend.ps1` will auto-kill existing processes |
