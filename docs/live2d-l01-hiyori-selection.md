# Live2D L01 Hiyori Selection

Date: 2026-05-24

Selected candidate: `L01 Hiyori Momose`.

Source:
- Live2D official Sample Data page.
- Cubism Web Samples repository runtime resources.

Project location:
- `android-app/src/main/assets/live2d/hiyori/Hiyori/Hiyori.model3.json`
- `android-app/src/main/assets/live2d/hiyori/index.html`
- `android-app/src/main/assets/live2d/selected-live2d-model.json`

Current status:
- Hiyori resources are bundled for technical preview.
- The APK build now supports packaging `src/main/assets`.
- Directly enabling the Live2D WebView as the default companion renderer caused emulator system unresponsiveness during cold start, so it is not enabled by default yet.

Next acceptance target:
- Open Hiyori in an isolated preview screen without blocking app launch.
- Verify WebView/WebGL load time, memory, CPU, and rendering on emulator and real device.
- Only after that, promote it from technical preview to the main companion renderer.

License boundary:
- Do not treat this as the final commercial product role until the specific Live2D Sample Data terms are reviewed.
- Use it first as a development/technical verification model.
