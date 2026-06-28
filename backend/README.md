---
title: Domain AI Backend
emoji: 🤖
colorFrom: blue
colorTo: indigo
sdk: docker
pinned: false
license: apache-2.0
short_description: OpenAI-compatible gateway for Hugging Face Inference API
---

# Domain AI Backend

A lightweight FastAPI gateway that exposes an **OpenAI-compatible `/v1` API**
backed by the [Hugging Face Inference API](https://huggingface.co/docs/api-inference).
Designed to be deployed as a Hugging Face Docker Space so the
[Domain AI Android app](https://github.com/gyawalisanish0/DomainAI) can use
any HF-hosted text-generation model as its cloud backend.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Liveness check |
| `GET` | `/v1/models` | Lists inference-ready HF models (OpenAI format) |
| `POST` | `/v1/chat/completions` | Chat completion — streaming & non-streaming |

## Deploying to Hugging Face Spaces

1. **Create a new Space** on [huggingface.co/new-space](https://huggingface.co/new-space)
   — choose **Docker** as the SDK.
2. **Push this directory** (or the whole repo) to the Space:
   ```bash
   git remote add space https://huggingface.co/spaces/<your-username>/domain-ai-backend
   git subtree push --prefix backend space main
   ```
3. **Set your HF token as a Space secret** — go to Space → Settings → Variables and secrets,
   add `HF_TOKEN` = your token from [hf.co/settings/tokens](https://huggingface.co/settings/tokens).

## Configuring the Android app

In Domain AI → Settings → Cloud → **Advanced: custom endpoint**:

| Field | Value |
|-------|-------|
| Base URL | `https://<your-username>-domain-ai-backend.hf.space/v1` |
| API key | Your HF token (or leave blank — Space secret is used as fallback) |
| Model | Any HF model ID, e.g. `meta-llama/Llama-3.2-3B-Instruct` |

## Running locally

```bash
pip install -r requirements.txt
HF_TOKEN=hf_xxx uvicorn main:app --reload --port 7860
```
