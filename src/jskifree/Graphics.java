/* Java2D back end.
 *
 * The original talked to the Win32 GDI, and the mapping across is almost
 * one-for-one:
 *
 *     CreateCompatibleDC / CreateCompatibleBitmap  ->  a BufferedImage
 *     BitBlt(SRCCOPY)                              ->  drawImage
 *     the AND-mask + SRCPAINT sprite blit          ->  ARGB with alpha
 *     TextOutA                                     ->  drawString
 *
 * Everything is drawn into an off-screen back buffer and copied to the
 * window once per frame, which is what ski32.exe did as well.
 */
package jskifree;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public final class Graphics {
    private Graphics() {}

    public static int windowWidth = 640;
    public static int windowHeight = 480;

    private static BufferedImage backBuffer;
    private static Graphics2D gc;

    /* The original painted the status labels with the stock SYSTEM_FONT and
     * the values with DEFAULT_GUI_FONT, which is why the labels look bold
     * beside the figures. */
    private static final Font VALUE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static FontMetrics valueMetrics;
    private static FontMetrics labelMetrics;

    private static final Color SNOW = new Color(Sprites.WHITE_RGB);
    private static final Color GREY = new Color(Sprites.GREY_RGB);

    /** The component the back buffer is shown in; it just blits. */
    public static final class Canvas extends JComponent {
        private static final long serialVersionUID = 1L;

        Canvas() {
            setOpaque(true);
            setBackground(SNOW);
            setPreferredSize(new Dimension(windowWidth, windowHeight));
            setMinimumSize(new Dimension(320, 300));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            if (backBuffer != null)
                g.drawImage(backBuffer, 0, 0, null);
        }
    }

    public static Canvas open(int width, int height) {
        windowWidth = width;
        windowHeight = height;
        allocateBackBuffer();
        return new Canvas();
    }

    private static void allocateBackBuffer() {
        if (gc != null)
            gc.dispose();
        backBuffer = new BufferedImage(Math.max(1, windowWidth), Math.max(1, windowHeight),
                                       BufferedImage.TYPE_INT_RGB);
        gc = backBuffer.createGraphics();
        gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        valueMetrics = gc.getFontMetrics(VALUE_FONT);
        labelMetrics = gc.getFontMetrics(LABEL_FONT);
    }

    public static void resized(int width, int height) {
        if (width == windowWidth && height == windowHeight)
            return;
        windowWidth = width;
        windowHeight = height;
        allocateBackBuffer();
    }

    public static void beginFrame() {
        gc.setColor(SNOW);
        gc.fillRect(0, 0, windowWidth, windowHeight);
    }

    /** Present the finished frame: the canvas blits the back buffer. */
    public static void endFrame(Canvas canvas) {
        java.awt.Graphics g = canvas.getGraphics();
        if (g != null) {
            try {
                g.drawImage(backBuffer, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
    }

    public static void drawBitmap(int bitmap, int x, int y) {
        if (bitmap < 1 || bitmap > Consts.BITMAP_COUNT)
            return;
        Sprites.Bitmap sprite = Sprites.get(bitmap);
        if (sprite == null || sprite.image == null)
            return;

        /* Cheap reject; Java2D would clip anyway. */
        if (x + sprite.width < 0 || y + sprite.height < 0 ||
            x >= windowWidth || y >= windowHeight)
            return;

        gc.drawImage(sprite.image, x, y, null);
    }

    /** A white box with a one-pixel black frame, FillRect plus FrameRect. */
    public static void fillFrame(int x, int y, int width, int height) {
        gc.setColor(SNOW);
        gc.fillRect(x, y, width, height);
        gc.setColor(Color.BLACK);
        gc.drawRect(x, y, width - 1, height - 1);
    }

    public static void fillGrey(int x, int y, int width, int height) {
        gc.setColor(GREY);
        gc.fillRect(x, y, width, height);
    }

    /** y is the text baseline. */
    public static void drawTextFont(int x, int y, String text, boolean bold) {
        gc.setFont(bold ? LABEL_FONT : VALUE_FONT);
        gc.setColor(Color.BLACK);
        gc.drawString(text, x, y);
    }

    public static void drawText(int x, int y, String text) {
        drawTextFont(x, y, text, false);
    }

    public static int textWidthFont(String text, boolean bold) {
        return (bold ? labelMetrics : valueMetrics).stringWidth(text);
    }

    public static int textWidth(String text) {
        return textWidthFont(text, false);
    }

    public static int fontAscent() {
        return valueMetrics.getAscent();
    }

    public static int fontHeight() {
        return valueMetrics.getAscent() + valueMetrics.getDescent();
    }
}
