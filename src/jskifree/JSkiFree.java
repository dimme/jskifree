/* Start-up, the event loop and the status bar.
 *
 * ski32.exe drove itself from a WM_TIMER firing every 40 ms plus an idle
 * hook, and painted a separate status window along the top. Here a Swing
 * timer on the event thread does the same job, so every piece of game state
 * is touched from one thread, and the status line is drawn straight onto
 * the back buffer.
 */
package jskifree;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import static jskifree.Consts.*;

public final class JSkiFree {
    private JSkiFree() {}

    private static final Game g = Game.g;

    /* Text taken from the original's string table. */
    private static final String TITLE_PLAYING = "JSkiFree";
    private static final String TITLE_PAUSED  = "JSkiFree Paused ... Press F3 to continue";

    private static JFrame frame;
    private static Graphics.Canvas canvas;
    private static Timer timer;
    private static long nextFrameDue;

    private static long millisecondsNow() {
        return System.currentTimeMillis();
    }

    /* ---------------------------------------------------------------- */
    /* Status bar                                                        */
    /* ---------------------------------------------------------------- */

    private static final String[] LABELS = { "Time:", "Dist:", "Speed:", "Style:" };
    private static final String[] TEMPLATES = { "00:00:00.00", " 0000m", " 0000m/s", "0000000" };
    private static final String[] values = { "", "", "", "" };
    private static long lastRefreshMs;
    private static boolean refreshed;

    /** The status box. ski32.exe gave this its own child window in the top
     *  right corner, sized from the text: a label column as wide as the
     *  widest label, a value column as wide as the widest template, plus
     *  four pixels, and four rows of the font's height plus four. */
    private static void drawStatusBar() {
        long milliseconds = g.courseTimeMs;
        int speed = 0;
        int labelWidth = 0, valueWidth = 0;
        int fontHeight = Graphics.fontHeight();
        int ascent = Graphics.fontAscent();

        for (int i = 0; i < 4; i++) {
            labelWidth = Math.max(labelWidth, Graphics.textWidthFont(LABELS[i], true));
            valueWidth = Math.max(valueWidth, Graphics.textWidth(TEMPLATES[i]));
        }
        int boxWidth = labelWidth + valueWidth + 4;
        int boxHeight = 4 * fontHeight + 4;
        int boxX = Graphics.windowWidth - boxWidth;

        /* GameFrame only redrew the readout once 0x148 ms had passed since
         * the last time, so the figures tick over about three times a second. */
        if (!refreshed || g.nowMs - lastRefreshMs >= STATUS_INTERVAL_MS) {
            refreshed = true;
            lastRefreshMs = g.nowMs;

            if (g.player != null && g.frameMs > 0)
                speed = (int) (g.player.velocityY * 1000L / (g.frameMs * UNITS_PER_METRE));

            /* Format strings 11..14 of the original's string table. */
            values[0] = String.format("%2d:%02d:%02d.%02d",
                                      milliseconds / 3600000L,
                                      (milliseconds / 60000L) % 60L,
                                      (milliseconds / 1000L) % 60L,
                                      (milliseconds % 1000L) / 10L);
            values[1] = String.format("%5sm", twoDigits(Course.distanceMetres()));
            values[2] = String.format("%5sm/s", twoDigits(speed));
            values[3] = String.format("%7d", g.stylePoints);
        }

        Graphics.fillFrame(boxX, 0, boxWidth, boxHeight);
        for (int i = 0; i < 4; i++) {
            int baseline = 2 + i * fontHeight + ascent;
            Graphics.drawTextFont(boxX + 2, baseline, LABELS[i], true);
            Graphics.drawText(boxX + labelWidth + 2, baseline, values[i]);
        }
    }

    /** C's "%.2d": at least two digits, zero padded, sign preserved. */
    private static String twoDigits(int value) {
        String digits = String.format("%02d", Math.abs(value));
        return value < 0 ? "-" + digits : digits;
    }

    /* ---------------------------------------------------------------- */
    /* Frame                                                             */
    /* ---------------------------------------------------------------- */

    /* The original popped its scoreboard up as a modal MessageBox captioned
     * "High Scores". This draws the same thing over the hill: a grey panel
     * in the middle of the window with the lines centred, a caption strip,
     * and an OK button, kept until the player presses something. */
    private static final int DIALOG_PADDING = 12;
    private static final int BUTTON_WIDTH   = 52;
    private static final int BUTTON_HEIGHT  = 22;
    private static final int CAPTION_HEIGHT = 18;

    private static void drawHighScores() {
        final String caption = "High Scores";
        String report = HighScore.report();
        if (report == null)
            return;

        int fontHeight = Graphics.fontHeight();
        int ascent = Graphics.fontAscent();
        String[] lines = report.split("\n", -1);

        int widest = 0;
        for (String line : lines)
            widest = Math.max(widest, Graphics.textWidth(line));
        if (widest < BUTTON_WIDTH + 2 * DIALOG_PADDING)
            widest = BUTTON_WIDTH + 2 * DIALOG_PADDING;

        int width = widest + 2 * DIALOG_PADDING;
        int height = CAPTION_HEIGHT + DIALOG_PADDING + lines.length * fontHeight +
                     DIALOG_PADDING + BUTTON_HEIGHT + DIALOG_PADDING;
        int left = (Graphics.windowWidth - width) / 2;
        int top = (Graphics.windowHeight - height) / 2;

        /* Panel, caption strip, and the caption text. */
        Graphics.fillGrey(left, top, width, height);
        Graphics.fillFrame(left, top, width, CAPTION_HEIGHT);
        Graphics.drawTextFont(left + (width - Graphics.textWidthFont(caption, true)) / 2,
                              top + (CAPTION_HEIGHT - fontHeight) / 2 + ascent, caption, true);

        /* Body text, each line centred as MessageBox centres it. */
        int y = top + CAPTION_HEIGHT + DIALOG_PADDING + ascent;
        for (String line : lines) {
            Graphics.drawText(left + (width - Graphics.textWidth(line)) / 2, y, line);
            y += fontHeight;
        }

        /* The OK button. */
        int bx = left + (width - BUTTON_WIDTH) / 2;
        int by = top + height - DIALOG_PADDING - BUTTON_HEIGHT;
        Graphics.fillFrame(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT);
        Graphics.fillGrey(bx + 1, by + 1, BUTTON_WIDTH - 2, BUTTON_HEIGHT - 2);
        Graphics.drawText(bx + (BUTTON_WIDTH - Graphics.textWidth("OK")) / 2,
                          by + (BUTTON_HEIGHT - fontHeight) / 2 + ascent, "OK");
    }

    private static void drawFrame() {
        Graphics.beginFrame();
        World.draw();
        drawStatusBar();
        drawHighScores();
        Graphics.endFrame(canvas);
    }

    /** UpdateRunState in the original: the timer runs only while the window
     *  is active and the player has not paused. */
    private static void updateRunState() {
        g.running = g.focused && !g.paused;
    }

    private static void restartGame() {
        World.reset();
        g.paused = false;                  /* RestartGame un-paused as well */
        updateRunState();
        frame.setTitle(TITLE_PLAYING);
    }

    private static void togglePause() {
        g.paused = !g.paused;
        updateRunState();
        frame.setTitle(g.paused ? TITLE_PAUSED : TITLE_PLAYING);
    }

    /* ---------------------------------------------------------------- */
    /* Events                                                            */
    /* ---------------------------------------------------------------- */

    private static void handleKeyPress(KeyEvent event) {
        int key = event.getKeyCode();
        char character = event.getKeyChar();
        if (character == KeyEvent.CHAR_UNDEFINED)
            character = 0;

        /* Any key puts the scoreboard away, and is swallowed doing it, just
         * as the original's message box swallowed the click that closed it. */
        if (HighScore.report() != null) {
            HighScore.dismissReport();
            return;
        }

        switch (key) {
        case KeyEvent.VK_F2:
            restartGame();
            return;
        case KeyEvent.VK_ENTER:
            /* Enter only restarts once the skier is gone, as in the original. */
            if (g.player == null)
                restartGame();
            return;
        case KeyEvent.VK_F3:
            togglePause();
            return;
        case KeyEvent.VK_ESCAPE:
            /* The original minimised on Escape rather than quitting. */
            frame.setExtendedState(Frame.ICONIFIED);
            return;
        case KeyEvent.VK_Q:
            if (character == 'q') {
                g.quit = true;
                shutdown();
                return;
            }
            break;
        default:
            break;
        }

        if (!g.running)
            return;

        /* The two debugging characters from the original's WM_CHAR handler:
         * 'r' repainted the hill and 't' ran one extra game frame. */
        if (character == 'r') {
            drawFrame();
            return;
        }
        if (character == 't') {
            g.previousMs = g.nowMs;
            g.nowMs = millisecondsNow();
            g.frameMs = g.nowMs - g.previousMs;
            if (g.frameMs == 0)
                g.frameMs = FRAME_INTERVAL_MS;
            World.update();
            drawFrame();
            return;
        }

        Skier.hiddenKey(character);

        /* A key that changes the skier's pose repainted at once in the
         * original rather than waiting for the next timer tick. */
        int before = g.player != null ? g.player.state : -1;
        Skier.keyDown(key);
        if (g.player != null && g.player.state != before)
            drawFrame();
    }

    private static void handlePointerMoved(MouseEvent event) {
        /* The pointer steers relative to where the skier is drawn, and a
         * change of pose is painted straight away. */
        if (g.running && HighScore.report() == null) {
            int before = g.player != null ? g.player.state : -1;
            Skier.pointerMoved(event.getX() - Graphics.windowWidth / 2,
                               event.getY() - Graphics.windowHeight / 3);
            if (g.player != null && g.player.state != before)
                drawFrame();
        }
    }

    private static void handleButtonPress() {
        if (HighScore.report() != null)
            HighScore.dismissReport();
        else if (g.player == null)
            restartGame();
        else if (g.running)
            Skier.pointerClicked();
    }

    private static void handleResized() {
        Graphics.resized(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()));
        World.updateViewport();
    }

    /* ---------------------------------------------------------------- */
    /* Frame timing                                                      */
    /* ---------------------------------------------------------------- */

    /** One tick of the 40 ms timer: run a frame if it is due, then arm the
     *  timer for the next one, resynchronising if we have fallen behind. */
    private static void tick() {
        if (g.quit)
            return;

        long now = millisecondsNow();
        if (now >= nextFrameDue) {
            g.previousMs = g.nowMs;
            g.nowMs = now;
            g.frameMs = now - g.previousMs;
            if (g.frameMs == 0)
                g.frameMs = FRAME_INTERVAL_MS;

            /* Frozen while the scoreboard is up, as under a modal box. */
            if (g.running && HighScore.report() == null)
                World.update();

            drawFrame();

            nextFrameDue += FRAME_INTERVAL_MS;
            if (nextFrameDue < now)             /* fell behind; resynchronise */
                nextFrameDue = now + FRAME_INTERVAL_MS;
        }

        long delay = nextFrameDue - millisecondsNow();
        timer.setInitialDelay((int) Math.max(1, delay));
        timer.restart();
    }

    private static void shutdown() {
        if (timer != null)
            timer.stop();
        if (frame != null)
            frame.dispose();
        System.exit(0);
    }

    /* ---------------------------------------------------------------- */
    /* Entry point                                                       */
    /* ---------------------------------------------------------------- */

    private static void printHelp() {
        System.out.print(
            "JSkiFree, a re-implementation of ski32.exe\n\n" +
            "Usage: java -jar skifree.jar [sound|nosound] [--help]\n\n" +
            "  sound      enable sound: the yeti's cry as it chases you\n" +
            "             and the noise it makes eating you\n" +
            "  nosound    silent (the default)\n" +
            "  --help     this text\n\n" +
            "Keys:\n" +
            "  arrow keys / numpad 1-9   steer\n" +
            "  numpad 2 or Down          point downhill\n" +
            "  numpad 4/6 or Left/Right  turn; repeat to skate\n" +
            "  numpad 8 or Up            climb, or change trick\n" +
            "  numpad 0, Insert, Space   jump\n" +
            "  F2                        restart\n" +
            "  F3                        pause\n" +
            "  Escape                    minimise\n" +
            "  q                         quit\n\n" +
            "High scores are kept in " + HighScore.path() + "\n");
    }

    public static void main(String[] args) {
        boolean wantSound = false;           /* silent unless asked for */

        for (String arg : args) {
            if (arg.equals("sound") || arg.equals("--sound")) {
                wantSound = true;
            } else if (arg.equals("nosound") || arg.equals("--nosound")) {
                wantSound = false;
            } else if (arg.equals("--help")) {
                printHelp();
                return;
            }
        }

        try {
            Sprites.load();
        } catch (IOException e) {
            System.err.println("skifree: cannot load sprites: " + e.getMessage());
            System.exit(1);
            return;
        }
        Sound.init(wantSound);

        final boolean soundOn = wantSound;
        SwingUtilities.invokeLater(() -> createAndRun(soundOn));
    }

    private static void createAndRun(boolean wantSound) {
        /* InitInstance sized the window to the full height of the screen
         * and made it as wide as it is tall, centred horizontally. */
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                               .getMaximumWindowBounds();
        int windowHeight = screen.height;
        int windowWidth = Math.min(screen.width, screen.height);

        frame = new JFrame(TITLE_PLAYING);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        try {
            frame.setIconImage(Icon.load());
        } catch (IOException e) {
            /* No icon, no matter. */
        }

        canvas = Graphics.open(windowWidth, windowHeight);
        frame.setContentPane(canvas);
        frame.pack();

        /* pack() sized the content to the screen; trim the decorations off
         * so the whole window fits, and pin the 320x300 minimum of the
         * original's WM_GETMINMAXINFO. */
        Insets insets = frame.getInsets();
        frame.setSize(windowWidth, windowHeight);
        frame.setMinimumSize(new Dimension(320 + insets.left + insets.right,
                                           300 + insets.top + insets.bottom));
        frame.setLocation(screen.x + (screen.width - windowWidth) / 2, screen.y);

        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { handleKeyPress(e); }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { handlePointerMoved(e); }
            @Override public void mouseDragged(MouseEvent e) { handlePointerMoved(e); }
        });
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handleButtonPress(); }
        });
        canvas.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { handleResized(); }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                g.quit = true;
                shutdown();
            }
        });
        frame.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            /* WM_ACTIVATE: the game only runs while its window is active,
             * and starts life waiting for that first activation. */
            @Override public void windowGainedFocus(WindowEvent e) {
                g.focused = true;
                updateRunState();
            }
            @Override public void windowLostFocus(WindowEvent e) {
                g.focused = false;
                updateRunState();
            }
        });

        g.rngState = (int) millisecondsNow();
        g.nowMs = millisecondsNow();
        g.previousMs = g.nowMs;
        g.focused = false;                 /* paused until the window activates */

        frame.setVisible(true);
        handleResized();
        restartGame();
        drawFrame();
        canvas.requestFocusInWindow();

        nextFrameDue = millisecondsNow();
        timer = new Timer(FRAME_INTERVAL_MS, e -> tick());
        timer.setRepeats(false);
        timer.setInitialDelay(1);
        timer.start();
    }
}
