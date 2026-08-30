/* The window icon, the original's ICONSKI resource, from resources/icons/new.ico.
 *
 * The .ico holds three 32x32 images; the first 4-bit one is used. Its XOR
 * bitmap and AND mask are decoded into an ARGB image.
 */
package jskifree;

import java.awt.image.BufferedImage;
import java.io.IOException;

public final class Icon {
    private Icon() {}

    public static BufferedImage load() throws IOException {
        byte[] data = Resources.read("/resources/icons/new.ico");
        int count = Resources.le16(data, 4);

        int width = 0, height = 0, offset = -1;
        for (int i = 0; i < count; i++) {
            int entry = 6 + i * 16;
            int w = data[entry] & 0xFF;
            int h = data[entry + 1] & 0xFF;
            int bits = Resources.le16(data, entry + 6);
            if (bits == 4) {
                width = w == 0 ? 256 : w;
                height = h == 0 ? 256 : h;
                offset = Resources.le32(data, entry + 12);
                break;
            }
        }
        if (offset < 0)
            throw new IOException("no 4-bit image in the icon");

        int bpp = Resources.le16(data, offset + 14);
        int paletteSize = 1 << bpp;
        int[] palette = new int[paletteSize];
        for (int k = 0; k < paletteSize; k++) {
            int b = data[offset + 40 + k * 4] & 0xFF;
            int g = data[offset + 41 + k * 4] & 0xFF;
            int r = data[offset + 42 + k * 4] & 0xFF;
            palette[k] = (r << 16) | (g << 8) | b;
        }
        int xorBase = offset + 40 + paletteSize * 4;
        int xorStride = ((width * bpp + 31) / 32) * 4;
        int andBase = xorBase + xorStride * height;
        int andStride = ((width + 31) / 32) * 4;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {              /* DIB rows are bottom-up */
            int xorRow = xorBase + (height - 1 - y) * xorStride;
            int andRow = andBase + (height - 1 - y) * andStride;
            for (int x = 0; x < width; x++) {
                int packed = data[xorRow + x / 2] & 0xFF;
                int index = (x % 2 == 0) ? (packed >> 4) : (packed & 0x0F);
                int transparent = (data[andRow + x / 8] >> (7 - x % 8)) & 1;
                int alpha = transparent != 0 ? 0 : 255;
                image.setRGB(x, y, (alpha << 24) | palette[index]);
            }
        }
        return image;
    }
}
