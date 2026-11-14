# VYBE AI — quick start

1. Build/install the Android project as usual. The new **AI** tab is in the bottom navigation.
2. To enable generative features, deploy `ai-backend/` and configure `VYBE_AI_BASE_URL` in your local Gradle properties.
3. Configure an OpenAI-compatible Qwen3 endpoint in the backend environment. `ai-backend/.env.example` shows the variables.
4. Check `https://YOUR_HOST/health` before opening the AI tab.
5. See [AI_INTEGRATION.md](AI_INTEGRATION.md) for details, privacy limits, feature behavior and model links.

The app intentionally remains usable without the AI server: existing playback is unchanged, recommendations fall back to the existing VYBE engine, mood search falls back to catalogue search, and Smart DJ falls back to the existing local SmartShuffleEngine.
