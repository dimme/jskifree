/* The object list, the streaming terrain and the frame update.
 *
 * The original kept every object on one singly linked list and streamed new
 * scenery in from whichever edge the player was moving towards, capping the
 * total sprite area so the hill never became impassably dense.
 */
package skifree;

import java.util.ArrayList;
import java.util.List;

import static skifree.Consts.*;

public final class World {
    private World() {}

    private static final Game g = Game.g;

    /** The player is drawn at the middle of the window horizontally and a
     *  third of the way down, which is where OnSize_ComputeViewport put it. */
    private static final int PLAYER_SCREEN_FRACTION_Y = 3;

    /* ---------------------------------------------------------------- */
    /* Random numbers                                                    */
    /* ---------------------------------------------------------------- */

    /** A small deterministic generator, so a given seed always builds the
     *  same hill. The original called its own LCG through the same "pick a
     *  number below n" interface. */
    public static int random(int limit) {
        if (limit <= 1)
            return 0;
        g.rngState = g.rngState * 1103515245 + 12345;
        return (int) (((g.rngState & 0xFFFFFFFFL) >>> 16) % limit);
    }

    /* ---------------------------------------------------------------- */
    /* Object list                                                       */
    /* ---------------------------------------------------------------- */

    /** Course furniture is placed deliberately rather than generated, so it
     *  is kept out of the running total that caps scenery density. */
    private static boolean countsTowardsDensity(GameObject object) {
        return !object.isFixture();
    }

    private static int bitmapArea(int bitmap) {
        return Sprites.width(bitmap) * Sprites.height(bitmap);
    }

    /** A couple of sprites are markings on the snow rather than things
     *  standing on it. The tag only affects draw order. */
    private static boolean bitmapLiesFlat(int bitmap) {
        return bitmap == BITMAP_MOGUL_FIELD || bitmap == BITMAP_DOG_MESS;
    }

    public static void setBitmap(GameObject object, int bitmap) {
        if (object.bitmap == bitmap)
            return;
        if (countsTowardsDensity(object)) {
            if (object.bitmap != 0)
                g.coveredArea -= bitmapArea(object.bitmap);
            g.coveredArea += bitmapArea(bitmap);
        }
        object.bitmap = bitmap;
        if (bitmapLiesFlat(bitmap))
            object.flags |= OBJECT_FLAG_FLAT;
        else
            object.flags &= ~OBJECT_FLAG_FLAT;
    }

    public static void setState(GameObject object, int state) {
        if (object.state == state)
            return;
        object.state = state;
        setBitmap(object, Tables.STATE_BITMAP[state & 0x3F]);
    }

    public static void kill(GameObject object) {
        object.flags |= OBJECT_FLAG_DEAD;
    }

    /** How many of the original's 100 sprite records this state would
     *  occupy: every generated object, plus the fixtures currently close
     *  enough to the view to have had a sprite. */
    private static int poolInUse() {
        int count = 0;
        for (GameObject object = g.objects; object != null; object = object.next) {
            if (object.isDead())
                continue;
            if (!object.isFixture()) {
                count++;
            } else {
                int x = worldWrap(object.x - g.viewLeft);
                int y = worldWrap(object.y - g.viewTop);
                if (x >= -SPAWN_MARGIN && x <= g.viewWidth + SPAWN_MARGIN &&
                    y >= -SPAWN_MARGIN && y <= g.viewHeight + SPAWN_MARGIN)
                    count++;
            }
        }
        return count;
    }

    private static GameObject alloc(int type, int x, int y, int flags) {
        GameObject object = new GameObject();
        object.next = g.objects;
        g.objects = object;
        object.type = type;
        object.x = worldWrap(x);
        object.y = worldWrap(y);
        object.previousY = y;
        object.flags = flags;
        object.state = -1;
        return object;
    }

    public static GameObject add(int type, int state, int x, int y) {
        if (poolInUse() >= OBJECT_POOL_SIZE)
            return null;               /* the pool is exhausted, as in the original */
        GameObject object = alloc(type, x, y, OBJECT_FLAG_SOLID);
        setState(object, state);
        return object;
    }

    public static GameObject addBanner(int bitmap, int x, int y) {
        if (poolInUse() >= OBJECT_POOL_SIZE)
            return null;
        GameObject object = alloc(OBJECT_BANNER, x, y, 0);
        setBitmap(object, bitmap);
        return object;
    }

    /** An animated object that is part of the furniture rather than
     *  generated terrain, the yetis and the chairlift. Flagged as a fixture
     *  before the bitmap is chosen so its area never enters the budget. */
    public static GameObject addScheduled(int type, int state, int x, int y) {
        GameObject object = alloc(type, x, y, OBJECT_FLAG_SOLID | OBJECT_FLAG_FIXTURE);
        setState(object, state);
        return object;
    }

    /** Scenery: keeps its real object type while taking a bitmap chosen at
     *  spawn time, which is how the original built the hill. */
    public static GameObject addScenery(int type, int bitmap, int x, int y) {
        if (poolInUse() >= OBJECT_POOL_SIZE)
            return null;
        GameObject object = alloc(type, x, y, OBJECT_FLAG_SOLID);
        setBitmap(object, bitmap);
        return object;
    }

    /** Course furniture that is also a real obstacle, the trees lining the
     *  tree slalom. */
    public static GameObject addCourseObstacle(int type, int bitmap, int x, int y) {
        GameObject object = alloc(type, x, y, OBJECT_FLAG_SOLID | OBJECT_FLAG_FIXTURE);
        setBitmap(object, bitmap);
        return object;
    }

    /** Course furniture, gates, signs, lift towers. */
    public static GameObject addFixture(int bitmap, int x, int y) {
        GameObject object = alloc(OBJECT_SIGN, x, y, OBJECT_FLAG_FIXTURE);
        setBitmap(object, bitmap);
        return object;
    }

    /** Bounding box in screen coordinates, relative to the view's top-left,
     *  computed with 16-bit wrap so the world is a seamless torus.
     *  Returns {left, top, right, bottom}. */
    public static int[] bounds(GameObject object, int[] out) {
        int width = Sprites.width(object.bitmap);
        int height = Sprites.height(object.bitmap);
        int x = worldWrap(object.x - g.viewLeft);
        int y = worldWrap(object.y - g.viewTop);
        /* x is the sprite's horizontal centre and y its base; height lifts it. */
        int left = x - width / 2;
        int bottom = y - object.height;
        out[0] = left;
        out[1] = bottom - height;
        out[2] = left + width;
        out[3] = bottom;
        return out;
    }

    /* ---------------------------------------------------------------- */
    /* Viewport                                                          */
    /* ---------------------------------------------------------------- */

    public static void updateViewport() {
        if (g.player != null) {
            g.skierX = g.player.x;
            g.skierY = g.player.y;
        }
        int centreX = g.skierX;
        int centreY = g.skierY;

        g.viewWidth = Graphics.windowWidth;
        g.viewHeight = Graphics.windowHeight;

        g.viewLeft = centreX - Graphics.windowWidth / 2;
        g.viewTop = centreY - Graphics.windowHeight / PLAYER_SCREEN_FRACTION_Y;
        g.viewRight = g.viewLeft + Graphics.windowWidth;
        g.viewBottom = g.viewTop + Graphics.windowHeight;
    }

    /* ---------------------------------------------------------------- */
    /* Terrain generation                                                */
    /* ---------------------------------------------------------------- */

    /** Which of the three marked courses, if any, contains this point. */
    private static int regionAt(int x, int y) {
        if (x >= SLALOM_LEFT && x <= SLALOM_RIGHT &&
            y >= COURSE_START_Y && y <= SLALOM_FINISH_Y)
            return COURSE_SLALOM;
        if (x >= TREE_SLALOM_LEFT && x <= TREE_SLALOM_RIGHT &&
            y >= COURSE_START_Y && y <= LONG_COURSE_FINISH_Y)
            return COURSE_TREE_SLALOM;
        if (x >= FREESTYLE_LEFT && x <= FREESTYLE_RIGHT &&
            y >= COURSE_START_Y && y <= LONG_COURSE_FINISH_Y)
            return COURSE_FREESTYLE;
        return COURSE_NONE;
    }

    /** Objects stop being created once their combined area exceeds this
     *  share of the visible area. The divisors are the original's. */
    private static boolean densityBudgetExceeded(int divisor) {
        int area = (g.viewWidth + 2 * SPAWN_MARGIN) *
                   (g.viewHeight + 2 * SPAWN_MARGIN);
        return g.coveredArea > area / divisor;
    }

    /** Open hillside: mostly trees, with occasional bumps, rocks and traffic. */
    private static int chooseOpenSlopeObject() {
        if (densityBudgetExceeded(32))
            return OBJECT_NONE;
        int roll = random(1000);
        if (roll <  50) return OBJECT_TREE_ANIM;
        if (roll < 500) return OBJECT_TREE;
        if (roll < 700) return OBJECT_RAMP;
        if (roll < 750) return OBJECT_MOGULS;
        if (roll < 950) return OBJECT_ROCK;
        if (roll < 970) return OBJECT_RAINBOW;
        if (roll < 990) return OBJECT_OTHER_SKIER;
        return OBJECT_DOG;
    }

    /** The slalom run is groomed: nothing but moguls between the gates. */
    private static int chooseSlalomObject() {
        if (densityBudgetExceeded(64))
            return OBJECT_NONE;
        return OBJECT_MOGULS;
    }

    /** The tree slalom is exactly what it says it is. */
    private static int chooseTreeSlalomObject() {
        if (densityBudgetExceeded(16))
            return OBJECT_NONE;
        return random(64) == 0 ? OBJECT_DOG : OBJECT_TREE;
    }

    /** The freestyle run is stocked with ramps to jump off. */
    private static int chooseFreestyleObject() {
        if (densityBudgetExceeded(32))
            return OBJECT_NONE;
        int roll = random(100);
        if (roll <  2) return OBJECT_TREE_ANIM;
        if (roll < 20) return OBJECT_TREE;
        if (roll < 50) return OBJECT_RAMP;
        if (roll < 60) return OBJECT_MOGULS;
        if (roll < 80) return OBJECT_ROCK;
        return OBJECT_RAINBOW;
    }

    /** The 1-bare : 1-large : 6-small tree draw used everywhere trees appear. */
    public static int randomTreeBitmap() {
        switch (random(8)) {
        case 0:  return BITMAP_TREE_BARE;
        case 1:  return BITMAP_TREE_LARGE;
        default: return BITMAP_TREE_SMALL;
        }
    }

    /** Scenery types carry no state machine, just a bitmap chosen on the spot. */
    private static int chooseStaticBitmap(int type) {
        switch (type) {
        case OBJECT_MOGULS:  return BITMAP_MOGUL_FIELD;
        case OBJECT_TREE:    return randomTreeBitmap();
        case OBJECT_ROCK:    return random(4) == 0 ? BITMAP_STUMP : BITMAP_ROCK;
        case OBJECT_RAMP:    return random(3) == 0 ? BITMAP_RAMP : BITMAP_SMALL_RAMP;
        case OBJECT_RAINBOW: return BITMAP_RAINBOW_BAR;
        default:             return BITMAP_TREE_SMALL;
        }
    }

    private static final int EDGE_LEFT = 0, EDGE_RIGHT = 1, EDGE_TOP = 2, EDGE_BOTTOM = 3;

    private static void spawnAtEdge(int edge) {
        int x, y;
        switch (edge) {
        case EDGE_LEFT:
            x = g.viewLeft - SPAWN_STEP;
            y = g.viewTop + random(g.viewHeight);
            break;
        case EDGE_RIGHT:
            x = g.viewRight + SPAWN_STEP;
            y = g.viewTop + random(g.viewHeight);
            break;
        case EDGE_TOP:
            x = g.viewLeft + random(g.viewWidth);
            y = g.viewTop - SPAWN_STEP;
            break;
        default:
            x = g.viewLeft + random(g.viewWidth);
            y = g.viewBottom + SPAWN_STEP;
            break;
        }

        int type;
        switch (regionAt(x, y)) {
        case COURSE_SLALOM:      type = chooseSlalomObject();     break;
        case COURSE_TREE_SLALOM: type = chooseTreeSlalomObject(); break;
        case COURSE_FREESTYLE:   type = chooseFreestyleObject();  break;
        default:                 type = chooseOpenSlopeObject();  break;
        }
        if (type == OBJECT_NONE)
            return;

        if (type < OBJECT_FIRST_STATIC)
            add(type, Tables.TYPE_INITIAL_STATE[type], x, y);
        else
            addScenery(type, chooseStaticBitmap(type), x, y);
    }

    /** Convert this frame's scrolling into spawn events. Every SPAWN_STEP
     *  units of travel introduces one new object at the leading edge. */
    private static void streamTerrain(int scrolledX, int scrolledY) {
        g.scrollDebtX += scrolledX;
        g.scrollDebtY += scrolledY;

        while (g.scrollDebtX > SPAWN_STEP) {
            g.scrollDebtX -= SPAWN_STEP;
            spawnAtEdge(EDGE_RIGHT);
        }
        while (g.scrollDebtX < -SPAWN_STEP) {
            g.scrollDebtX += SPAWN_STEP;
            spawnAtEdge(EDGE_LEFT);
        }
        while (g.scrollDebtY > SPAWN_STEP) {
            g.scrollDebtY -= SPAWN_STEP;
            spawnAtEdge(EDGE_BOTTOM);
        }
        while (g.scrollDebtY < -SPAWN_STEP) {
            g.scrollDebtY += SPAWN_STEP;
            spawnAtEdge(EDGE_TOP);
        }
    }

    /* ---------------------------------------------------------------- */
    /* Frame update                                                      */
    /* ---------------------------------------------------------------- */

    /** Anything that has scrolled well clear of the window is discarded. */
    private static void retireOffscreenObjects() {
        int[] r = new int[4];
        for (GameObject object = g.objects; object != null; object = object.next) {
            if (object == g.player ||
                (object.flags & (OBJECT_FLAG_NO_SCROLL | OBJECT_FLAG_FIXTURE)) != 0)
                continue;
            bounds(object, r);
            if (r[2] < -SPAWN_MARGIN ||
                r[0] > g.viewWidth + SPAWN_MARGIN ||
                r[3] < -SPAWN_MARGIN ||
                r[1] > g.viewHeight + SPAWN_MARGIN)
                kill(object);
        }
    }

    private static void reapDeadObjects() {
        GameObject previous = null;
        GameObject object = g.objects;
        while (object != null) {
            GameObject next = object.next;
            if (object.isDead()) {
                if (previous == null)
                    g.objects = next;
                else
                    previous.next = next;
                if (object.bitmap != 0 && countsTowardsDensity(object))
                    g.coveredArea -= bitmapArea(object.bitmap);
                if (object == g.player)
                    g.player = null;
            } else {
                previous = object;
            }
            object = next;
        }
    }

    public static void update() {
        int previousX = g.player != null ? g.player.x : 0;
        int previousY = g.player != null ? g.player.y : 0;

        for (GameObject object = g.objects; object != null; object = object.next)
            object.previousY = object.y;

        for (GameObject object = g.objects; object != null; object = object.next) {
            if (object.isDead())
                continue;
            if (object == g.player)
                Skier.update(object);
            else if (object.type < OBJECT_FIRST_STATIC)
                Npc.update(object);
        }

        if (g.player != null)
            Course.trackPlayer(previousX, previousY);

        Collide.all();

        updateViewport();
        if (g.player != null)
            streamTerrain(g.player.x - previousX, g.player.y - previousY);

        retireOffscreenObjects();
        reapDeadObjects();

        /* About once every 26 seconds a snowboarder rides in from above and
         * cuts down the hill: the original rolled 1 in 0x29A every frame. */
        if (g.player != null && random(0x29A) == 0)
            add(OBJECT_SNOWBOARDER, SNOWBOARDER_LEFT,
                g.viewLeft + random(g.viewWidth), g.viewTop - SPAWN_STEP);
    }

    /* ---------------------------------------------------------------- */
    /* Drawing                                                           */
    /* ---------------------------------------------------------------- */

    /** Painter's algorithm: things lower down the hill are drawn last. A
     *  sprite that lies flat on the snow sorts by the top of its patch. */
    private static int depthOf(GameObject object) {
        if ((object.flags & OBJECT_FLAG_FLAT) != 0)
            return object.y - Sprites.height(object.bitmap);
        return object.y;
    }

    private static final List<GameObject> drawOrder = new ArrayList<>();

    public static void draw() {
        drawOrder.clear();
        for (GameObject object = g.objects; object != null; object = object.next)
            if (!object.isDead())
                drawOrder.add(object);
        drawOrder.sort((left, right) -> {
            int ld = depthOf(left), rd = depthOf(right);
            if (ld != rd)
                return Integer.compare(ld, rd);
            return Integer.compare(left.x, right.x);
        });

        int[] r = new int[4];
        for (GameObject object : drawOrder) {
            bounds(object, r);
            Graphics.drawBitmap(object.bitmap, r[0], r[1]);
        }
    }

    /* ---------------------------------------------------------------- */
    /* Reset                                                             */
    /* ---------------------------------------------------------------- */

    public static void reset() {
        g.objects = null;
        g.player = null;
        g.coveredArea = 0;
        g.scrollDebtX = 0;
        g.scrollDebtY = 0;
        g.course = COURSE_NONE;
        g.courseTimeMs = 0;
        g.stylePoints = 0;
        g.nextGate = 0;
        g.eaten = false;
        g.doubleSpeed = false;

        g.skierX = 0;
        g.skierY = 0;

        /* The original reseeded its generator from the tick count on every
         * new game, so no two lives build quite the same hill. */
        g.rngState ^= (int) g.nowMs * 0x9E3779B1;
        g.player = add(OBJECT_SKIER, SKIER_DOWNHILL, 0, 0);
        updateViewport();
        Course.buildStartArea();
        Npc.placeYetisAndLift();
    }
}
