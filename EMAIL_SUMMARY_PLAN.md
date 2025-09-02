# Email Summary Feature Plan (Android TV)

This document outlines a practical, reliable way to support emailing Gemini summaries from SmartTube on Android TV devices, where a traditional email app is usually not available.

## Overview
- Current UI: The Gemini summary overlay includes an “Email Summary” button (`overlay_video_summary.xml`).
- Current logic: `VideoMenuPresenter` triggers `Intent.ACTION_SENDTO` with `mailto:` and extras.
- Problem: Most Android TV devices have no email client, so the intent fails (“No email app found”).

## Goals
- Reliable send of summaries without requiring a local email client.
- Keep a simple user flow from the overlay (one or two clicks).
- Preserve privacy and avoid embedding secrets in the app.
- Provide graceful fallbacks if network or backend is unavailable.

## Approaches Considered
- Device Email Intent (as-is):
  - Pros: No backend work; uses user’s chosen client.
  - Cons: Fails on TV without an email app (common). Not reliable.
- Cloud Email Service (Recommended):
  - App POSTs summary to a server endpoint which sends the email via SendGrid/Mailgun/AWS SES.
  - Pros: Works on TV, secrets stay server-side, reliable logging/retries.
  - Cons: Requires a small backend and configuration.
- Google Apps Script Web App:
  - Pros: Quick prototype; no infra.
  - Cons: Gmail quotas, sender restrictions; less production‑ready.
- Fallbacks (non-email):
  - Copy to Clipboard: Let user paste on another device.
  - QR Code: Encode a short link to the summary; user opens on phone.
  - Messaging hooks (Telegram bot/Discord webhook): Simple “send to self”, but not email.

## Selected Design
- Primary: Cloud Email Service (+ simple HTTP POST from app).
- Keep: Device Email Intent (best effort when present).
- Add: Fallback actions in overlay (Copy, QR) for offline or misconfig scenarios.

## Client Changes
- Settings (Gemini Settings):
  - Send Method: `Device Email` | `Cloud Email` | `Ask Each Time`.
  - Recipient Email: already exists (`GeminiData.getSummaryEmail`).
  - Cloud Email Endpoint URL: string pref.
  - Cloud Email Auth Token: string pref (do not commit; user‑entered).
- Overlay actions:
  - Keep “Email Summary” primary button.
  - If method is `Ask Each Time`, show a small chooser: Email (Device), Cloud Email, Copy, QR.
  - Toasts for success/failure.
- Networking:
  - HTTP POST to backend endpoint with JSON payload (see API Spec below).
  - Reasonable timeout (e.g., 10s) and error handling (no retries by default; backend should retry).
- Fallbacks:
  - Copy: Copy subject + body to clipboard; show toast.
  - QR: Generate a QR for a short link to the summary. If body is large, upload to backend `/paste` or create a short‑lived link, then encode URL.
- Storage & Security:
  - Store endpoint/token/recipient in `GeminiData` (AppPrefs). Never check tokens into git.
  - Optionally allow loading defaults from a local properties file under `assets/` for sideloaded configs (not committed).

## Backend Email API Spec
- Endpoint: `POST /send-summary`
- Auth: `Authorization: Bearer <token>`
- Content-Type: `application/json`
- Request body:
  ```json
  {
    "to": "user@example.com",
    "subject": "SmartTube Summary: <video title>",
    "body": "<plain text summary with header>",
    "videoId": "<YouTube id>",
    "title": "<Video title>",
    "channel": "<Channel name>",
    "link": "https://www.youtube.com/watch?v=<id>"
  }
  ```
- Response:
  - 200: `{ "ok": true }`
  - 4xx/5xx: `{ "ok": false, "error": "..." }`
- Providers:
  - SendGrid (simplest), Mailgun, or AWS SES from a Node/Express or Firebase Function.
- Notes:
  - Log minimal PII. Mask recipient in logs.
  - Consider server‑side retries and rate limits.

## UX Details
- Success states:
  - Cloud email: Toast “Summary emailed to <recipient>”.
  - Device intent: Just handoff; if no handler, toast “No email app found”.
  - Copy: Toast “Summary copied to clipboard”.
  - QR: Show QR panel with “Scan to open summary”.
- Failure states:
  - Cloud email error: Show brief error and offer Copy/QR.
  - Network offline: Offer Copy/QR immediately.

## Implementation Notes (Client)
- Update `GeminiData` (common):
  - Add keys: `gemini_send_method`, `gemini_email_endpoint`, `gemini_email_token`.
- Update `GeminiSettingsPresenter` (common):
  - Add radio for Send Method; inputs for Endpoint/Token; recipient email already present.
- Update summary overlay wiring (`VideoMenuPresenter` + overlay classes):
  - If Send Method = Device Email → keep current `mailto:` intent.
  - If = Cloud Email → build JSON + POST; show toasts.
  - If = Ask Each Time → show chooser (Email, Cloud, Copy, QR).
  - Add Copy action using `ClipboardManager`.
  - Add QR action: use ZXing (or similar) to render QR into an overlay dialog.
- Networking: Use existing HTTP utils (if any) or a light client (OkHttp is often present; prefer existing deps).

## Implementation Notes (Backend)
- Minimal Node/Express example (SendGrid):
  - Env vars: `SENDGRID_API_KEY`, `AUTH_TOKEN`.
  - Validate bearer token, validate payload, send email.
  - Return JSON `{ok:true}` or error.
- Firebase Functions alternative:
  - HTTP onRequest function, same schema, SendGrid/Mailgun/SES client.
- Optional `/paste` endpoint to store large bodies and return a short URL (for QR).

## Testing
- Unit: JSON payload construction; settings persistence for endpoint/token/method.
- Instrumented: Overlay action routing (mock network), Copy/QR visibility.
- Manual:
  - No email client present → Device intent fails gracefully.
  - Cloud Email success and failure paths.
  - Large summaries with QR + paste flow.

## Risks & Mitigations
- No backend set up: Provide Copy/QR as always‑available fallback.
- Long body in mailto: Mailto may truncate; prefer Cloud Email.
- Token leakage: Never commit tokens; input via settings. Consider masking on screen.
- Deliverability: Use reputable providers (SPF/DKIM for custom domains if needed).

## Milestones
1) Add prefs + settings UI for Send Method, Endpoint, Token.
2) Implement Cloud Email POST and toasts.
3) Add Copy to Clipboard action.
4) Add QR flow (and backend `/paste` if needed).
5) Provide sample backend (Node/Express + SendGrid).
6) Document setup in BUILD_AND_DEPLOY and link to this file.

## Quick Setup Checklist
- Decide provider (SendGrid/Mailgun/SES) and deploy minimal endpoint.
- Configure in app: Recipient email, Send Method = Cloud Email, Endpoint URL, Auth token.
- Test send from overlay; verify fallback actions.

