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
- `Live2DPreviewActivity` now opens L01 as an isolated, user-triggered preview in the `:live2d` process.
- The preview uses `loadDataWithBaseURL` plus a local asset WebView interceptor because plain `file://` and a direct custom URL did not reliably start the Pixi/Live2D loader in emulator validation.
- Emulator validation produced a real Hiyori render, not a placeholder screenshot: `artifacts/debug-ui/live2d-preview-scaled-final.png`.

Next acceptance target:
- Reduce cold WebView/Live2D load time. Emulator validation has ranged from roughly 60-91 seconds before the first render, so the preview remains gated and uses a 120-second timeout.
- Verify WebView/WebGL load time, memory, CPU, and rendering on a real device.
- Only after that, promote it from technical preview to the main companion renderer.

License boundary:
- Do not treat this as the final commercial product role until the specific Live2D Sample Data terms are reviewed.
- Use it first as a development/technical verification model.
