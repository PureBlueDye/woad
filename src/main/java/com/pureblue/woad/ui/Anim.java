package com.pureblue.woad.ui;

/**
 * Time-based animation: easing curves, and small value holders that move toward a target.
 *
 * <p>Everything here advances by elapsed real time, read from {@link System#nanoTime()}, so an
 * animation lasts the same at 30 frames per second as at 240. Nothing counts frames.
 */
public final class Anim {

    private Anim() {}

    /** Milliseconds from a monotonic clock, with sub-millisecond precision. */
    public static double nowMs() {
        return System.nanoTime() / 1_000_000.0;
    }

    // ---- Easing --------------------------------------------------------------------------------

    /** Maps linear progress 0..1 to eased progress. */
    @FunctionalInterface
    public interface Ease {
        float apply(float t);

        Ease LINEAR = t -> t;
        Ease OUT_CUBIC = t -> 1f - cube(1f - t);
        Ease OUT_QUINT = t -> 1f - (float) Math.pow(1f - t, 5);
        Ease IN_OUT_CUBIC = t -> t < 0.5f ? 4f * t * t * t : 1f - cube(-2f * t + 2f) / 2f;
        /** Overshoots slightly past 1 before settling: the "spring" of a switch knob. */
        Ease OUT_BACK = t -> {
            float c1 = 1.25f;
            float c3 = c1 + 1f;
            float u = t - 1f;
            return 1f + c3 * u * u * u + c1 * u * u;
        };
    }

    private static float cube(float v) {
        return v * v * v;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---- Holders -------------------------------------------------------------------------------

    /**
     * A 0..1 progress that runs toward "on" or "off" over a fixed duration — hover, focus, a
     * switch knob.
     *
     * <p>It advances linearly and is eased on the way out, so reversing half-way through picks up
     * from where it is instead of jumping. Going off uses the mirrored curve: an overshooting ease
     * would otherwise overshoot backwards the moment it starts to leave.
     */
    public static final class Toggle {
        private final float durationMs;
        private final Ease ease;
        private float linear;
        private boolean on;
        private double last = -1;

        public Toggle(int durationMs, Ease ease) {
            this.durationMs = Math.max(1, durationMs);
            this.ease = ease;
        }

        /** Jumps straight to a state, for the first frame a widget is shown. */
        public Toggle snap(boolean state) {
            on = state;
            linear = state ? 1f : 0f;
            last = nowMs();
            return this;
        }

        /** Advances toward {@code target} and returns the eased progress. */
        public float update(boolean target) {
            double now = nowMs();
            if (last < 0) {
                snap(target);
                return linear;
            }
            float step = (float) ((now - last) / durationMs);
            last = now;
            on = target;
            linear = clamp01(target ? linear + step : linear - step);
            return value();
        }

        /** The eased progress, without advancing. */
        public float value() {
            return on ? ease.apply(linear) : 1f - ease.apply(1f - linear);
        }
    }

    /**
     * A number that glides toward a target along an eased curve over a fixed duration — the
     * sliding pill of a tab strip, a fill that grows.
     */
    public static final class Tween {
        private final float durationMs;
        private final Ease ease;
        private float from;
        private float to;
        private double start = -1;

        public Tween(int durationMs, Ease ease) {
            this.durationMs = Math.max(1, durationMs);
            this.ease = ease;
        }

        /** Retargets, starting from wherever the value is right now. */
        public void set(float target) {
            if (start < 0) {
                from = to = target;
                start = nowMs() - durationMs;
                return;
            }
            if (target == to) return;
            from = get();
            to = target;
            start = nowMs();
        }

        public float get() {
            if (start < 0) return to;
            float t = clamp01((float) ((nowMs() - start) / durationMs));
            return lerp(from, to, ease.apply(t));
        }
    }

    /**
     * Exponential smoothing toward a moving target, used for scrolling. The catch-up is defined
     * by a half-life, which makes it independent of the frame rate: the same wheel flick settles
     * in the same time whether frames come every 4 ms or every 30 ms.
     */
    public static final class Smooth {
        private final double halfLifeMs;
        private float value;
        private float target;
        private double last = -1;

        public Smooth(int halfLifeMs) {
            this.halfLifeMs = Math.max(1, halfLifeMs);
        }

        public void setTarget(float target) {
            this.target = target;
        }

        public float target() {
            return target;
        }

        /** Moves both the value and the target, with no animation (e.g. dragging a scrollbar). */
        public void jump(float to) {
            value = target = to;
        }

        public float update() {
            double now = nowMs();
            if (last < 0) last = now;
            double dt = Math.min(100, now - last);
            last = now;
            float k = (float) (1 - Math.pow(0.5, dt / halfLifeMs));
            value += (target - value) * k;
            if (Math.abs(target - value) < 0.05f) value = target;
            return value;
        }
    }

    /** A short dip and recovery after an event — the press of a button. */
    public static final class Pulse {
        private final float downMs;
        private final float upMs;
        private double fired = -1e9;

        public Pulse(int downMs, int upMs) {
            this.downMs = Math.max(1, downMs);
            this.upMs = Math.max(1, upMs);
        }

        public void fire() {
            fired = nowMs();
        }

        /** 0 at rest, 1 at the bottom of the press. */
        public float value() {
            float t = (float) (nowMs() - fired);
            if (t < 0 || t > downMs + upMs) return 0f;
            if (t < downMs) return Ease.OUT_CUBIC.apply(t / downMs);
            return 1f - Ease.OUT_CUBIC.apply((t - downMs) / upMs);
        }
    }
}
