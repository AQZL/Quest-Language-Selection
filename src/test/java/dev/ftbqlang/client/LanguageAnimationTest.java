package dev.ftbqlang.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageAnimationTest {
    private static final long OPENED_AT = 10_000_000_000L;

    @Test
    void wordsKeepMovingAfterEntranceAndAfterAFullMinute() {
        LanguageAnimation animation = new LanguageAnimation(OPENED_AT);

        for (double seconds : new double[]{1.0, 60.0}) {
            assertEquals(1.0, animation.entrance(at(seconds)));
            for (int index = 0; index < LanguageAnimation.WORD_COUNT; index++) {
                var before = animation.word(index, 960, 540, 60, 10, at(seconds));
                var after = animation.word(index, 960, 540, 60, 10, at(seconds + 0.25));

                assertTrue(Math.hypot(after.x() - before.x(), after.y() - before.y()) > 0.01,
                        "Word " + index + " stopped floating at " + seconds + " seconds");
            }
        }
    }

    @Test
    void wordsCoverTheWholeViewportAtDifferentGuiSizes() {
        for (int[] size : new int[][]{{320, 180}, {854, 480}, {1920, 1080}, {480, 854}}) {
            LanguageAnimation animation = new LanguageAnimation(OPENED_AT);
            double wordWidth = Math.min(70, size[0] / 8.0);
            double wordHeight = 10;
            for (double seconds : new double[]{1, 17, 60}) {
                boolean upper = false, lower = false, left = false, right = false, center = false;
                for (int index = 0; index < LanguageAnimation.WORD_COUNT; index++) {
                    var pose = animation.word(index, size[0], size[1], wordWidth, wordHeight, at(seconds));
                    double x = (pose.x() + wordWidth / 2) / size[0];
                    double y = (pose.y() + wordHeight / 2) / size[1];
                    upper |= y < 0.25;
                    lower |= y > 0.75;
                    left |= x < 0.20;
                    right |= x > 0.80;
                    center |= x > 0.25 && x < 0.75 && y > 0.25 && y < 0.75;
                    assertTrue(pose.x() >= 4 && pose.x() + wordWidth <= size[0] - 4);
                    assertTrue(pose.y() >= 4 && pose.y() + wordHeight <= size[1] - 4);
                }
                assertTrue(upper && lower && left && right && center,
                        "Words must span all viewport regions at " + size[0] + "x" + size[1]);
            }
        }
    }

    @Test
    void closingStartsAtTheExactCurrentFloatingPose() {
        LanguageAnimation animation = new LanguageAnimation(OPENED_AT);
        long closingAt = at(7.125);
        LanguageAnimation.WordPose[] before = new LanguageAnimation.WordPose[LanguageAnimation.WORD_COUNT];
        for (int index = 0; index < before.length; index++) {
            before[index] = animation.word(index, 854, 480, 60, 10, closingAt);
        }

        animation.beginClose(closingAt);

        assertTrue(animation.isClosing());
        for (int index = 0; index < before.length; index++) {
            assertEquals(before[index], animation.word(index, 854, 480, 60, 10, closingAt));
        }
        assertEquals(1.0, animation.panelScaleY(closingAt));
    }

    @Test
    void repeatedCloseRequestsDoNotRestartTheAnimation() {
        LanguageAnimation once = new LanguageAnimation(OPENED_AT);
        LanguageAnimation repeated = new LanguageAnimation(OPENED_AT);
        once.beginClose(at(5));
        repeated.beginClose(at(5));
        repeated.beginClose(at(5.1));
        repeated.beginClose(at(5.2));

        assertEquals(once.panelScaleY(at(5.12)), repeated.panelScaleY(at(5.12)));
        assertEquals(once.lineWidth(at(5.18)), repeated.lineWidth(at(5.18)));
        for (int index = 0; index < LanguageAnimation.WORD_COUNT; index++) {
            assertEquals(once.word(index, 960, 540, 60, 10, at(5.25)),
                    repeated.word(index, 960, 540, 60, 10, at(5.25)));
        }
        assertTrue(repeated.isFinished(at(5 + LanguageAnimation.CLOSE_SECONDS)));
    }

    @Test
    void fallingWordsAccelerateAndLeaveTheViewportBeforeClosure() {
        for (int[] size : new int[][]{{320, 180}, {854, 480}, {1920, 1080}, {480, 854}}) {
            LanguageAnimation animation = new LanguageAnimation(OPENED_AT);
            animation.beginClose(at(5));
            for (int index = 0; index < LanguageAnimation.WORD_COUNT; index++) {
                double y0 = animation.word(index, size[0], size[1], 60, 10, at(5)).y();
                double y1 = animation.word(index, size[0], size[1], 60, 10, at(5.08)).y();
                double y2 = animation.word(index, size[0], size[1], 60, 10, at(5.16)).y();
                double finalY = animation.word(index, size[0], size[1], 60, 10,
                        at(5 + LanguageAnimation.CLOSE_SECONDS)).y();

                assertTrue(y1 > y0, "Every word immediately falls downward");
                assertTrue(y2 - y1 > y1 - y0, "Gravity must increase downward speed");
                assertTrue(finalY > size[1], "Every word must leave the screen before the modal disappears");
            }
        }
    }

    @Test
    void crtCloseHasShortSeparateCollapseLineAndFallPhases() {
        LanguageAnimation animation = new LanguageAnimation(OPENED_AT);
        assertFalse(animation.isFinished(at(60)));
        animation.beginClose(at(5));

        assertTrue(animation.panelScaleY(at(5.08)) < 0.2);
        assertEquals(0.0, animation.panelScaleY(at(5.16)), 1.0e-12);
        assertTrue(animation.lineWidth(at(5.16)) > 0);
        assertEquals(0.0, animation.lineWidth(at(5.22)), 1.0e-12);
        assertFalse(animation.isFinished(at(5.319)));
        assertTrue(animation.isFinished(at(5.32)));
    }

    @Test
    void backdropFadesContinuouslyAndIsTransparentBeforeModalRemoval() {
        LanguageAnimation animation = new LanguageAnimation(OPENED_AT);
        assertEquals(0.0, animation.backdropOpacity(at(0)));
        assertEquals(1.0, animation.backdropOpacity(at(1)));
        animation.beginClose(at(5));
        assertEquals(1.0, animation.backdropOpacity(at(5)));

        double previous = 1.0;
        for (int frame = 1; frame <= 40; frame++) {
            double opacity = animation.backdropOpacity(at(5 + frame * 0.01));
            assertTrue(opacity >= 0.0 && opacity <= previous,
                    "The backdrop must fade out without a brightness flash");
            previous = opacity;
        }
        assertEquals(0.0, animation.backdropOpacity(at(5 + LanguageAnimation.CLOSE_SECONDS)));
    }

    @Test
    void closingDuringEntranceDoesNotFlashTheBackdropToFullOpacity() {
        LanguageAnimation animation = new LanguageAnimation(OPENED_AT);
        double before = animation.backdropOpacity(at(0.05));
        animation.beginClose(at(0.05));
        assertEquals(before, animation.backdropOpacity(at(0.05)));
        assertTrue(animation.backdropOpacity(at(0.06)) < before);
    }

    @Test
    void animationSamplingIsIndependentOfFrameRate() {
        LanguageAnimation reference = new LanguageAnimation(OPENED_AT);
        reference.beginClose(at(5));
        long sampledAt = at(5.275);

        for (int fps : new int[]{30, 60, 144}) {
            LanguageAnimation sampled = new LanguageAnimation(OPENED_AT);
            for (int frame = 0; frame < 5 * fps; frame++) {
                sampleAllWords(sampled, at(frame / (double) fps));
            }
            sampled.beginClose(at(5));
            for (int frame = 0; frame < 0.275 * fps; frame++) {
                sampleAllWords(sampled, at(5 + frame / (double) fps));
            }

            for (int index = 0; index < LanguageAnimation.WORD_COUNT; index++) {
                assertEquals(reference.word(index, 960, 540, 60, 10, sampledAt),
                        sampled.word(index, 960, 540, 60, 10, sampledAt),
                        "Sampling at " + fps + " FPS must preserve trajectories");
            }
            assertEquals(reference.panelScaleY(sampledAt), sampled.panelScaleY(sampledAt));
            assertEquals(reference.lineWidth(sampledAt), sampled.lineWidth(sampledAt));
        }
    }

    private static void sampleAllWords(LanguageAnimation animation, long now) {
        for (int index = 0; index < LanguageAnimation.WORD_COUNT; index++) {
            animation.word(index, 960, 540, 60, 10, now);
        }
        animation.panelScaleY(now);
        animation.lineWidth(now);
    }

    private static long at(double seconds) {
        return OPENED_AT + Math.round(seconds * 1_000_000_000.0);
    }
}
