# Linly-Talker Integration Plan

Date: 2026-05-24

This note records the investigation of `Kedreamix/Linly-Talker` and `Kedreamix/Linly-Talker-Stream`, and turns it into a concrete integration plan for Gouxiong Sleep.

## Finding

Linly-Talker is not a single missing model. It is an assembled digital-human pipeline:

```text
user audio/text -> ASR -> LLM -> TTS / voice clone -> avatar lip-sync video
```

The original Linly-Talker repository is strongest as an offline or semi-offline generation pipeline. It already wires together:

- ASR: Whisper, FunASR, OmniSenseVoice
- LLM: Linly, Qwen, Qwen2, Gemini, ChatGPT, ChatGLM, GPT4Free, QAnything
- TTS: EdgeTTS, PaddleTTS
- Voice clone: GPT-SoVITS, CosyVoice
- Talking avatar: SadTalker, Wav2Lip, Wav2Lipv2, NeRFTalk, MuseTalk
- API samples: separate FastAPI services for TTS, LLM, and Talker

Linly-Talker-Stream is the more relevant project for this product. It refactors the same idea into a real-time architecture:

```text
browser/app session -> WebRTC offer -> avatar session -> TTS audio chunks -> lip-sync frames -> WebRTC audio/video tracks
```

It already has modular folders for `asr`, `llm`, `tts`, `avatars`, `server`, and `web`, and supports switchable avatar engines through config:

- `wav2lip`
- `musetalk`
- `ernerf`
- `talkinggaussian`

Important limitation: the current Stream implementation still says full-duplex mainly depends on browser speech recognition for user-side endpoint detection, while avatar audio/video playback is streamed with WebRTC. It also lists server-side VAD as a TODO. So it is a valuable engineering baseline, not a complete replacement for our realtime session logic.

## Comparison With Current Project

Our current project already has product and mobile infrastructure that Linly does not provide:

- Native Android APK
- Sleep guard flows and local safety gates
- Long-term care memory
- `/api/live/session` WebSocket protocol
- PCM16 audio frame receive path
- Optional Aliyun Realtime bridge
- APK `AudioTrack` low-latency model audio playback
- Conservative auto barge-in detection
- Local layered 2D Avatar state machine

Linly provides the part we should stop rebuilding from scratch:

- Real talking-head avatar rendering
- Wav2Lip/MuseTalk style lip-sync
- Voice-clone TTS adapters
- WebRTC media return path
- Avatar model/asset packaging conventions

The right architecture is therefore not to replace our server. The right architecture is to add a digital-human media service beside it.

## Target Architecture

```text
Android APK
  |-- existing /api/live/session WebSocket
  |     |-- text turns
  |     |-- PCM16 frames
  |     |-- fallback TTS/audio frames
  |     |-- emotion events for local 2D Avatar
  |
  |-- new digital-human media session
        |-- WebRTC offer/answer
        |-- receives generated avatar audio/video stream
        |-- falls back to local 2D Avatar when unavailable

Gouxiong Node Server
  |-- auth, memory, sleep safety, companion model prompts
  |-- routes model text to Linly media service when digital human is enabled
  |-- never delegates wake-up safety or emergency behavior to Linly

Linly Media Service
  |-- based on Linly-Talker-Stream
  |-- first engine: wav2lip
  |-- later engine: musetalk
  |-- TTS: start with EdgeTTS or existing provider, then evaluate CosyVoice/GPT-SoVITS
```

## Phase 1: Sidecar Prototype

Goal: prove that our app can open a digital-human session without disturbing the existing sleep companion.

Use Linly-Talker-Stream almost as-is:

- Run backend on `8010`.
- Run web frontend only for validation, not as final UI.
- Configure `LINLY_TALKER_STREAM_URL` for the backend API and `LINLY_TALKER_STREAM_WEB_URL` for the browser validation page. If `WEB_URL` is empty, the Node server reports the backend URL as the preview fallback because Linly can also serve static `web` files from the same app.
- Use `config/config_wav2lip.yaml`.
- Use `wav2lip` because it has the smallest integration surface.
- Keep `asr.mode: browser` disabled for APK integration; our APK/server should remain responsible for user input.

Add a Node server capability flag:

```json
{
  "digital_human_stream": {
    "configured": true,
    "provider": "linly-talker-stream",
    "transport": "webrtc",
    "avatar_engine": "wav2lip",
    "base_url": "http://127.0.0.1:8010",
    "web_url": "http://127.0.0.1:8010"
  }
}
```

Add a server proxy endpoint later if the APK cannot talk to the sidecar directly:

```text
GET  /api/avatar/status
POST /api/avatar/session/offer
POST /api/avatar/session/:id/say
POST /api/avatar/session/:id/stop
GET  /api/avatar/session/:id/speaking
```

Map these to Linly Stream:

- `POST /offer` for WebRTC SDP handshake
- `POST /human` or equivalent text route for LLM/TTS/avatar speech
- `POST /humanaudio` for direct audio-driven speech
- `POST /interrupt_talk` / `flush_talk()` for interruption
- `POST /is_speaking` for speaking-state probes

## Phase 2: Product Integration

The APK keeps the current local 2D Avatar as the default reliable surface. Digital human becomes an optional media layer.

Current integration status:

- The Node server exposes stable `/api/avatar/*` proxy endpoints.
- Authenticated `GET /api/avatar/status` probes Linly `/health` and reports `live` separately from static configuration.
- Android `ServerApiClient` parses Linly digital-human status from `/health`.
- Android `ServerApiClient` has control-plane helpers for `avatarStatus`, `avatarOffer`, `avatarSay`, `avatarStop`, and `avatarSpeaking`.
- The APK "诚实检查" page shows whether Linly is configured and still calls out local 2D Avatar fallback.
- The APK has a gated `LinlyAvatarPreviewActivity` from the honest-check page. The `Linly` button opens the configured `web_url` in Android WebView so the upstream browser client can be tested on-device.
- The same Activity also has a `Relay` preview that loads `assets/linly/relay-preview.html`, creates a WebRTC offer in WebView, sends it through authenticated `/api/avatar/session/offer`, and drives speech through `/api/avatar/session/:id/say`. This validates the Gouxiong server proxy path instead of bypassing it.
- Native WebRTC media playback is not implemented yet. The WebView preview is a validation bridge, not the final embedded media layer.

State mapping:

| Gouxiong state | Linly action |
| --- | --- |
| `LISTENING` | keep WebRTC session alive, idle/silent avatar frames |
| `THINKING` | local 2D Avatar may show thinking; Linly does not need to speak |
| `SPEAKING` | send final or streaming reply text to Linly TTS/avatar |
| `INTERRUPTED` | call sidecar stop/flush and return APK to listening |
| `FALLBACK_TEXT` | bypass Linly and use current local TTS/2D Avatar |
| `SLEEP_GUARD` | do not invoke Linly media service |

The server remains the owner of:

- bedtime safety rules
- emergency contact behavior
- strong wake-up countdowns
- medication and hydration reminders
- long-term memory write policy
- prompt constraints for elderly/sleep companion behavior

Linly only owns:

- generated voice audio
- talking-head video
- avatar media session lifecycle

## Phase 3: MuseTalk And Voice Clone

After Wav2Lip proves the transport and lifecycle:

- Evaluate `musetalk` for more natural 2D lip-sync.
- Add a Chinese-friendly TTS provider with lower latency and stable licensing.
- Evaluate CosyVoice zero-shot or GPT-SoVITS only after consent and privacy copy are in place, because voice cloning is sensitive.
- Store voice-clone reference audio only with explicit consent, clear deletion, and no hidden background recording.

Do not put voice clone models or private keys in the APK.

## Files Reviewed

Local clones:

- `tmp/Linly-Talker`
- `tmp/Linly-Talker-Stream`

Most relevant Linly-Talker files:

- `webui.py`: end-to-end ASR/LLM/TTS/Talker orchestration
- `api/talker_api.py`: FastAPI video generation endpoint
- `api/tts_api.py`: FastAPI TTS and voice-clone endpoint
- `TFG/SadTalker.py`: SadTalker wrapper
- `TFG/Wav2Lip.py`: Wav2Lip wrapper
- `TFG/MuseTalk.py`: MuseTalk realtime-ish wrapper
- `VITS/GPT_SoVITS.py`: GPT-SoVITS inference wrapper
- `VITS/CosyVoice.py`: CosyVoice wrapper

Most relevant Linly-Talker-Stream files:

- `src/server/app.py`: loads config, preloads avatar model, starts server
- `src/server/routes/webrtc.py`: WebRTC offer and track setup
- `src/server/routes/audio.py`: `/asr` and `/humanaudio` routes
- `src/utils/webrtc.py`: `HumanPlayer` and audio/video track timing
- `src/avatars/base.py`: shared avatar/TTS/audio queue abstraction
- `src/avatars/factory.py`: avatar engine selection
- `src/avatars/wav2lip/avatar.py`: Wav2Lip realtime avatar loop
- `src/tts/base.py`: text queue to audio chunk pipeline
- `src/llm/service.py`: per-session LLM instance and response routing
- `config/config_wav2lip.yaml`: minimal 2D avatar config

## Acceptance Criteria

Prototype is acceptable when:

- Existing `/api/live/session` tests still pass.
- Server health clearly distinguishes local 2D Avatar from Linly digital-human stream.
- APK can start a digital-human WebRTC session and receive nonblank audio/video.
- A text reply from our server can drive the Linly avatar to speak.
- User interruption stops local playback and Linly avatar speech within 500 ms.
- When Linly service is down, the APK falls back to current local 2D Avatar and TTS.
- Night sleep guard and emergency behavior remain local/server-owned and never depend on Linly.

## Recommendation

Do not copy the old Linly-Talker monolith into this repository.

Use Linly-Talker-Stream as a sidecar service first. Treat it as a media engine, not the product brain. Our app keeps the companion brain, safety logic, memory, and Android experience; Linly supplies the missing talking-head media layer.
