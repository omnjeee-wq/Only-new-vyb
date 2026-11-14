# VYBE AI integration

This branch adds an **AI Hub** destination with six music-intelligence workflows while preserving the existing JioSaavn repository, Media3 player, Room history and recommendation engines.

## What is connected

1. **Mood search** — optional Qwen3 prompt interpretation (`POST /v1/mood-search`), followed by the existing music catalogue search. If no AI server is configured, the original phrase is searched as a normal catalogue query.
2. **Personalized recommendations** — reuses VYBE's existing listening-history recommendation engine; optionally asks Qwen3 to rerank the already retrieved candidate list. The AI never invents playable song IDs.
3. **Music understanding** — user explicitly selects an audio file; the app uploads it to the configured backend. The included backend uses librosa for approximate BPM, chroma/key, energy and timbre features. Key/mood are estimates, not authoritative labels.
4. **VYBE AI assistant** — Qwen3 through an OpenAI-compatible endpoint (Ollama, vLLM, etc.).
5. **Lyrics intelligence** — sends only text the user pastes to the configured LLM for thematic interpretation. The endpoint is instructed not to reproduce lyrics.
6. **Smart DJ queue** — reuses VYBE's existing on-device SmartShuffleEngine; optionally asks Qwen3 to sequence the current queue. The current track remains first and only IDs already in the queue are accepted.

AI calls are optional. Without a configured backend, playback and the local recommendation / Smart DJ paths remain available. Generative assistant and lyrics analysis require the backend.

## Configure the Android app

Add this to your **user-level** `~/.gradle/gradle.properties` (not to source control):

```properties
VYBE_AI_BASE_URL=https://your-public-https-ai-host/
```

Then rebuild the app. The app requires HTTPS because the manifest disallows cleartext HTTP. Do not put a private LLM API key in the Android app; keep provider credentials on the backend.

## Run the backend

1. Install Python 3.11+ and FFmpeg.
2. In `ai-backend/`, create a virtual environment and install `requirements.txt`.
3. Copy `.env.example` to `.env` and set the LLM endpoint/model.
4. Start Ollama (or another OpenAI-compatible server) and pull a Qwen3 model supported by that server. Example Ollama model names vary by quantization and available hardware.
5. Run `uvicorn main:app --host 0.0.0.0 --port 8000`.
6. Expose it through a trusted HTTPS reverse proxy/tunnel, then use that HTTPS URL in Gradle properties.

Docker is also supported:

```bash
docker build -t vybe-ai ./ai-backend
docker run --env-file ai-backend/.env -p 8000:8000 vybe-ai
```

If the LLM runs on the host and the API runs in Docker, set `LLM_BASE_URL` to the host address reachable from the container (for example `http://host.docker.internal:11434/v1` where supported).

## API health check

Open `https://your-host/health`. It should return `status: ok`. `llmConfigured` must be true for assistant, lyrics explanation and LLM reranking.

## Open-source components / model references

- **Qwen3** (Apache 2.0 open-weight models; use the license attached to the exact model checkpoint): https://github.com/QwenLM/Qwen3
- **MOSS-Music** (Apache 2.0; optional future upgrade for music captioning, singing-lyrics ASR, structure/chord/key/tempo reasoning; not bundled into the Android APK): https://github.com/OpenMOSS/MOSS-Music
- **LAION CLAP** (audio/text embedding research implementation; optional future upgrade for semantic audio-to-text matching; not bundled in this integration): https://github.com/LAION-AI/CLAP
- **librosa** (audio analysis library used by the included backend): https://github.com/librosa/librosa

Large models are not embedded in the APK. They run on a server with suitable memory/compute. The backend currently uses librosa for actual uploaded-audio signal analysis; MOSS-Music and CLAP are documented extension points, not falsely represented as active integrations.

## Important limits

- The app's current music provider and stream URLs remain unchanged. The AI backend does not download JioSaavn tracks or bypass provider restrictions.
- For audio analysis, select a local file that you own or have permission to analyze. Files are sent to the configured backend and are limited to 40 MB / 12 minutes by default.
- Do not expose the backend publicly without authentication, rate limiting, HTTPS and upload controls. Add your own auth gateway before sharing the service beyond trusted users.
- The included recommendation and DJ fallbacks use available metadata/history. They are not claimed to be trained neural recommendation models.
