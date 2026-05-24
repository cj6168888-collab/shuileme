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
- `Live2DPreviewActivity` now opens L01 as a user-triggered preview. It is not exposed in the normal user flow.
- The preview uses `loadDataWithBaseURL` plus a local asset WebView interceptor because plain `file://` and a direct custom URL did not reliably start the Pixi/Live2D loader in emulator validation.
- Emulator validation produced a real Hiyori render, not a placeholder screenshot: `artifacts/debug-ui/live2d-preview-scaled-final.png`.
- `android-app/e2e-live2d-preview.ps1` provides a repeatable honest validation path: install/start the isolated preview, wait for a loaded status, capture screenshot/logcat, and run a simple pixel check so a blank WebView is not counted as passing.
- 2026-05-24 follow-up validation failed on the emulator: the isolated `:live2d` process and MainActivity-backed debug action can show Android's not-responding dialog during cold start. Because of that, the main companion UI no longer exposes a user-facing Live2D button. The preview remains a development-only validation target until it passes without ANR.
- `Live2DPreviewActivity` is not exported. External apps and direct `adb shell am start -n com.gouxiong.sleep/.Live2DPreviewActivity` are denied; debug validation must enter through the small `DebugLive2DEntryActivity`, which exists only to avoid MainActivity startup cost while testing the gated preview.
- `android-app/e2e-live2d-gate.ps1` checks the gate itself: direct Activity start must be denied, while the internal debug entry may open the non-loading preview shell without ANR. This passed on 2026-05-24 with evidence in `artifacts/debug-ui/live2d-gate-20260524-095513`.
- The preview is now split into a lightweight native shell (`Live2DPreviewActivity`) and a separate WebView renderer (`Live2DWebViewActivity`) that only starts after pressing Load.
- 2026-05-24 rendering validation passed with the original Hiyori 2048 textures after two fixes: large Live2D asset types are stored uncompressed in the APK, and `Live2DWebViewActivity` waits for window focus before starting WebView/Live2D loading. Evidence: `artifacts/debug-ui/live2d-e2e-20260524-102339`.
- 2026-05-24 command validation passed: `android-app/e2e-live2d-preview.ps1 -AutoLoad -CommandCheck` verified native-to-JS mood commands (`thinking`, `speaking`, `comforting`) and mouth level commands after a real render. Evidence: `artifacts/debug-ui/live2d-e2e-20260524-104130`.
- A 1024 texture experiment was rejected because it could report ready while failing the pixel render check; the app uses the original atlas for honest rendering.
- This preview is intentionally separate from the user's selected assistant character. It validates the Live2D runtime path only; the main companion stage still uses the selected role through native `AvatarView` plus role PNG assets.
- A no-motion-preload experiment was rejected for now. One run rendered and accepted commands, but the follow-up run produced `PixelCheck: false` with bridge ready around 117 seconds, so it is not reliable enough to keep. Evidence: `artifacts/debug-ui/live2d-e2e-20260524-142906`.

Next acceptance target:
- Reduce cold WebView/Live2D load time. The current honest preview pass still needs roughly 90 seconds before first render on the emulator, so the preview remains gated and uses a 180-second timeout.
- Promote only after repeated passes and after main companion integration can drive mood and mouth movement without blocking voice interaction during a real voice session.
- Verify WebView/WebGL load time, memory, CPU, and rendering on a real device.
- Only after that, promote it from technical preview to the main companion renderer.

License boundary:
- Do not treat this as the final commercial product role until the specific Live2D Sample Data terms are reviewed.
- Use it first as a development/technical verification model.
