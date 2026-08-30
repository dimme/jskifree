/* Reading the extracted originals off the classpath, plus the little-endian
 * helpers the DIB, ICO and WAV decoders share. */
package skifree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Resources {
    private Resources() {}

    public static byte[] read(String path) throws IOException {
        try (InputStream in = Resources.class.getResourceAsStream(path)) {
            if (in == null)
                throw new IOException("missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0)
                out.write(chunk, 0, n);
            return out.toByteArray();
        }
    }

    public static int le16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    public static int le32(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
             | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }
}
