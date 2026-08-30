/* Sound effects.
 *
 * The nine clips are the WAVE resources of the original executable, loaded
 * from resources/sounds, and each is played from the same place the original
 * passed that slot to sndPlaySound.
 *
 * sndPlaySound was called with SND_ASYNC | SND_MEMORY and without
 * SND_NOSTOP, so a new sound cut off whatever was still playing: there is
 * one voice, and the most recent request owns it. That is reproduced here
 * with a single playback slot and a generation counter the worker checks
 * between buffers. The one deliberate softening: the yeti re-triggers its
 * cry on every frame of a chase, and restarting a 2.4 s clip every 40 ms
 * would leave only its first few milliseconds audible, so a request for the
 * sound that is already playing is ignored and the cry runs on unbroken.
 * Sound is off unless the game is started with "sound" on the command line.
 */
package jskifree;

import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import static jskifree.Consts.SOUND_COUNT;

public final class Sound {
    private Sound() {}

    private static final int BUFFER_FRAMES = 512;

    /* Which file plays for which sound id. The files are the WAVE resources
     * 1..9 of the original executable. Resources 10 and 11 exist in the
     * binary but nothing in the code references them. */
    private static final String[] FILES = {
        "01_crash.wav",        /* SOUND_CRASH       */
        "02_jump.wav",         /* SOUND_JUMP        */
        "03_dog_bark.wav",     /* SOUND_DOG         */
        "04_land.wav",         /* SOUND_LAND        */
        "05_snowboarder.wav",  /* SOUND_SNOWBOARDER */
        "06_other_skier.wav",  /* SOUND_OTHER_SKIER */
        "07_gobble.wav",       /* SOUND_EATEN       */
        "08_dog_mess.wav",     /* SOUND_DOG_MESS    */
        "09_argh.wav",         /* SOUND_YETI        */
    };

    /** A PCM clip: raw interleaved samples plus their format. */
    private static final class Clip {
        final byte[] samples;
        final AudioFormat format;
        Clip(byte[] samples, AudioFormat format) {
            this.samples = samples;
            this.format = format;
        }
    }

    private static final Clip[] CLIPS = new Clip[SOUND_COUNT];

    private static boolean enabled;
    private static final Object lock = new Object();
    private static int generation;          /* bumped whenever a new sound starts */
    private static int current = -1;        /* sound id now playing, or -1        */

    public static void init(boolean wantSound) {
        enabled = wantSound;
        if (!enabled)
            return;
        for (int i = 0; i < SOUND_COUNT; i++) {
            try {
                CLIPS[i] = parseWav(Resources.read("/resources/sounds/" + FILES[i]));
            } catch (IOException e) {
                System.err.println("skifree: cannot load " + FILES[i] + ": " + e.getMessage());
            }
        }
    }

    /** Parse RIFF/WAVE PCM by hand, as the generator did. */
    private static Clip parseWav(byte[] data) throws IOException {
        if (data.length < 12 || data[0] != 'R' || data[1] != 'I' || data[2] != 'F'
            || data[3] != 'F' || data[8] != 'W' || data[9] != 'A' || data[10] != 'V'
            || data[11] != 'E')
            throw new IOException("not a RIFF/WAVE file");
        int pos = 12;
        AudioFormat format = null;
        byte[] samples = null;
        while (pos + 8 <= data.length) {
            String id = new String(data, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = Resources.le32(data, pos + 4);
            int body = pos + 8;
            if (id.equals("fmt ")) {
                int audioFormat = Resources.le16(data, body);
                int channels = Resources.le16(data, body + 2);
                int rate = Resources.le32(data, body + 4);
                int bits = Resources.le16(data, body + 14);
                if (audioFormat != 1)
                    throw new IOException("expected PCM");
                format = new AudioFormat(rate, bits, channels, bits != 8, false);
            } else if (id.equals("data")) {
                int length = Math.min(size, data.length - body);
                samples = new byte[length];
                System.arraycopy(data, body, samples, 0, length);
            }
            pos = body + size + (size & 1);
        }
        if (format == null || samples == null)
            throw new IOException("missing fmt or data chunk");
        return new Clip(samples, format);
    }

    public static void play(int sound) {
        if (!enabled || sound < 0 || sound >= SOUND_COUNT)
            return;
        final Clip clip = CLIPS[sound];
        if (clip == null)
            return;

        final int myGeneration;
        synchronized (lock) {
            if (current == sound)
                return;                          /* already playing: let it run */
            generation++;                        /* cuts off whatever is playing */
            current = sound;
            myGeneration = generation;
        }

        Thread thread = new Thread(() -> playClip(clip, myGeneration), "skifree-sound");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playClip(Clip clip, int myGeneration) {
        int frameBytes = clip.format.getFrameSize();
        int frames = clip.samples.length / frameBytes;
        int done = 0;
        boolean cutOff = false;

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, clip.format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            try {
                line.open(clip.format);
                line.start();
                while (done < frames) {
                    int chunk = Math.min(frames - done, BUFFER_FRAMES);
                    synchronized (lock) {
                        cutOff = generation != myGeneration;
                    }
                    if (cutOff)
                        break;                   /* a newer sound took the voice */
                    line.write(clip.samples, done * frameBytes, chunk * frameBytes);
                    done += chunk;
                }
                if (cutOff)
                    line.flush();
                else
                    line.drain();
            } finally {
                line.close();
            }
        } catch (Exception e) {
            /* No audio device, or the format is refused: stay silent. */
        }

        synchronized (lock) {
            if (generation == myGeneration)
                current = -1;                    /* finished on our own */
        }
    }
}
