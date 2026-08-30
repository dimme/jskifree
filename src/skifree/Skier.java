/* The player's state machine and physics.
 *
 * Steering is table driven: TURN_TABLE says which pose the left and right
 * keys move to, and MOTION_TABLE says how fast each pose travels.
 */
package skifree;

import java.awt.event.KeyEvent;

import static skifree.Consts.*;

public final class Skier {
    private Skier() {}

    private static final Game g = Game.g;

    /** The sideways shove that the skate keys add, and its ceiling. */
    private static final int SKATE_IMPULSE = 8;

    /** Pressing jump on flat ground costs this much downhill speed. */
    private static final int STANDING_JUMP_COST = 4;
    private static final int STANDING_JUMP_LIFT = 2;

    /** Climbing the hill happens at a fixed crawl. */
    private static final int CLIMB_SPEED = -4;

    /** Style earned per frame while a trick is held. The original's switch
     *  also had a -1 case for the skate and climb poses, but it is
     *  unreachable: the fixup above it rewrites those states to a hard turn
     *  first. Skating therefore costs nothing. */
    private static int styleRateForState(int state) {
        switch (state) {
        case SKIER_JUMP_TWIST:
            return 2;
        case SKIER_JUMP_FLIP:
        case SKIER_JUMP_TUCK:
            return 4;
        case SKIER_JUMP_ROLL_LEFT:
        case SKIER_JUMP_ROLL_RIGHT:
            return 8;
        default:
            return 0;
        }
    }

    public static void addStyle(int points) {
        /* The original only counted style while the freestyle run was live. */
        if (g.course == COURSE_FREESTYLE)
            g.stylePoints += points;
    }

    /* ---------------------------------------------------------------- */
    /* Input                                                             */
    /* ---------------------------------------------------------------- */

    /** Up cycles the aerial tricks forwards, Down cycles them back. */
    private static int nextTrickState(int state, boolean forwards) {
        if (forwards) {
            switch (state) {
            case SKIER_JUMP_SPREAD: return SKIER_JUMP_FLIP;
            case SKIER_JUMP_LEFT:   return SKIER_JUMP_ROLL_LEFT;
            case SKIER_JUMP_RIGHT:  return SKIER_JUMP_ROLL_RIGHT;
            case SKIER_JUMP_FLIP:   return SKIER_JUMP_TUCK;
            case SKIER_JUMP_TUCK:   return SKIER_JUMP_SPREAD;
            default:                return state;
            }
        }
        switch (state) {
        case SKIER_JUMP_SPREAD:     return SKIER_JUMP_TUCK;
        case SKIER_JUMP_FLIP:       return SKIER_JUMP_SPREAD;
        case SKIER_JUMP_TUCK:       return SKIER_JUMP_FLIP;
        case SKIER_JUMP_ROLL_LEFT:  return SKIER_JUMP_LEFT;
        case SKIER_JUMP_ROLL_RIGHT: return SKIER_JUMP_RIGHT;
        default:                    return state;
        }
    }

    /** A key press, given as a KeyEvent virtual key code. */
    public static void keyDown(int key) {
        GameObject skier = g.player;
        if (skier == null)
            return;

        int state = skier.state;
        boolean airborne = skier.height > 0;

        /* A crashed skier ignores the controls until they have stopped and
         * sat up, exactly as in the original. */
        if (skierIsCrashed(state))
            return;

        switch (key) {
        /* --- fixed headings, only available with the skis on the snow --- */
        case KeyEvent.VK_HOME: case KeyEvent.VK_NUMPAD7:
            if (!airborne) state = SKIER_LEFT_HARD;
            break;
        case KeyEvent.VK_END: case KeyEvent.VK_NUMPAD1:
            if (!airborne) state = SKIER_LEFT_SLIGHT;
            break;
        case KeyEvent.VK_PAGE_UP: case KeyEvent.VK_NUMPAD9:
            if (!airborne) state = SKIER_RIGHT_HARD;
            break;
        case KeyEvent.VK_PAGE_DOWN: case KeyEvent.VK_NUMPAD3:
            if (!airborne) state = SKIER_RIGHT_SLIGHT;
            break;

        /* --- steering --- */
        case KeyEvent.VK_LEFT: case KeyEvent.VK_KP_LEFT: case KeyEvent.VK_NUMPAD4:
            state = Tables.TURN_TABLE[state][0];
            if (state == SKIER_SKATE_LEFT) {
                int pushed = skier.velocityX - SKATE_IMPULSE;
                skier.velocityX = pushed < -SKATE_IMPULSE ? -SKATE_IMPULSE : pushed;
            }
            break;
        case KeyEvent.VK_RIGHT: case KeyEvent.VK_KP_RIGHT: case KeyEvent.VK_NUMPAD6:
            state = Tables.TURN_TABLE[state][1];
            if (state == SKIER_SKATE_RIGHT) {
                int pushed = skier.velocityX + SKATE_IMPULSE;
                skier.velocityX = pushed > SKATE_IMPULSE ? SKATE_IMPULSE : pushed;
            }
            break;

        /* --- up: herringbone back up the hill, or cycle a trick --- */
        case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP: case KeyEvent.VK_NUMPAD8:
            if (state == SKIER_LEFT_HARD || state == SKIER_SKATE_LEFT ||
                state == SKIER_SITTING) {
                if (skier.velocityY == 0) {
                    state = SKIER_CLIMB_LEFT;
                    skier.velocityY = CLIMB_SPEED;
                }
            } else if (state == SKIER_RIGHT_HARD || state == SKIER_SKATE_RIGHT) {
                if (skier.velocityY == 0) {
                    state = SKIER_CLIMB_RIGHT;
                    skier.velocityY = CLIMB_SPEED;
                }
            } else if (skierIsAirborneTrick(state)) {
                state = nextTrickState(state, true);
            }
            break;

        /* --- down: point down the fall line, or cycle a trick back --- */
        case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN: case KeyEvent.VK_NUMPAD2:
            if (!airborne)
                state = SKIER_DOWNHILL;
            else
                state = nextTrickState(state, false);
            break;

        /* --- jump --- */
        case KeyEvent.VK_INSERT: case KeyEvent.VK_NUMPAD0: case KeyEvent.VK_SPACE:
            if (!airborne) {
                skier.velocityZ = STANDING_JUMP_LIFT;
                if (skier.velocityY > STANDING_JUMP_COST)
                    skier.velocityY -= STANDING_JUMP_COST;
                state = SKIER_JUMP_SPREAD;
            }
            break;

        default:
            return;
        }

        World.setState(skier, state);
    }

    /* ---------------------------------------------------------------- */
    /* Mouse control                                                     */
    /* ---------------------------------------------------------------- */

    /** On the snow, the heading is chosen from where the pointer sits
     *  relative to the skier. The original compared four times the vertical
     *  offset against the horizontal one, so the bands are slopes. */
    private static int headingFromPointer(int offsetX, int offsetY) {
        if (offsetY > 0) {
            if (offsetX == 0)
                return SKIER_DOWNHILL;
            int slope = (offsetY * 4) / offsetX;
            if (slope < -11) return SKIER_DOWNHILL;
            if (slope <  -5) return SKIER_LEFT_SLIGHT;
            if (slope <  -2) return SKIER_LEFT_MEDIUM;
            if (slope <   0) return SKIER_LEFT_HARD;
            if (slope >  11) return SKIER_DOWNHILL;
            if (slope >   5) return SKIER_RIGHT_SLIGHT;
            if (slope >   2) return SKIER_RIGHT_MEDIUM;
            if (slope >   0) return SKIER_RIGHT_HARD;
        }
        /* Pointer level with the skier or above: turn as hard as possible. */
        return offsetX >= 0 ? SKIER_RIGHT_HARD : SKIER_LEFT_HARD;
    }

    /** In the air the pointer picks a trick by quadrant instead. */
    private static int trickFromPointer(int offsetX, int offsetY) {
        if (offsetX >= 0) {
            if (offsetY < 0)
                return (-offsetX != offsetY && offsetX <= -offsetY)
                       ? SKIER_JUMP_TWIST : SKIER_JUMP_RIGHT;
            return offsetY <= offsetX ? SKIER_JUMP_LEFT : SKIER_JUMP_SPREAD;
        }
        if (offsetY < 0)
            return offsetX <= offsetY ? SKIER_JUMP_LEFT : SKIER_JUMP_TWIST;
        return offsetX <= -offsetY ? SKIER_JUMP_LEFT : SKIER_JUMP_SPREAD;
    }

    public static void pointerMoved(int offsetX, int offsetY) {
        GameObject skier = g.player;
        if (skier == null || skierIsCrashed(skier.state))
            return;
        World.setState(skier, skier.height == 0
                       ? headingFromPointer(offsetX, offsetY)
                       : trickFromPointer(offsetX, offsetY));
    }

    /** Either mouse button jumps from the snow, or advances the trick in
     *  the air. The click jump is stronger than the keyboard's and costs no
     *  downhill speed; the original really did favour the mouse here. */
    public static void pointerClicked() {
        GameObject skier = g.player;
        if (skier == null)
            return;
        int state = skier.state;
        if (state == SKIER_CRASHED)
            return;

        if (skier.height == 0) {
            skier.velocityZ = 4;
            state = SKIER_JUMP_SPREAD;
        } else if (state != SKIER_TANGLED) {
            state = nextTrickState(state, true);
        }
        World.setState(skier, state);
    }

    /** Undocumented character keys, straight from the original's WM_CHAR
     *  handler. Case-sensitive: the speed toggle is lowercase 'f' only, and
     *  the nudge keys use opposite cases for opposite directions. */
    public static void hiddenKey(char character) {
        GameObject skier = g.player;

        if (character == 'f') {
            g.doubleSpeed = !g.doubleSpeed;
            return;
        }
        if (skier == null)
            return;

        switch (character) {
        case 'x': skier.x = worldWrap(skier.x + 2); break;
        case 'X': skier.x = worldWrap(skier.x - 2); break;
        case 'y': skier.y = worldWrap(skier.y + 2); break;
        case 'Y': skier.y = worldWrap(skier.y - 2); break;
        default: break;
        }
    }

    /* ---------------------------------------------------------------- */
    /* Physics                                                           */
    /* ---------------------------------------------------------------- */

    /** A crashed skier slides to a halt and then sits up. */
    private static void updateCrashed(GameObject skier) {
        if (skier.velocityX == 0 && skier.velocityY == 0) {
            World.setState(skier, SKIER_SITTING);
            return;
        }
        skier.velocityX -= Motion.signOf(skier.velocityX);
        skier.velocityY -= Motion.signOf(skier.velocityY);
    }

    public static void update(GameObject skier) {
        int state = skier.state;

        if (state == SKIER_CRASHED) {
            updateCrashed(skier);
            return;
        }

        Motion.integrate(skier);
        Motion.apply(skier, Tables.MOTION_TABLE[state]);

        /* The skate and climb poses are momentary: one frame of shove and
         * the skier settles back into the corresponding hard turn. */
        switch (state) {
        case SKIER_SKATE_LEFT:
        case SKIER_CLIMB_LEFT:
            state = SKIER_LEFT_HARD;
            break;
        case SKIER_SKATE_RIGHT:
        case SKIER_CLIMB_RIGHT:
            state = SKIER_RIGHT_HARD;
            break;
        default:
            /* Touching down resolves whatever trick was being held. Only the
             * spread eagle and the two side jumps land cleanly. The
             * original's -64 style / crash sound branch here is dead code,
             * because LANDING_STATE never yields 0x11. */
            if (skierIsAirborneTrick(state) && skier.height == 0) {
                state = Tables.LANDING_STATE[state];
                Sound.play(SOUND_LAND);
            }
            break;
        }

        World.setState(skier, state);
        addStyle(styleRateForState(state));
    }
}
