package com.gouxiong.sleep.avatar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

import com.gouxiong.sleep.util.CompanionAssistant;
import com.gouxiong.sleep.util.Theme;

public final class AvatarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private AvatarState state = AvatarState.LISTENING;
    private String role = CompanionAssistant.ROLE_GENTLE_WOMAN;
    private String emotion = "";
    private float emotionIntensity = 0.65f;
    private Bitmap characterBitmap;
    private int characterResourceId;
    private boolean characterBitmapMode;
    private float targetMouthLevel;
    private float mouthLevel;
    private float blinkLevel;
    private float nodLevel;
    private float lookX;
    private float lookY;
    private float targetLookX;
    private float targetLookY;
    private float waveLevel;
    private boolean speaking;
    private boolean attached;
    private boolean animationEnabled = true;
    private long stateStartedAtMs = System.currentTimeMillis();
    private long lastBlinkAtMs = 0L;
    private long nextAutonomousGazeAtMs = 0L;
    private final Runnable frameTicker = new Runnable() {
        @Override
        public void run() {
            if (!attached) return;
            advanceFrame();
            invalidate();
            postDelayed(this, 33L);
        }
    };

    public AvatarView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        if (Build.VERSION.SDK_INT >= 21) {
            setElevation(Theme.dp(context, 1));
        }
    }

    public void setRole(String role) {
        this.role = CompanionAssistant.normalize(role);
        invalidate();
    }

    public void setCharacterResource(int resId) {
        if (resId == 0 || resId == characterResourceId && characterBitmap != null) return;
        characterResourceId = resId;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        characterBitmap = BitmapFactory.decodeResource(getResources(), resId, options);
        invalidate();
    }

    public void setCharacterBitmapMode(boolean enabled) {
        characterBitmapMode = enabled;
        invalidate();
    }

    public void setAnimationEnabled(boolean enabled) {
        animationEnabled = enabled;
        removeCallbacks(frameTicker);
        if (attached && animationEnabled) {
            post(frameTicker);
        }
        invalidate();
    }

    public AvatarState state() {
        return state;
    }

    public void applyCommand(AvatarCommand command) {
        if (command == null || command.type == null) return;
        if (AvatarCommand.SET_STATE.equals(command.type)) {
            setAvatarState(command.state);
        } else if (AvatarCommand.SET_EMOTION.equals(command.type)) {
            emotion = command.emotion;
            emotionIntensity = command.intensity;
            applyEmotionFallback(command.emotion);
        } else if (AvatarCommand.START_SPEAKING.equals(command.type)) {
            speaking = true;
            setAvatarState(AvatarState.SPEAKING);
            targetMouthLevel = Math.max(targetMouthLevel, command.mouthLevel);
        } else if (AvatarCommand.STOP_SPEAKING.equals(command.type)) {
            speaking = false;
            targetMouthLevel = 0f;
        } else if (AvatarCommand.MOUTH_LEVEL.equals(command.type)) {
            targetMouthLevel = command.mouthLevel;
            if (command.mouthLevel > 0.02f) speaking = true;
        } else if (AvatarCommand.BLINK.equals(command.type)) {
            blinkLevel = 1f;
            lastBlinkAtMs = System.currentTimeMillis();
        } else if (AvatarCommand.NOD.equals(command.type)) {
            nodLevel = 1f;
        } else if (AvatarCommand.LOOK_AT_USER.equals(command.type)) {
            setGazeTarget(0f, 0f);
        } else if (AvatarCommand.LOOK_DOWN.equals(command.type)) {
            setGazeTarget(0f, 1f);
        } else if (AvatarCommand.WAVE.equals(command.type)) {
            waveLevel = 1f;
        } else if (AvatarCommand.URGENT_WAKE.equals(command.type)) {
            emotion = "urgent";
            emotionIntensity = 1f;
            speaking = true;
            targetMouthLevel = Math.max(targetMouthLevel, 0.7f);
            setAvatarState(AvatarState.URGENT_WAKEUP);
        }
        invalidate();
    }

    private void setAvatarState(AvatarState next) {
        AvatarState clean = next == null ? AvatarState.LISTENING : next;
        if (clean == state) return;
        state = clean;
        stateStartedAtMs = System.currentTimeMillis();
        if (state != AvatarState.SPEAKING && state != AvatarState.URGENT_WAKEUP) {
            targetMouthLevel = 0f;
            speaking = false;
        }
        if (state == AvatarState.URGENT_WAKEUP || state == AvatarState.HAPPY) {
            waveLevel = 1f;
        }
        seedStateGaze();
    }

    private void applyEmotionFallback(String value) {
        String clean = value == null ? "" : value.toLowerCase();
        if (clean.contains("happy") || clean.contains("smile")) setAvatarState(AvatarState.HAPPY);
        else if (clean.contains("worr") || clean.contains("urgent") || clean.contains("risk")) setAvatarState(AvatarState.WORRIED);
        else if (clean.contains("comfort")) setAvatarState(AvatarState.COMFORTING);
        else if (clean.contains("think")) setAvatarState(AvatarState.THINKING);
        else if (clean.contains("speak")) setAvatarState(AvatarState.SPEAKING);
        else if (clean.contains("listen")) setAvatarState(AvatarState.LISTENING);
    }

    private void advanceFrame() {
        long now = System.currentTimeMillis();
        if (lastBlinkAtMs == 0L || now - lastBlinkAtMs > blinkInterval(now)) {
            blinkLevel = 1f;
            lastBlinkAtMs = now;
        }
        blinkLevel = approach(blinkLevel, 0f, 0.16f);
        nodLevel = approach(nodLevel, 0f, 0.08f);
        waveLevel = approach(waveLevel, 0f, 0.045f);
        updateAutonomousGaze(now);
        lookX = approach(lookX, targetLookX, 0.055f);
        lookY = approach(lookY, targetLookY, 0.055f);
        if (speaking && targetMouthLevel < 0.08f) {
            float wave = (float) Math.abs(Math.sin(now / 92d));
            targetMouthLevel = 0.18f + wave * 0.46f;
        }
        mouthLevel = approach(mouthLevel, targetMouthLevel, 0.22f);
        if (!speaking || targetMouthLevel > 0.08f) {
            targetMouthLevel = approach(targetMouthLevel, speaking ? targetMouthLevel : 0f, 0.12f);
        }
    }

    private long blinkInterval(long now) {
        long offset = Math.abs((role + state.wireName()).hashCode() % 900);
        return 2600L + offset + (long) (Math.sin(now / 1300d) * 260d);
    }

    private void seedStateGaze() {
        if (state == AvatarState.THINKING || state == AvatarState.READING || state == AvatarState.FINDING) {
            setGazeTarget(-0.18f, 0.45f);
        } else if (state == AvatarState.COMFORTING) {
            setGazeTarget(0.0f, 0.18f);
        } else if (state == AvatarState.SEEING) {
            setGazeTarget(0.28f, -0.12f);
        } else {
            setGazeTarget(0f, 0f);
        }
        nextAutonomousGazeAtMs = System.currentTimeMillis() + 900L;
    }

    private void updateAutonomousGaze(long now) {
        if (state == AvatarState.URGENT_WAKEUP || state == AvatarState.SPEAKING) {
            setGazeTarget(0f, 0f);
            nextAutonomousGazeAtMs = now + 700L;
            return;
        }
        if (now < nextAutonomousGazeAtMs) return;
        float phase = (float) Math.sin(now / 1000d + role.hashCode() * 0.001d);
        if (state == AvatarState.LISTENING || state == AvatarState.USER_SPEAKING) {
            setGazeTarget(phase * 0.24f, (float) Math.sin(now / 1470d) * 0.08f);
            nextAutonomousGazeAtMs = now + 1400L + Math.abs((role + now).hashCode() % 900);
        } else if (state == AvatarState.THINKING || state == AvatarState.READING || state == AvatarState.FINDING) {
            setGazeTarget(phase * 0.16f, 0.38f + Math.abs((float) Math.sin(now / 1800d)) * 0.22f);
            nextAutonomousGazeAtMs = now + 1700L;
        } else if (state == AvatarState.COMFORTING || state == AvatarState.WORRIED) {
            setGazeTarget(phase * 0.10f, 0.10f + Math.abs((float) Math.sin(now / 2000d)) * 0.12f);
            nextAutonomousGazeAtMs = now + 2100L;
        } else {
            setGazeTarget(phase * 0.12f, 0f);
            nextAutonomousGazeAtMs = now + 1800L;
        }
    }

    private void setGazeTarget(float x, float y) {
        targetLookX = clampSigned(x);
        targetLookY = clampSigned(y);
    }

    private float clampSigned(float value) {
        if (value < -1f) return -1f;
        if (value > 1f) return 1f;
        return value;
    }

    private float approach(float value, float target, float step) {
        return value + (target - value) * Math.max(0f, Math.min(1f, step));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        removeCallbacks(frameTicker);
        if (animationEnabled) {
            post(frameTicker);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(frameTicker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = Theme.dp(getContext(), 232);
        int width = resolveSize(defaultSize, widthMeasureSpec);
        int height = resolveSize(defaultSize, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f + size * 0.03f;
        long now = System.currentTimeMillis();
        float t = (now - stateStartedAtMs) / 1000f;
        float breath = (float) Math.sin(now / 620d) * stateBreathScale();
        float nod = nodLevel * (float) Math.sin(now / 95d) * size * 0.014f;
        if (characterBitmapMode && characterBitmap != null) {
            drawCharacterBitmapStage(canvas, cx, cy, size, t, breath, nod);
            return;
        }
        canvas.save();
        canvas.translate(0f, breath * size + nod);
        drawHalo(canvas, cx, cy, size, t);
        drawBody(canvas, cx, cy, size, t);
        drawHead(canvas, cx, cy, size);
        drawHair(canvas, cx, cy, size);
        drawEars(canvas, cx, cy, size);
        drawFace(canvas, cx, cy, size, t);
        drawHands(canvas, cx, cy, size, t);
        canvas.restore();
    }

    private void drawCharacterBitmapStage(Canvas canvas, float cx, float cy, float size, float t, float breath, float nod) {
        drawHalo(canvas, cx, cy, size, t);
        float pulse = state == AvatarState.SPEAKING || state == AvatarState.URGENT_WAKEUP
                ? mouthLevel * 0.020f
                : 0f;
        float scale = 1f + Math.abs(breath) * 0.62f + pulse;
        float fitW = getWidth() * 0.92f;
        float fitH = getHeight() * 0.98f;
        float bitmapScale = Math.min(fitW / Math.max(1, characterBitmap.getWidth()), fitH / Math.max(1, characterBitmap.getHeight()));
        float destW = characterBitmap.getWidth() * bitmapScale;
        float destH = characterBitmap.getHeight() * bitmapScale;
        float bottom = getHeight() + Theme.dp(getContext(), 4);
        float left = cx - destW / 2f + lookX * size * 0.020f;
        float top = bottom - destH + lookY * size * 0.012f;

        canvas.save();
        canvas.translate(0f, breath * size + nod);
        canvas.scale(scale, scale, cx, bottom);
        float right = left + destW;
        float destBottom = top + destH;
        rect.set(left, top, right, destBottom);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(characterBitmap, null, rect, paint);
        canvas.restore();

        drawBitmapStateCue(canvas, left, top, right, destBottom, size, t);
    }

    private void drawBitmapStateCue(Canvas canvas, float left, float top, float right, float bottom, float size, float t) {
        float cx = (left + right) / 2f;
        float faceY = top + (bottom - top) * bitmapFaceYRatio();
        float mouthY = top + (bottom - top) * bitmapMouthYRatio();
        float unit = Math.max(Theme.dp(getContext(), 8), size * 0.032f);
        paint.setStyle(Paint.Style.FILL);
        if (state == AvatarState.URGENT_WAKEUP) {
            paint.setColor(Color.argb(205, 235, 70, 52));
            canvas.drawCircle(cx - unit * 1.95f, faceY - unit * 5.2f, unit * (1.0f + 0.16f * (float) Math.sin(t * 12f)), paint);
            canvas.drawCircle(cx, faceY - unit * 6.1f, unit * (1.0f + 0.16f * (float) Math.sin(t * 12f + 1.2f)), paint);
            canvas.drawCircle(cx + unit * 1.95f, faceY - unit * 5.2f, unit * (1.0f + 0.16f * (float) Math.sin(t * 12f + 2.4f)), paint);
            return;
        }
        if (state == AvatarState.SPEAKING || mouthLevel > 0.08f) {
            drawBitmapMouthHint(canvas, cx, mouthY, unit, t);
            return;
        }
        if (state == AvatarState.SEEING || state == AvatarState.READING || state == AvatarState.FINDING) {
            paint.setColor(Theme.mix(Theme.GREEN, Color.WHITE, 0.12f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(unit * 0.32f);
            float lensX = cx + unit * 3.6f;
            float lensY = faceY - unit * 0.7f;
            canvas.drawCircle(lensX, lensY, unit * 1.05f, paint);
            canvas.drawLine(lensX + unit * 0.72f, lensY + unit * 0.72f, lensX + unit * 1.65f, lensY + unit * 1.62f, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
    }

    private void drawBitmapMouthHint(Canvas canvas, float cx, float mouthY, float unit, float t) {
        float open = unit * (0.28f + mouthLevel * 1.18f + (float) Math.abs(Math.sin(t * 7.5f)) * 0.16f);
        float width = unit * (1.18f + mouthLevel * 0.54f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(112, 70, 36, 36));
        canvas.drawOval(oval(cx, mouthY, width, open), paint);
        paint.setColor(Color.argb(92, 255, 128, 128));
        canvas.drawOval(oval(cx, mouthY + open * 0.32f, width * 0.58f, open * 0.28f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(unit * 0.14f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(148, 94, 48, 42));
        rect.set(cx - width * 1.10f, mouthY - open * 1.65f, cx + width * 1.10f, mouthY + open * 1.35f);
        canvas.drawArc(rect, 28f, 124f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private float bitmapFaceYRatio() {
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) return 0.39f;
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) return 0.34f;
        return 0.335f;
    }

    private float bitmapMouthYRatio() {
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) return 0.47f;
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) return 0.405f;
        return 0.385f;
    }

    private float stateBreathScale() {
        if (state == AvatarState.URGENT_WAKEUP) return 0.016f;
        if (state == AvatarState.THINKING || state == AvatarState.SEEING || state == AvatarState.READING) return 0.007f;
        return 0.010f;
    }

    private int accentColor() {
        return CompanionAssistant.roleColor(role);
    }

    private int hairColor() {
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) return Color.rgb(60, 57, 47);
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) return Color.rgb(86, 65, 48);
        if (CompanionAssistant.ROLE_SISTER.equals(role)) return Color.rgb(86, 54, 42);
        return Color.rgb(115, 72, 55);
    }

    private int clothColor() {
        if (CompanionAssistant.ROLE_SISTER.equals(role)) return Color.rgb(255, 142, 125);
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) return Color.rgb(112, 176, 127);
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) return Color.rgb(103, 162, 215);
        return Color.rgb(164, 139, 214);
    }

    private int skinColor() {
        return Color.rgb(255, 213, 179);
    }

    private void drawHalo(Canvas canvas, float cx, float cy, float size, float t) {
        int accent = accentColor();
        float pulse = 1f + (float) Math.sin(t * 2.8f) * 0.025f;
        float radius = size * (state == AvatarState.URGENT_WAKEUP ? 0.49f : 0.43f) * pulse;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.mix(accent, Theme.WARM_WHITE, state == AvatarState.URGENT_WAKEUP ? 0.58f : 0.78f));
        paint.setAlpha(state == AvatarState.INTERRUPTED ? 120 : 165);
        canvas.drawCircle(cx, cy - size * 0.03f, radius, paint);
        paint.setAlpha(255);
    }

    private void drawBody(Canvas canvas, float cx, float cy, float size, float t) {
        float top = cy + size * 0.22f;
        float bodyW = size * 0.46f;
        float bodyH = size * 0.34f;
        rect.set(cx - bodyW / 2f, top, cx + bodyW / 2f, top + bodyH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(clothColor());
        canvas.drawRoundRect(rect, size * 0.14f, size * 0.14f, paint);
        paint.setColor(Theme.mix(clothColor(), Color.WHITE, 0.35f));
        canvas.drawCircle(cx, top + size * 0.08f, size * 0.045f, paint);
        paint.setStrokeWidth(size * 0.012f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Theme.mix(clothColor(), Color.BLACK, 0.18f));
        canvas.drawLine(cx, top + size * 0.02f, cx, top + bodyH * 0.72f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHead(Canvas canvas, float cx, float cy, float size) {
        float headW = size * 0.46f;
        float headH = size * 0.50f;
        rect.set(cx - headW / 2f, cy - size * 0.28f, cx + headW / 2f, cy - size * 0.28f + headH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(skinColor());
        canvas.drawOval(rect, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.008f);
        paint.setColor(Color.argb(50, 120, 70, 35));
        canvas.drawOval(rect, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawEars(Canvas canvas, float cx, float cy, float size) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(skinColor());
        canvas.drawCircle(cx - size * 0.25f, cy - size * 0.05f, size * 0.052f, paint);
        canvas.drawCircle(cx + size * 0.25f, cy - size * 0.05f, size * 0.052f, paint);
        paint.setColor(Color.rgb(241, 175, 151));
        canvas.drawCircle(cx - size * 0.252f, cy - size * 0.05f, size * 0.026f, paint);
        canvas.drawCircle(cx + size * 0.252f, cy - size * 0.05f, size * 0.026f, paint);
    }

    private void drawHair(Canvas canvas, float cx, float cy, float size) {
        int hair = hairColor();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(hair);
        rect.set(cx - size * 0.235f, cy - size * 0.30f, cx + size * 0.235f, cy + size * 0.00f);
        canvas.drawArc(rect, 185f, 170f, false, paint);
        path.reset();
        path.moveTo(cx - size * 0.21f, cy - size * 0.16f);
        path.quadTo(cx - size * 0.08f, cy - size * 0.34f, cx + size * 0.08f, cy - size * 0.17f);
        path.quadTo(cx + size * 0.16f, cy - size * 0.27f, cx + size * 0.22f, cy - size * 0.09f);
        path.lineTo(cx + size * 0.18f, cy - size * 0.02f);
        path.quadTo(cx, cy - size * 0.12f, cx - size * 0.20f, cy - size * 0.02f);
        path.close();
        canvas.drawPath(path, paint);
        if (CompanionAssistant.ROLE_SISTER.equals(role)) {
            paint.setColor(Color.rgb(250, 111, 126));
            rect.set(cx + size * 0.10f, cy - size * 0.34f, cx + size * 0.23f, cy - size * 0.24f);
            canvas.drawOval(rect, paint);
            rect.set(cx + size * 0.20f, cy - size * 0.34f, cx + size * 0.32f, cy - size * 0.24f);
            canvas.drawOval(rect, paint);
        }
    }

    private void drawFace(Canvas canvas, float cx, float cy, float size, float t) {
        float eyeY = cy - size * 0.06f + lookY * size * 0.014f;
        float leftEyeX = cx - size * 0.085f + lookX * size * 0.012f;
        float rightEyeX = cx + size * 0.085f + lookX * size * 0.012f;
        drawBrows(canvas, cx, eyeY, size);
        drawEyes(canvas, leftEyeX, rightEyeX, eyeY, size);
        drawCheeks(canvas, cx, cy, size);
        drawMouth(canvas, cx, cy, size);
    }

    private void drawBrows(Canvas canvas, float cx, float eyeY, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(size * 0.014f);
        paint.setColor(hairColor());
        float slope = 0f;
        if (state == AvatarState.WORRIED || state == AvatarState.URGENT_WAKEUP) slope = size * 0.024f;
        if (state == AvatarState.HAPPY || state == AvatarState.COMFORTING) slope = -size * 0.012f;
        canvas.drawLine(cx - size * 0.14f, eyeY - size * 0.065f + slope, cx - size * 0.045f, eyeY - size * 0.075f - slope, paint);
        canvas.drawLine(cx + size * 0.045f, eyeY - size * 0.075f - slope, cx + size * 0.14f, eyeY - size * 0.065f + slope, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawEyes(Canvas canvas, float leftX, float rightX, float y, float size) {
        boolean closed = blinkLevel > 0.34f || state == AvatarState.COMFORTING && blinkLevel > 0.18f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.016f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(52, 44, 38));
        if (closed) {
            canvas.drawLine(leftX - size * 0.035f, y, leftX + size * 0.035f, y + size * 0.006f, paint);
            canvas.drawLine(rightX - size * 0.035f, y + size * 0.006f, rightX + size * 0.035f, y, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawOval(oval(leftX, y, size * 0.049f, size * 0.062f), paint);
        canvas.drawOval(oval(rightX, y, size * 0.049f, size * 0.062f), paint);
        paint.setColor(Color.rgb(58, 49, 42));
        float pupilX = lookX * size * 0.012f;
        float pupilY = lookY * size * 0.010f;
        canvas.drawCircle(leftX + pupilX, y + pupilY, size * 0.025f, paint);
        canvas.drawCircle(rightX + pupilX, y + pupilY, size * 0.025f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(leftX + pupilX - size * 0.008f, y + pupilY - size * 0.010f, size * 0.006f, paint);
        canvas.drawCircle(rightX + pupilX - size * 0.008f, y + pupilY - size * 0.010f, size * 0.006f, paint);
    }

    private RectF oval(float cx, float cy, float rx, float ry) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        return rect;
    }

    private void drawCheeks(Canvas canvas, float cx, float cy, float size) {
        int alpha = state == AvatarState.URGENT_WAKEUP ? 210 : 120;
        if (state == AvatarState.WORRIED) alpha = 150;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, 255, 126, 126));
        canvas.drawOval(oval(cx - size * 0.145f, cy + size * 0.045f, size * 0.048f, size * 0.024f), paint);
        canvas.drawOval(oval(cx + size * 0.145f, cy + size * 0.045f, size * 0.048f, size * 0.024f), paint);
    }

    private void drawMouth(Canvas canvas, float cx, float cy, float size) {
        float y = cy + size * 0.105f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (state == AvatarState.SPEAKING || state == AvatarState.URGENT_WAKEUP || mouthLevel > 0.08f) {
            float open = size * (0.018f + mouthLevel * 0.052f);
            float width = size * (0.065f + mouthLevel * 0.026f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(82, 38, 38));
            canvas.drawOval(oval(cx, y, width, open), paint);
            paint.setColor(Color.rgb(244, 116, 116));
            canvas.drawOval(oval(cx, y + open * 0.32f, width * 0.52f, open * 0.24f), paint);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.015f);
        paint.setColor(Color.rgb(95, 51, 43));
        if (state == AvatarState.WORRIED || state == AvatarState.INTERRUPTED) {
            rect.set(cx - size * 0.055f, y - size * 0.015f, cx + size * 0.055f, y + size * 0.060f);
            canvas.drawArc(rect, 205f, 130f, false, paint);
        } else if (state == AvatarState.THINKING || state == AvatarState.SEEING || state == AvatarState.READING || state == AvatarState.FINDING) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawOval(oval(cx, y, size * 0.032f, size * 0.020f), paint);
        } else {
            rect.set(cx - size * 0.075f, y - size * 0.055f, cx + size * 0.075f, y + size * 0.045f);
            canvas.drawArc(rect, 25f, 130f, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHands(Canvas canvas, float cx, float cy, float size, float t) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(skinColor());
        float leftArm = waveLevel > 0.05f ? (float) Math.sin(t * 10f) * size * 0.028f : 0f;
        if (state == AvatarState.SEEING || state == AvatarState.READING || state == AvatarState.FINDING) {
            paint.setColor(Theme.mix(skinColor(), Color.WHITE, 0.10f));
            canvas.drawCircle(cx + size * 0.23f, cy + size * 0.20f, size * 0.045f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.018f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(skinColor());
            canvas.drawLine(cx + size * 0.18f, cy + size * 0.24f, cx + size * 0.25f, cy + size * 0.11f, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        canvas.drawCircle(cx - size * 0.26f, cy + size * 0.18f - leftArm, size * 0.046f, paint);
        canvas.drawCircle(cx + size * 0.26f, cy + size * 0.19f, size * 0.044f, paint);
        if (state == AvatarState.URGENT_WAKEUP) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.018f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Theme.RED);
            canvas.drawLine(cx - size * 0.31f, cy + size * 0.11f, cx - size * 0.22f, cy + size * 0.04f, paint);
            canvas.drawLine(cx + size * 0.22f, cy + size * 0.04f, cx + size * 0.31f, cy + size * 0.11f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
