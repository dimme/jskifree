/* The 89 bitmaps held in ski32.exe's resource section, decoded at start-up
 * from the extracted BMPs in resources/sprites.
 *
 * The resources are 4bpp DIBs. Pure white is the key colour: the original
 * built each sprite's mask by blitting the bitmap into a monochrome DC over
 * a white background, which made white see-through. Here each sprite is an
 * ARGB image with white pixels fully transparent, which comes to the same
 * thing when composited over the white snow.
 */
package skifree;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static skifree.Consts.BITMAP_COUNT;

public final class Sprites {
    private Sprites() {}

    /** One sprite: its size and its image. */
    public static final class Bitmap {
        public final int width;
        public final int height;
        public final BufferedImage image;

        Bitmap(int width, int height, BufferedImage image) {
            this.width = width;
            this.height = height;
            this.image = image;
        }
    }

    /** Windows' button face, used for dialog chrome. */
    public static final int GREY_RGB = 0xFFC0C0C0;
    /** The snow, and the sprites' transparent colour. */
    public static final int WHITE_RGB = 0xFFFFFFFF;

    /* Indexed by bitmap id, so entry 0 is a placeholder and the ids line up
     * one-for-one with the resource ids in ski32.exe. */
    private static final Bitmap[] BITMAPS = new Bitmap[BITMAP_COUNT + 1];

    public static Bitmap get(int id) {
        return BITMAPS[id];
    }

    public static int width(int id)  { return BITMAPS[id] == null ? 0 : BITMAPS[id].width; }
    public static int height(int id) { return BITMAPS[id] == null ? 0 : BITMAPS[id].height; }

    private static final String DIR = "/resources/sprites/";

    /** Load every sprite listed in resources/sprites/index.txt, in id order. */
    public static void load() throws IOException {
        BITMAPS[0] = new Bitmap(0, 0, null);
        try (InputStream index = Sprites.class.getResourceAsStream(DIR + "index.txt")) {
            if (index == null)
                throw new IOException("missing " + DIR + "index.txt");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(index, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                int id = Integer.parseInt(line.substring(0, 3));
                if (id < 1 || id > BITMAP_COUNT)
                    throw new IOException("sprite id out of range: " + line);
                BITMAPS[id] = decode(Resources.read(DIR + line));
            }
        }
        for (int i = 1; i <= BITMAP_COUNT; i++)
            if (BITMAPS[i] == null)
                throw new IOException("sprite " + i + " is missing from " + DIR);
    }

    /** Decode a 4bpp bottom-up DIB into an ARGB image with white keyed out. */
    private static Bitmap decode(byte[] raw) throws IOException {
        int pixelOffset = Resources.le32(raw, 10);
        int width = Resources.le32(raw, 18);
        int height = Resources.le32(raw, 22);
        int bitsPerPixel = Resources.le16(raw, 28);
        if (bitsPerPixel != 4)
            throw new IOException("expected a 4bpp sprite, got " + bitsPerPixel);

        int[] palette = new int[16];
        for (int i = 0; i < 16; i++) {
            int b = raw[54 + i * 4] & 0xFF;
            int g = raw[55 + i * 4] & 0xFF;
            int r = raw[56 + i * 4] & 0xFF;
            palette[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        int stride = ((width * 4 + 31) / 32) * 4;    /* DWORD aligned rows */
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            int base = pixelOffset + (height - 1 - y) * stride;
            for (int x = 0; x < width; x++) {
                int packed = raw[base + x / 2] & 0xFF;
                int index = (x % 2 == 0) ? (packed >> 4) : (packed & 0x0F);
                int colour = palette[index];
                row[x] = colour == WHITE_RGB ? 0 : colour;
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return new Bitmap(width, height, image);
    }
}
