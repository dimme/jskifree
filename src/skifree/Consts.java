/* JSkiFree, a re-implementation of ski32.exe.
 *
 * The structure, the sprite ids, the skier state machine and the numeric
 * tables all come from the original 1991 Windows game (Chris Pirih's SkiFree
 * 1.04). Where a table was recovered verbatim from the executable the comment
 * says so; behaviour that was reconstructed rather than copied is marked too.
 *
 * The original drew through the Win32 GDI with a dirty-rectangle display
 * list. Java2D lines up closely enough that the rendering is a direct
 * translation, except that we simply redraw the whole back buffer every
 * frame instead of tracking damaged rectangles.
 *
 * This class holds everything that was a #define or an enum in skifree.h.
 */
package skifree;

public final class Consts {
    private Consts() {}

    /* ---------------------------------------------------------------- */
    /* World units                                                       */
    /* ---------------------------------------------------------------- */

    /** The original works in whole pixels and reports distance as pixels/16. */
    public static final int UNITS_PER_METRE = 16;

    /** Milliseconds per simulation frame (ski32.exe used a 0x28 ms WM_TIMER). */
    public static final int FRAME_INTERVAL_MS = 40;

    /** The status readout was refreshed only every 0x148 ms, not every frame. */
    public static final int STATUS_INTERVAL_MS = 328;

    /** The original allocated exactly this many sprite records up front. */
    public static final int OBJECT_POOL_SIZE = 100;

    /** World coordinates were 16-bit in ski32.exe: the hill wraps round at
     *  +-32768 units (2048 m). Every position update goes through this. */
    public static int worldWrap(int value) {
        return ((value + 32768) & 0xFFFF) - 32768;
    }

    /** Objects are spawned and retired this far outside the visible area. */
    public static final int SPAWN_MARGIN = 120;

    /** Scrolling this many world units past the edge triggers one spawn. */
    public static final int SPAWN_STEP = 60;

    /* ---------------------------------------------------------------- */
    /* Bitmaps                                                           */
    /* ---------------------------------------------------------------- */

    public static final int BITMAP_COUNT = 89;

    /* Bitmap ids worth naming; these match the resource ids in ski32.exe. */
    public static final int BITMAP_GATE_LEFT      = 23;
    public static final int BITMAP_GATE_RIGHT     = 24;
    public static final int BITMAP_GATE_PASSED    = 25;
    public static final int BITMAP_GATE_MISSED    = 26;
    public static final int BITMAP_MOGUL_FIELD    = 27;
    public static final int BITMAP_ROCK           = 45;
    public static final int BITMAP_STUMP          = 46;
    public static final int BITMAP_SMALL_RAMP     = 47;
    public static final int BITMAP_RAMP           = 48;
    public static final int BITMAP_TREE_SMALL     = 49;
    public static final int BITMAP_TREE_BARE      = 50;
    public static final int BITMAP_TREE_LARGE     = 51;
    public static final int BITMAP_RAINBOW_BAR    = 52;
    public static final int BITMAP_LOGO           = 53;
    public static final int BITMAP_VERSION        = 54;
    public static final int BITMAP_HELP_NUMPAD    = 55;
    public static final int BITMAP_HELP_KEYS      = 56;
    public static final int BITMAP_START_RIGHT    = 57;
    public static final int BITMAP_START_LEFT     = 58;
    public static final int BITMAP_FINISH_RIGHT   = 59;
    public static final int BITMAP_FINISH_LEFT    = 60;
    public static final int BITMAP_SIGN_SLALOM    = 61;
    public static final int BITMAP_SIGN_TREE      = 62;
    public static final int BITMAP_SIGN_FREESTYLE = 63;
    public static final int BITMAP_LIFT_TOWER     = 64;
    public static final int BITMAP_DOG_MESS       = 82;
    public static final int BITMAP_MOGUL_SMALL    = 86;

    /* ---------------------------------------------------------------- */
    /* Skier states                                                      */
    /* ---------------------------------------------------------------- */

    /* States 0x00..0x15 of the player object, in the order used by the
     * original's tables. Numbering must not change: TURN_TABLE, MOTION_TABLE
     * and STATE_BITMAP are all indexed by it. */
    public static final int SKIER_DOWNHILL        = 0x00;
    public static final int SKIER_LEFT_SLIGHT     = 0x01;
    public static final int SKIER_LEFT_MEDIUM     = 0x02;
    public static final int SKIER_LEFT_HARD       = 0x03;
    public static final int SKIER_RIGHT_SLIGHT    = 0x04;
    public static final int SKIER_RIGHT_MEDIUM    = 0x05;
    public static final int SKIER_RIGHT_HARD      = 0x06;
    public static final int SKIER_SKATE_LEFT      = 0x07;
    public static final int SKIER_SKATE_RIGHT     = 0x08;
    public static final int SKIER_CLIMB_LEFT      = 0x09;
    public static final int SKIER_CLIMB_RIGHT     = 0x0A;
    public static final int SKIER_CRASHED         = 0x0B;
    public static final int SKIER_SITTING         = 0x0C;
    public static final int SKIER_JUMP_SPREAD     = 0x0D;
    public static final int SKIER_JUMP_LEFT       = 0x0E;
    public static final int SKIER_JUMP_RIGHT      = 0x0F;
    public static final int SKIER_JUMP_TWIST      = 0x10;
    public static final int SKIER_TANGLED         = 0x11;
    public static final int SKIER_JUMP_FLIP       = 0x12;
    public static final int SKIER_JUMP_TUCK       = 0x13;
    public static final int SKIER_JUMP_ROLL_LEFT  = 0x14;
    public static final int SKIER_JUMP_ROLL_RIGHT = 0x15;
    public static final int SKIER_STATE_COUNT     = 0x16;

    public static boolean skierIsCrashed(int state) {
        return state == SKIER_CRASHED || state == SKIER_TANGLED;
    }

    public static boolean skierIsAirborneTrick(int state) {
        return state >= SKIER_JUMP_SPREAD && state <= SKIER_JUMP_ROLL_RIGHT;
    }

    /* ---------------------------------------------------------------- */
    /* States belonging to the other object types                        */
    /* ---------------------------------------------------------------- */

    /* These continue the same numbering, because STATE_BITMAP is one flat
     * 64-entry table shared by every object on the hill. */
    public static final int OTHER_SKIER_STRAIGHT    = 0x16;
    public static final int OTHER_SKIER_LEFT        = 0x17;
    public static final int OTHER_SKIER_RIGHT       = 0x18;
    public static final int OTHER_SKIER_DOWN        = 0x19;
    public static final int OTHER_SKIER_FLATTENED   = 0x1A;
    public static final int OTHER_SKIER_STATE_COUNT = 5;
    public static final int OTHER_SKIER_FIRST_STATE = OTHER_SKIER_STRAIGHT;

    public static final int DOG_WALK_A     = 0x1B;
    public static final int DOG_WALK_B     = 0x1C;
    public static final int DOG_BARK       = 0x1D;
    public static final int DOG_BARK_AGAIN = 0x1E;

    public static final int SNOWBOARDER_LEFT        = 0x1F;
    public static final int SNOWBOARDER_RIGHT       = 0x20;
    public static final int SNOWBOARDER_AIRBORNE    = 0x21;
    public static final int SNOWBOARDER_CRASH_FIRST = 0x22;
    public static final int SNOWBOARDER_CRASH_LAST  = 0x26;
    public static final int SNOWBOARDER_STATE_COUNT = 8;
    public static final int SNOWBOARDER_FIRST_STATE = SNOWBOARDER_LEFT;

    public static final int LIFT_CHAIR_FIRST = 0x27;
    public static final int LIFT_CHAIR_LAST  = 0x29;

    public static final int YETI_APPEAR_FIRST = 0x2A;
    public static final int YETI_APPEAR_LAST  = 0x2B;
    public static final int YETI_RUN_FIRST    = 0x2C;
    public static final int YETI_RUN_LAST     = 0x2F;
    public static final int YETI_REACH_FIRST  = 0x30;
    public static final int YETI_REACH_LAST   = 0x31;
    public static final int YETI_EAT_FIRST    = 0x32;
    public static final int YETI_EAT_LAST     = 0x33;
    public static final int YETI_FULL_FIRST   = 0x34;
    public static final int YETI_FULL_LAST    = 0x37;

    public static final int FIRE_FIRST = 0x38;
    public static final int FIRE_LAST  = 0x3B;

    public static final int TREE_STILL      = 0x3C;
    public static final int TREE_WALK       = 0x3D;
    public static final int TREE_LEAN_LEFT  = 0x3E;
    public static final int TREE_LEAN_RIGHT = 0x3F;

    /* ---------------------------------------------------------------- */
    /* Object types                                                      */
    /* ---------------------------------------------------------------- */

    /* Type codes 0x00..0x11, as stored at offset +0x18 of the original's
     * object struct. Types below OBJECT_FIRST_STATIC animate and have their
     * own behaviour function; the rest are scenery picked from a bitmap id. */
    public static final int OBJECT_SKIER       = 0x00;
    public static final int OBJECT_OTHER_SKIER = 0x01;
    public static final int OBJECT_DOG         = 0x02;
    public static final int OBJECT_SNOWBOARDER = 0x03;
    public static final int OBJECT_LIFT_CHAIR  = 0x04;
    /* Four yetis, one guarding each edge of the world. */
    public static final int OBJECT_YETI_NORTH  = 0x05;
    public static final int OBJECT_YETI_SOUTH  = 0x06;
    public static final int OBJECT_YETI_WEST   = 0x07;
    public static final int OBJECT_YETI_EAST   = 0x08;
    public static final int OBJECT_FIRE        = 0x09;
    public static final int OBJECT_TREE_ANIM   = 0x0A;
    public static final int OBJECT_MOGULS      = 0x0B;
    public static final int OBJECT_SIGN        = 0x0C;
    public static final int OBJECT_TREE        = 0x0D;
    public static final int OBJECT_ROCK        = 0x0E;
    public static final int OBJECT_RAMP        = 0x0F;
    public static final int OBJECT_RAINBOW     = 0x10;
    public static final int OBJECT_BANNER      = 0x11;
    public static final int OBJECT_TYPE_COUNT  = 0x12;

    public static final int OBJECT_NONE         = OBJECT_TYPE_COUNT;
    public static final int OBJECT_FIRST_STATIC = OBJECT_MOGULS;

    /* Object flags. */
    public static final int OBJECT_FLAG_DEAD      = 1 << 0;
    public static final int OBJECT_FLAG_SOLID     = 1 << 1;
    public static final int OBJECT_FLAG_NO_SCROLL = 1 << 2;
    public static final int OBJECT_FLAG_FIXTURE   = 1 << 3;
    public static final int OBJECT_FLAG_FLAT      = 1 << 4;

    /* ---------------------------------------------------------------- */
    /* The yetis' territory                                              */
    /* ---------------------------------------------------------------- */

    /* Note the one-unit gap between the two tests; that asymmetry is in the
     * original and is preserved. */
    public static final int YETI_HOME_NORTH = -1999;
    public static final int YETI_HOME_SOUTH = 31999;
    public static final int YETI_HOME_WEST  = -15999;
    public static final int YETI_HOME_EAST  = 15999;

    public static final int YETI_TRESPASS_NORTH = -2000;
    public static final int YETI_TRESPASS_SOUTH = 32000;
    public static final int YETI_TRESPASS_WEST  = -16000;
    public static final int YETI_TRESPASS_EAST  = 16000;

    public static final int YETI_SPEED_SIDEWAYS = 16;
    public static final int YETI_SPEED_DOWNHILL = 26;
    public static final int YETI_SPEED_UPHILL   = -10;

    /* ---------------------------------------------------------------- */
    /* Courses                                                           */
    /* ---------------------------------------------------------------- */

    public static final int COURSE_NONE        = 0;
    public static final int COURSE_SLALOM      = 1;
    public static final int COURSE_TREE_SLALOM = 2;
    public static final int COURSE_FREESTYLE   = 3;

    public static final int COURSE_START_Y        = 0x280;
    public static final int SLALOM_FINISH_Y       = 0x21C0;
    public static final int LONG_COURSE_FINISH_Y  = 0x4100;
    public static final int SLALOM_LEFT           = -0x240;
    public static final int SLALOM_RIGHT          = -0x140;
    public static final int FREESTYLE_LEFT        = -0xA0;
    public static final int FREESTYLE_RIGHT       = 0xA0;
    public static final int TREE_SLALOM_LEFT      = 0x140;
    public static final int TREE_SLALOM_RIGHT     = 0x200;

    /** A missed slalom gate costs five seconds, as in the original. */
    public static final int MISSED_GATE_PENALTY_MS = 5000;

    /* ---------------------------------------------------------------- */
    /* Sounds                                                            */
    /* ---------------------------------------------------------------- */

    /* One per WAVE resource the original loads, named for the event. */
    public static final int SOUND_CRASH       = 0;
    public static final int SOUND_JUMP        = 1;
    public static final int SOUND_DOG         = 2;
    public static final int SOUND_LAND        = 3;
    public static final int SOUND_SNOWBOARDER = 4;
    public static final int SOUND_OTHER_SKIER = 5;
    public static final int SOUND_EATEN       = 6;
    public static final int SOUND_DOG_MESS    = 7;
    public static final int SOUND_YETI        = 8;
    public static final int SOUND_COUNT       = 9;
}
