from __future__ import annotations

import io
import json
import os
import re
from typing import Any

import httpx
import librosa
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

APP_VERSION = "0.1.0"
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "").rstrip("/")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_MODEL = os.getenv("LLM_MODEL", "qwen3:8b")
TIMEOUT = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "90"))
MAX_AUDIO_BYTES = int(os.getenv("MAX_AUDIO_BYTES", str(40 * 1024 * 1024)))

app = FastAPI(title="VYBE AI Backend", version=APP_VERSION)
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


class AssistantRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    context: dict[str, str] = Field(default_factory=dict)


class MoodSearchRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=500)
    language: str | None = None
    limit: int = Field(default=30, ge=1, le=50)


class LyricsRequest(BaseModel):
    lyrics: str = Field(min_length=1, max_length=12000)
    question: str = Field(default="Explain the meaning, mood and themes briefly.", max_length=1000)


class SongItem(BaseModel):
    id: str
    title: str
    artist: str
    album: str = ""
    language: str = ""
    durationSeconds: int = 0
    bpm: float | None = None
    key: str | None = None
    mood: str | None = None


class RecommendationRequest(BaseModel):
    history: list[SongItem] = Field(default_factory=list, max_length=60)
    candidates: list[SongItem] = Field(default_factory=list, max_length=80)
    limit: int = Field(default=12, ge=1, le=30)


class DjRequest(BaseModel):
    songs: list[SongItem] = Field(default_factory=list, max_length=80)
    startingSongId: str | None = None
    preferredMood: str | None = None


async def llm_chat(system: str, user: str, temperature: float = 0.35) -> str:
    if not LLM_BASE_URL:
        raise HTTPException(status_code=503, detail="LLM is not configured. Set LLM_BASE_URL and deploy an OpenAI-compatible Qwen3 endpoint.")
    headers = {"Content-Type": "application/json"}
    if LLM_API_KEY:
        headers["Authorization"] = f"Bearer {LLM_API_KEY}"
    payload = {
        "model": LLM_MODEL,
        "temperature": temperature,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            response = await client.post(f"{LLM_BASE_URL}/chat/completions", headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"].strip()
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Configured LLM request failed: {type(exc).__name__}") from exc


def parse_json_object(text: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip(), flags=re.IGNORECASE)
    try:
        value = json.loads(cleaned)
        return value if isinstance(value, dict) else {}
    except Exception:
        match = re.search(r"\{.*\}", cleaned, flags=re.DOTALL)
        if match:
            try:
                value = json.loads(match.group(0))
                return value if isinstance(value, dict) else {}
            except Exception:
                pass
    return {}


@app.get("/health")
async def health():
    return {"status": "ok", "version": APP_VERSION, "llmConfigured": bool(LLM_BASE_URL), "model": LLM_MODEL if LLM_BASE_URL else None}


@app.post("/v1/assistant")
async def assistant(request: AssistantRequest):
    answer = await llm_chat(
        "You are VYBE, a concise music discovery assistant. Help users find music and use their music app. "
        "Never claim to have played, queued, purchased, or changed anything. Return practical music suggestions. "
        "Do not reproduce copyrighted song lyrics.",
        json.dumps({"message": request.message, "context": request.context}, ensure_ascii=False),
    )
    return {"text": answer, "provider": LLM_MODEL}


@app.post("/v1/mood-search")
async def mood_search(request: MoodSearchRequest):
    if not LLM_BASE_URL:
        # Safe fallback: Android performs a normal catalog search using the original phrase.
        return {"query": request.prompt, "tags": [], "provider": "local-fallback"}
    raw = await llm_chat(
        "Convert a natural-language music mood request into concise search keywords for a music catalogue. "
        "Return JSON only: {\"query\": string, \"tags\": [string]}. Do not name songs unless the user did.",
        json.dumps({"prompt": request.prompt, "language": request.language, "limit": request.limit}, ensure_ascii=False),
        temperature=0.1,
    )
    result = parse_json_object(raw)
    return {"query": str(result.get("query") or request.prompt)[:240], "tags": result.get("tags", [])[:12], "provider": LLM_MODEL}


@app.post("/v1/lyrics/insights")
async def lyrics_insights(request: LyricsRequest):
    answer = await llm_chat(
        "Analyze only the user-provided lyrics. Explain themes, emotional tone, imagery and possible interpretations. "
        "Do not continue, reproduce, or quote substantial portions of the lyrics. Be clear that interpretations are subjective.",
        json.dumps({"lyrics": request.lyrics, "question": request.question}, ensure_ascii=False),
        temperature=0.25,
    )
    return {"text": answer, "provider": LLM_MODEL}


@app.post("/v1/recommendations")
async def recommendations(request: RecommendationRequest):
    candidates = request.candidates
    if not candidates:
        return {"songIds": []}
    if LLM_BASE_URL:
        raw = await llm_chat(
            "Rank candidate music tracks for this listener. Return JSON only as {\"songIds\":[...]} using ONLY candidate IDs. "
            "Use listening history as preference signals, balance familiarity and discovery, avoid repeated artists when possible.",
            json.dumps({"history": [x.model_dump() for x in request.history], "candidates": [x.model_dump() for x in candidates], "limit": request.limit}, ensure_ascii=False),
            temperature=0.1,
        )
        ids = parse_json_object(raw).get("songIds", [])
        valid = {x.id for x in candidates}
        ranked = [str(x) for x in ids if str(x) in valid]
        if ranked:
            return {"songIds": ranked[: request.limit], "provider": LLM_MODEL}
    # Deterministic fallback based on existing user taste metadata.
    artist_counts: dict[str, int] = {}
    language_counts: dict[str, int] = {}
    for item in request.history:
        artist_counts[item.artist.lower()] = artist_counts.get(item.artist.lower(), 0) + 1
        if item.language:
            language_counts[item.language.lower()] = language_counts.get(item.language.lower(), 0) + 1
    ranked = sorted(candidates, key=lambda x: (
        artist_counts.get(x.artist.lower(), 0) * 2 + language_counts.get(x.language.lower(), 0),
        -artist_counts.get(x.artist.lower(), 0),
    ), reverse=True)
    return {"songIds": [x.id for x in ranked[: request.limit]], "provider": "metadata-fallback"}


@app.post("/v1/dj/sequence")
async def dj_sequence(request: DjRequest):
    songs = request.songs
    if len(songs) < 2:
        return {"songIds": [x.id for x in songs]}
    if LLM_BASE_URL:
        raw = await llm_chat(
            "Create a smooth DJ-style sequence from the supplied tracks. Keep the startingSongId first when present. "
            "Prefer gradual changes in energy, compatible moods and varied artists. Return JSON only: {\"songIds\":[...]} "
            "and use only supplied IDs.",
            json.dumps({"songs": [x.model_dump() for x in songs], "startingSongId": request.startingSongId, "preferredMood": request.preferredMood}, ensure_ascii=False),
            temperature=0.15,
        )
        ids = parse_json_object(raw).get("songIds", [])
        valid = {x.id for x in songs}
        ranked = [str(x) for x in ids if str(x) in valid]
        if ranked:
            if request.startingSongId in valid:
                ranked = [request.startingSongId] + [x for x in ranked if x != request.startingSongId]
            ranked += [x.id for x in songs if x.id not in ranked]
            return {"songIds": ranked, "provider": LLM_MODEL}
    # Metadata-only fallback: keep the selected start track first, then diversify artists.
    start = next((x for x in songs if x.id == request.startingSongId), songs[0])
    rest = [x for x in songs if x.id != start.id]
    rest.sort(key=lambda x: (x.artist.lower(), x.title.lower()))
    return {"songIds": [start.id] + [x.id for x in rest], "provider": "metadata-fallback"}


@app.post("/v1/analyze")
async def analyze_audio(file: UploadFile = File(...)):
    if not (file.content_type or "").startswith("audio/") and file.content_type not in ("application/octet-stream",):
        raise HTTPException(status_code=415, detail="Please upload an audio file.")
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="The selected audio file is empty.")
    if len(raw) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail=f"Audio file exceeds {MAX_AUDIO_BYTES // (1024 * 1024)} MB.")
    try:
        audio, sample_rate = librosa.load(io.BytesIO(raw), sr=22050, mono=True)
        if len(audio) > sample_rate * 12 * 60:
            audio = audio[: sample_rate * 12 * 60]
        if len(audio) < sample_rate * 3:
            raise ValueError("Audio must be at least 3 seconds long.")
        tempo = float(librosa.feature.tempo(y=audio, sr=sample_rate)[0])
        rms = float(np.mean(librosa.feature.rms(y=audio)))
        centroid = float(np.mean(librosa.feature.spectral_centroid(y=audio, sr=sample_rate)))
        chroma = np.mean(librosa.feature.chroma_stft(y=audio, sr=sample_rate), axis=1)
        note_names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
        musical_key = note_names[int(np.argmax(chroma))]
        energy = float(np.clip(rms * 5.0, 0.0, 1.0))
        mood = "high-energy" if energy > 0.55 and tempo > 115 else "calm" if energy < 0.22 else "steady / balanced"
        tags = ["bright timbre" if centroid > 2500 else "warm timbre", "high energy" if energy > 0.55 else "low energy"]
        return {
            "text": "Audio analysis completed with librosa signal features. Key is an approximate chroma estimate, not a guaranteed musicological transcription.",
            "bpm": round(tempo, 1), "musicalKey": musical_key, "mood": mood,
            "energy": round(energy, 3), "tags": tags, "provider": "librosa",
        }
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"Could not analyze this audio format: {type(exc).__name__}") from exc
