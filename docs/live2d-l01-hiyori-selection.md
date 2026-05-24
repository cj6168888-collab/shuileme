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

Next acceptance target:
- Reduce cold WebView/Live2D load time. Emulator validation has ranged from roughly 60-91 seconds before the first render, so the preview remains gated and uses a 120-second timeout.
- Make the isolated preview pass `android-app/e2e-live2d-preview.ps1` without ANR/not-responding dialogs.
- Verify WebView/WebGL load time, memory, CPU, and rendering on a real device.
- Only after that, promote it from technical preview to the main companion renderer.

License boundary:
- Do not treat this as the final commercial product role until the specific Live2D Sample Data terms are reviewed.
- Use it first as a development/technical verification model.
