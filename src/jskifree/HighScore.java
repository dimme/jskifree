/* Top-ten tables.
 *
 * ski32.exe kept these in the [Ski] section of entpack.ini, one line per
 * course, as a space-separated list of scores under the keys SS (slalom),
 * GS (tree slalom) and FS (freestyle). The same layout is used here, in
 * ~/.skifree, so the file stays readable and hand-editable.
 *
 * Times are stored negated, which is the trick the original used so that a
 * single "bigger is better" sort could rank both the timed courses and the
 * freestyle score.
 */
package jskifree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static jskifree.Consts.*;

public final class HighScore {
    private HighScore() {}

    private static final int MAX_SCORES = 10;

    /* The most recent scoreboard, kept so the game can show it the way the
     * original's message box did. Dismissed by the next key or click. */
    private static String report;
    private static boolean reportVisible;

    public static String report() {
        return reportVisible ? report : null;
    }

    public static void dismissReport() {
        reportVisible = false;
    }

    public static String path() {
        /* $HOME first, as the C version did, then the JVM's idea of it. */
        String home = System.getenv("HOME");
        if (home == null || home.isEmpty())
            home = System.getProperty("user.home");
        return (home != null ? home : ".") + "/.skifree";
    }

    private static String keyForCourse(int course) {
        switch (course) {
        case COURSE_SLALOM:      return "SS";
        case COURSE_TREE_SLALOM: return "GS";
        default:                 return "FS";
        }
    }

    private static String nameForCourse(int course) {
        switch (course) {
        case COURSE_SLALOM:      return "Slalom";
        case COURSE_TREE_SLALOM: return "Tree Slalom";
        default:                 return "Freestyle";
        }
    }

    /** Read the whole file, collecting the scores stored under key and
     *  keeping the other lines verbatim so they can be written out again. */
    private static List<Long> readScores(String key, List<String> otherLines) {
        List<Long> scores = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + "=")) {
                    for (String token : line.substring(key.length() + 1).trim().split("\\s+")) {
                        if (scores.size() >= MAX_SCORES)
                            break;
                        if (token.isEmpty())
                            continue;
                        try {
                            scores.add(Long.parseLong(token));
                        } catch (NumberFormatException e) {
                            break;
                        }
                    }
                } else {
                    otherLines.add(line);
                }
            }
        } catch (IOException e) {
            /* No file yet: an empty table. */
        }
        return scores;
    }

    private static void writeScores(String key, List<Long> scores, List<String> otherLines) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path()))) {
            for (String line : otherLines)
                out.println(line);
            StringBuilder sb = new StringBuilder(key).append('=');
            for (long score : scores)
                sb.append(score).append(' ');
            out.println(sb);
        } catch (IOException e) {
            System.err.println("skifree: cannot write " + path() + ": " + e.getMessage());
        }
    }

    private static String formatScore(long score, boolean isATime) {
        if (isATime) {
            long milliseconds = -score;
            long hundredths = (milliseconds % 1000) / 10;
            long seconds = (milliseconds / 1000) % 60;
            long minutes = (milliseconds / 1000 / 60) % 60;
            long hours = milliseconds / 1000 / 3600;
            return String.format("%2d:%02d:%02d.%02d", hours, minutes, seconds, hundredths);
        }
        return String.format("%9d", score);
    }

    public static void record(int course, long score, boolean lowerIsBetter) {
        String key = keyForCourse(course);

        /* Negating a time turns "lower is better" into "higher is better". */
        if (lowerIsBetter)
            score = -score;

        List<String> otherLines = new ArrayList<>();
        List<Long> scores = readScores(key, otherLines);

        int position;
        for (position = 0; position < scores.size(); position++)
            if (scores.get(position) < score)
                break;

        if (position < MAX_SCORES) {
            scores.add(position, score);
            while (scores.size() > MAX_SCORES)
                scores.remove(scores.size() - 1);
        }
        writeScores(key, scores, otherLines);

        /* Build the scoreboard exactly as the original's message box showed
         * it: one entry per line, " <-- that's you!" after the new entry,
         * and a blank line plus "<score> <-- try again!" when it missed. */
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scores.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(formatScore(scores.get(i), lowerIsBetter));
            if (i == position) sb.append(" <-- that's you!");
        }
        if (position >= MAX_SCORES)
            sb.append("\n\n").append(formatScore(score, lowerIsBetter)).append(" <-- try again!");

        report = sb.toString();
        System.out.println(nameForCourse(course) + " high scores\n" + report);
        System.out.flush();
        reportVisible = true;
    }
}
