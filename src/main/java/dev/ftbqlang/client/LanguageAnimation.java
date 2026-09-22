package dev.ftbqlang.client;

/** Frame-rate-independent animation math. Times use the same monotonic nanosecond clock. */
public final class LanguageAnimation {
    public static final int WORD_COLUMNS = 6;
    public static final int WORD_ROWS = 4;
    public static final int WORD_COUNT = WORD_COLUMNS * WORD_ROWS;
    public static final double ENTER_SECONDS = 0.28;
    public static final double COLLAPSE_SECONDS = 0.16;
    public static final double LINE_END_SECONDS = 0.22;
    public static final double CLOSE_SECONDS = 0.32;

    private final long openedAt;
    private long closingAt;
    private boolean closing;

    public LanguageAnimation(long openedAt) {
        this.openedAt = openedAt;
    }

    public void beginClose(long now) {
        if (!closing) {
            closing = true;
            closingAt = now;
        }
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isFinished(long now) {
        return closing && seconds(now, closingAt) >= CLOSE_SECONDS;
    }

    public double entrance(long now) {
        return easeOut(seconds(closing ? closingAt : now, openedAt) / ENTER_SECONDS);
    }

    public double panelScaleY(long now) {
        return closing ? Math.pow(1.0 - clamp(seconds(now, closingAt) / COLLAPSE_SECONDS), 3) : 1.0;
    }

    public double backdropOpacity(long now) {
        return closing ? entrance(now) * (1.0 - easeOut(seconds(now, closingAt) / CLOSE_SECONDS))
                : entrance(now);
    }

    public double lineWidth(long now) {
        if (!closing || seconds(now, closingAt) < COLLAPSE_SECONDS) {
            return 0.0;
        }
        return 1.0 - easeOut((seconds(now, closingAt) - COLLAPSE_SECONDS)
                / (LINE_END_SECONDS - COLLAPSE_SECONDS));
    }

    public WordPose word(int index, int screenWidth, int screenHeight,
                         double wordWidth, double wordHeight, long now) {
        // The grid fills the whole viewport, including the area behind the opaque picker.
        double age = seconds(closing ? closingAt : now, openedAt);
        double cellWidth = screenWidth / (double) WORD_COLUMNS;
        double cellHeight = screenHeight / (double) WORD_ROWS;
        double phase = index * 2.399963;
        double speed = 0.72 + (index % 5) * 0.11;
        double ampX = Math.min(22.0, cellWidth * 0.19);
        double ampY = Math.min(18.0, cellHeight * 0.19);
        double centerX = (index % WORD_COLUMNS + 0.5 + 0.09 * Math.sin(phase)) * cellWidth;
        double centerY = (index / WORD_COLUMNS + 0.5 + 0.10 * Math.cos(phase)) * cellHeight;
        double x = centerX - wordWidth / 2.0 + Math.sin(age * speed + phase) * ampX;
        double y = centerY - wordHeight / 2.0 + Math.cos(age * speed * 0.87 + phase) * ampY;
        x = Math.clamp(x, 4.0, Math.max(4.0, screenWidth - wordWidth - 4.0));
        y = Math.clamp(y, 4.0, Math.max(4.0, screenHeight - wordHeight - 4.0));
        double angle = 4.0 * Math.sin(age * 0.45 + phase);
        double alpha = entrance(now) * (0.68 + 0.12 * Math.sin(age * 0.6 + phase));

        if (closing) {
            double fallTime = seconds(now, closingAt);
            // Capture each word's exact floating position, then give it downward momentum.
            // Analytic integration keeps the same trajectory at 30, 60 or 144 FPS.
            double velocityX = ampX * speed * Math.cos(age * speed + phase)
                    + (index % 3 - 1) * screenWidth * 0.045;
            double velocityY = screenHeight * (1.1 + (index % 5) * 0.08);
            double gravity = screenHeight * (20.0 + index % 4);
            x += velocityX * fallTime;
            y += velocityY * fallTime + 0.5 * gravity * fallTime * fallTime;
            angle += (index % 2 == 0 ? 1.0 : -1.0) * (55 + index % 7 * 9) * fallTime;
        }
        return new WordPose(x, y, angle, alpha);
    }

    private static double seconds(long now, long start) {
        return Math.max(0.0, (now - start) / 1_000_000_000.0);
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    private static double easeOut(double progress) {
        return 1.0 - Math.pow(1.0 - clamp(progress), 3);
    }

    public record WordPose(double x, double y, double rotation, double alpha) {
    }
}
