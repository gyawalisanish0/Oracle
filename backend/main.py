"""
Domain AI — Hugging Face Inference backend.

Exposes an OpenAI-compatible /v1 API that proxies requests to the
Hugging Face Inference API.  Deploy this as an HF Docker Space, set
HF_TOKEN as a Space secret, then point Domain AI's custom endpoint
at your Space URL.
"""

import json
import os
from typing import AsyncIterator

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

app = FastAPI(title="Domain AI Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

HF_API_BASE = "https://api-inference.huggingface.co/v1"
HF_HUB_BASE = "https://huggingface.co/api"


def _resolve_token(request: Request) -> str | None:
    """
    Token priority:
      1. Authorization: Bearer <token> from the incoming request
      2. HF_TOKEN environment variable (set as a Space secret)
    """
    auth = request.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        candidate = auth[7:].strip()
        if candidate:
            return candidate
    return os.environ.get("HF_TOKEN") or None


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok", "service": "domain-ai-backend"}


# ---------------------------------------------------------------------------
# Model listing  (/v1/models)
# ---------------------------------------------------------------------------

@app.get("/v1/models")
async def list_models(request: Request):
    """
    Returns inference-ready text-generation models from HF Hub in
    OpenAI-compatible format.  No token needed for public listing.
    """
    token = _resolve_token(request)
    headers = {"Authorization": f"Bearer {token}"} if token else {}

    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(
            f"{HF_HUB_BASE}/models",
            params={
                "pipeline_tag": "text-generation",
                "inference": "warm",
                "sort": "downloads",
                "direction": "-1",
                "limit": "30",
            },
            headers=headers,
        )

    if resp.status_code != 200:
        raise HTTPException(status_code=resp.status_code, detail=resp.text)

    hf_models = resp.json()
    return {
        "object": "list",
        "data": [
            {
                "id": m.get("modelId") or m.get("id", ""),
                "object": "model",
                "owned_by": m.get("author", "huggingface"),
                "downloads": m.get("downloads", 0),
            }
            for m in hf_models
            if m.get("modelId") or m.get("id")
        ],
    }


# ---------------------------------------------------------------------------
# Chat completions  (/v1/chat/completions)
# ---------------------------------------------------------------------------

@app.post("/v1/chat/completions")
async def chat_completions(request: Request):
    """
    Proxies an OpenAI-compatible chat completion request to the HF
    Inference API.  Supports both streaming (SSE) and non-streaming modes.

    The HF token must be available via the Authorization header or the
    HF_TOKEN environment variable; requests without a token are rejected.
    """
    token = _resolve_token(request)
    if not token:
        raise HTTPException(
            status_code=401,
            detail=(
                "No HF token found. Pass Authorization: Bearer <hf_token> "
                "or set HF_TOKEN as a Space secret."
            ),
        )

    body = await request.json()
    upstream_headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    if body.get("stream", False):
        return StreamingResponse(
            _stream_completion(body, upstream_headers),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    # Non-streaming path
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(
            f"{HF_API_BASE}/chat/completions",
            json=body,
            headers=upstream_headers,
        )

    if resp.status_code != 200:
        raise HTTPException(status_code=resp.status_code, detail=resp.text)

    return resp.json()


async def _stream_completion(
    body: dict, headers: dict
) -> AsyncIterator[str]:
    """Pipe SSE chunks from HF straight back to the client."""
    async with httpx.AsyncClient(timeout=120) as client:
        async with client.stream(
            "POST",
            f"{HF_API_BASE}/chat/completions",
            json=body,
            headers=headers,
        ) as resp:
            if resp.status_code != 200:
                error_body = await resp.aread()
                yield f"data: {json.dumps({'error': error_body.decode()})}\n\n"
                return

            async for chunk in resp.aiter_text():
                if chunk:
                    yield chunk
