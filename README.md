# J.A.R.V.I.S. — Voice-Only Bilingual AI Assistant

Voice-only Iron-Man-style AI assistant for **Android (Jetpack Compose)** and **PWA (`pwa/`)**, supporting **Hindi, English, and Hinglish** with Google Gemini API.

## Download

Replace `<YOUR_GITHUB_USERNAME>` and `<REPO_NAME>` below after pushing to GitHub:

[![Download Latest Release](https://img.shields.io/badge/Download-Latest%20Release-00e5ff?style=for-the-badge&logo=github&logoColor=white)](https://github.com/<YOUR_GITHUB_USERNAME>/<REPO_NAME>/releases/latest)

- **All Releases:** `https://github.com/<YOUR_GITHUB_USERNAME>/<REPO_NAME>/releases`

## Features

- **Voice-Only HUD:** Glowing animated arc-reactor mic button with Listening, Thinking, and Speaking states plus live scrolling transcript.
- **Hindi, English & Hinglish:** Auto-detects Devanagari and Hinglish phrasing and responds in the matching language.
- **Local Voice Commands:** Handles time checks, opening YouTube/Google/WhatsApp, countdown timers, stop, repeat, and language switching locally before calling Gemini.
- **Wake Mode & Memory:** Optional `"Jarvis"` wake mode and 10-message conversation context.
- **No Hardcoded Keys:** First-launch setup screen saves your Gemini API key locally on device.

## Project Structure

- `app/` — Native Android Jetpack Compose application
- `pwa/` — Standalone Progressive Web App (`index.html`, `manifest.json`, `service-worker.js`)
- `.github/workflows/release.yml` — Automated GitHub Actions workflow to build & publish APK releases
