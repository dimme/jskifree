/* The three marked runs.
 *
 * Each course is a fixed corridor down the hill with a start banner, a
 * finish banner and, for the two slaloms, a line of gates. A gate turns
 * green as you pass it on the correct side and red if you miss it, and a
 * miss adds five seconds to your time.
 */
package jskifree;

import java.util.ArrayList;
import java.util.List;

import static jskifree.Consts.*;

public final class Course {
    private Course() {}

    private static final Game g = Game.g;

    /* Gate spacing and the offset either side of the corridor centre. */
    private static final int SLALOM_FIRST_GATE_Y  = 0x3C0;
    private static final int SLALOM_GATE_SPACING  = 0x140;
    private static final int SLALOM_GATE_LEFT_X   = -496;
    private static final int SLALOM_GATE_RIGHT_X  = -400;

    private static final int TREE_FIRST_GATE_Y    = 0x410;
    private static final int TREE_GATE_SPACING    = 400;
    private static final int TREE_GATE_LEFT_X     = 400;
    private static final int TREE_GATE_RIGHT_X    = 432;

    private static final int MAX_GATES = 64;

    private static final class Gate {
        final int x, y;
        final int bitmap;            /* BITMAP_GATE_LEFT or BITMAP_GATE_RIGHT */
        final GameObject marker;     /* the object on the hill, to be recoloured */
        Gate(int x, int y, int bitmap, GameObject marker) {
            this.x = x; this.y = y; this.bitmap = bitmap; this.marker = marker;
        }
    }

    /* One gate list per slalom. Both stand for the whole life, exactly like
     * the original's course schedules. */
    private static final int GATE_LIST_SLALOM = 0, GATE_LIST_TREE = 1;
    @SuppressWarnings("unchecked")
    private static final List<Gate>[] gates = new List[] { new ArrayList<Gate>(), new ArrayList<Gate>() };

    private static int gateListFor(int course) {
        return course == COURSE_SLALOM ? GATE_LIST_SLALOM : GATE_LIST_TREE;
    }

    /* ---------------------------------------------------------------- */
    /* Construction                                                      */
    /* ---------------------------------------------------------------- */

    private static void addGate(int list, int x, int y, int bitmap) {
        if (gates[list].size() >= MAX_GATES)
            return;
        gates[list].add(new Gate(x, y, bitmap, World.addFixture(bitmap, x, y)));
    }

    private static void buildSlalomGates() {
        boolean onTheLeft = true;
        for (int y = SLALOM_FIRST_GATE_Y; y < SLALOM_FINISH_Y; y += SLALOM_GATE_SPACING) {
            addGate(GATE_LIST_SLALOM,
                    onTheLeft ? SLALOM_GATE_LEFT_X : SLALOM_GATE_RIGHT_X, y,
                    onTheLeft ? BITMAP_GATE_LEFT : BITMAP_GATE_RIGHT);
            onTheLeft = !onTheLeft;
        }
    }

    private static void buildTreeSlalomGates() {
        boolean onTheLeft = true;
        for (int y = TREE_FIRST_GATE_Y; y < LONG_COURSE_FINISH_Y; y += TREE_GATE_SPACING) {
            addGate(GATE_LIST_TREE,
                    onTheLeft ? TREE_GATE_LEFT_X : TREE_GATE_RIGHT_X, y,
                    onTheLeft ? BITMAP_GATE_LEFT : BITMAP_GATE_RIGHT);
            onTheLeft = !onTheLeft;
            /* The original salted the run with an extra tree beside each
             * gate, drawn from the usual tree lottery, so they can crash you
             * (or catch fire) like any other tree on the hill. */
            World.addCourseObstacle(OBJECT_TREE, World.randomTreeBitmap(),
                                    TREE_GATE_LEFT_X + World.random(32), y);
        }
    }

    /** Start and finish banners flank the corridor at the two ends. */
    private static void addCourseMarkers(int leftX, int rightX, int finishY) {
        World.addFixture(BITMAP_START_RIGHT,  leftX,  COURSE_START_Y);
        World.addFixture(BITMAP_START_LEFT,   rightX, COURSE_START_Y);
        World.addFixture(BITMAP_FINISH_RIGHT, leftX,  finishY);
        World.addFixture(BITMAP_FINISH_LEFT,  rightX, finishY);
    }

    public static void buildStartArea() {
        int bannerY = g.viewBottom - SPAWN_STEP;

        gates[GATE_LIST_SLALOM].clear();
        gates[GATE_LIST_TREE].clear();

        /* Title art sits beside the skier: the logo's base is level with
         * the skier's (world y = 0), the version line just under it. */
        World.addFixture(BITMAP_LOGO, -40 - Sprites.width(BITMAP_LOGO) / 2, 0);
        World.addFixture(BITMAP_VERSION, -40 - Sprites.width(BITMAP_LOGO) / 2,
                         Sprites.height(BITMAP_VERSION) + 4);
        World.addFixture(BITMAP_HELP_NUMPAD, Sprites.width(BITMAP_HELP_NUMPAD),
                         Sprites.height(BITMAP_HELP_NUMPAD));
        World.addFixture(BITMAP_HELP_KEYS, Sprites.width(BITMAP_HELP_NUMPAD),
                         Sprites.height(BITMAP_HELP_NUMPAD) + Sprites.height(BITMAP_HELP_KEYS) + 4);

        if (bannerY > COURSE_START_Y)
            bannerY = 0x208;

        /* One signpost per course along the bottom of the opening screen. */
        int slalomX = g.viewLeft + SPAWN_STEP;
        int treeX = g.viewRight - SPAWN_STEP;
        if (slalomX < -0x140) slalomX = -0x140;
        if (treeX > 0x140) treeX = 0x140;
        World.addFixture(BITMAP_SIGN_SLALOM,    slalomX, bannerY);
        World.addFixture(BITMAP_SIGN_TREE,      treeX,   bannerY);
        World.addFixture(BITMAP_SIGN_FREESTYLE, 0,       bannerY);

        addCourseMarkers(SLALOM_LEFT, SLALOM_RIGHT, SLALOM_FINISH_Y);
        addCourseMarkers(TREE_SLALOM_LEFT, TREE_SLALOM_RIGHT, LONG_COURSE_FINISH_Y);
        addCourseMarkers(FREESTYLE_LEFT, FREESTYLE_RIGHT, LONG_COURSE_FINISH_Y);

        buildSlalomGates();
        buildTreeSlalomGates();

        /* The lift line runs down the left of the hill. */
        for (int y = -1024; y < 23553; y += 2048)
            World.addFixture(BITMAP_LIFT_TOWER, -128, y);
    }

    /* ---------------------------------------------------------------- */
    /* Tracking the player                                               */
    /* ---------------------------------------------------------------- */

    /** Where a value stood at the moment the skier crossed a given line,
     *  assuming both moved linearly across the frame. */
    private static long interpolateAtCrossing(long valueNow, long valueBefore,
                                              int yNow, int yBefore, int crossingY) {
        long span = yNow - yBefore;
        if (span == 0)
            return valueNow;
        return valueBefore + (valueNow - valueBefore) * (crossingY - yBefore) / span;
    }

    /** The corridors are inclusive of their edges. */
    private static int courseForX(int x) {
        if (x >= SLALOM_LEFT && x <= SLALOM_RIGHT)           return COURSE_SLALOM;
        if (x >= FREESTYLE_LEFT && x <= FREESTYLE_RIGHT)     return COURSE_FREESTYLE;
        if (x >= TREE_SLALOM_LEFT && x <= TREE_SLALOM_RIGHT) return COURSE_TREE_SLALOM;
        return COURSE_NONE;
    }

    private static int courseFinishY(int course) {
        return course == COURSE_SLALOM ? SLALOM_FINISH_Y : LONG_COURSE_FINISH_Y;
    }

    private static void startCourse(int course, int previousY, int y) {
        g.course = course;
        g.courseStartMs = interpolateAtCrossing(g.nowMs, g.previousMs, y, previousY, COURSE_START_Y);
        g.courseTimeMs = 0;
        g.nextGate = 0;
        /* Style is deliberately NOT reset here. The original only zeroed it
         * on a new game, so freestyle style accumulates across runs. */
    }

    private static void finishCourse(int previousY, int y) {
        int course = g.course;
        int finish = courseFinishY(course);
        long crossedAt = interpolateAtCrossing(g.nowMs, g.previousMs, y, previousY, finish);

        if (course != COURSE_FREESTYLE)
            g.courseTimeMs = crossedAt - g.courseStartMs;
        g.course = COURSE_NONE;

        /* The scoreboard that follows is modal: the main loop holds the
         * world while HighScore.report() is showing. */
        if (course == COURSE_FREESTYLE)
            HighScore.record(course, g.stylePoints, false);
        else
            HighScore.record(course, g.courseTimeMs, true);
    }

    /** Judge every gate the skier has just drawn level with. */
    private static void judgeGates(int previousX, int previousY, int x, int y) {
        if (g.course != COURSE_SLALOM && g.course != COURSE_TREE_SLALOM)
            return;
        List<Gate> list = gates[gateListFor(g.course)];

        while (g.nextGate < list.size()) {
            Gate gate = list.get(g.nextGate);
            if (gate.y >= y)
                break;                                 /* not reached yet */

            long crossingX = interpolateAtCrossing(x, previousX, y, previousY, gate.y);
            boolean missed = (gate.bitmap == BITMAP_GATE_LEFT  && crossingX > gate.x) ||
                             (gate.bitmap == BITMAP_GATE_RIGHT && crossingX < gate.x);

            if (missed)
                g.courseStartMs -= MISSED_GATE_PENALTY_MS;
            if (gate.marker != null)
                World.setBitmap(gate.marker, missed ? BITMAP_GATE_MISSED : BITMAP_GATE_PASSED);
            g.nextGate++;
        }
    }

    public static void trackPlayer(int previousX, int previousY) {
        GameObject skier = g.player;
        if (skier == null)
            return;
        int x = skier.x;
        int y = skier.y;

        if (g.course == COURSE_NONE) {
            /* Crossing the start line inside a corridor begins that run. */
            if (previousY <= COURSE_START_Y && y > COURSE_START_Y) {
                long entryX = interpolateAtCrossing(x, previousX, y, previousY, COURSE_START_Y);
                int course = courseForX((int) entryX);
                if (course != COURSE_NONE)
                    startCourse(course, previousY, y);
            }
            return;
        }

        /* Only the two slaloms are timed. */
        if (g.course != COURSE_FREESTYLE)
            g.courseTimeMs = g.nowMs - g.courseStartMs;

        /* Skiing back to or above the start line abandons the run. */
        if (y <= COURSE_START_Y) {
            g.course = COURSE_NONE;
            return;
        }

        judgeGates(previousX, previousY, x, y);

        if (y > courseFinishY(g.course))
            finishCourse(previousY, y);
    }

    /** The status bar counts down to the finish while a course is being run
     *  and counts up from the top of the hill the rest of the time. */
    public static int distanceMetres() {
        int y = g.skierY;

        /* DrawStatusBar read the distance off the skier object, so once the
         * yeti has removed him the readout shows 00m. */
        if (g.player == null)
            return 0;

        int distance = g.course != COURSE_NONE ? courseFinishY(g.course) - y : y;
        return distance / UNITS_PER_METRE;
    }
}
