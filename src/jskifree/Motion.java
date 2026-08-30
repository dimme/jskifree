/* The physics step shared by the player, the other skier and the
 * snowboarder.
 *
 * Every moving object in the original ran through the same pair of
 * routines: one integrated the position and applied gravity, the other
 * eased the velocities towards the targets held in a motion record.
 */
package jskifree;

import static jskifree.Consts.worldWrap;

public final class Motion {
    private Motion() {}

    /** Braking is fixed; only acceleration is per-state. */
    private static final int BRAKING_RATE = 2;

    static int signOf(int value) {
        return value < 0 ? -1 : (value > 0 ? 1 : 0);
    }

    /** Move current towards target, gaining acceleration per frame when
     *  speeding up and BRAKING_RATE when slowing down, never overshooting. */
    private static int approach(int current, int target, int acceleration) {
        int stepped;
        if (target < current) {
            stepped = current - BRAKING_RATE;
            return stepped < target ? target : stepped;
        }
        stepped = current + acceleration;
        return target < stepped ? target : stepped;
    }

    /** Ease the velocities towards the targets described by a motion record. */
    public static void apply(GameObject object, Tables.Motion motion) {
        int direction = motion.lateralDirection;

        /* A record with no direction of its own keeps drifting whichever way
         * the object is already going. */
        if (direction == 0)
            direction = signOf(object.velocityX);

        /* Working in the frame of that direction lets one comparison serve
         * both left and right. */
        int sideways = (short) (direction * object.velocityX);
        int downhillSpeed = object.velocityY > 0 ? object.velocityY : 0;

        /* Sideways drift is proportional to how fast the object is already
         * travelling: you cannot carve across the hill from a standstill. */
        int targetSideways = (short) ((motion.lateralFactor * downhillSpeed) / 2);
        targetSideways = approach(sideways, targetSideways, motion.lateralAccel);

        object.velocityY = approach(object.velocityY, motion.downhillTarget,
                                    motion.downhillAccel);
        object.velocityX = (short) (direction * targetSideways);
    }

    /** Advance a position by its velocity, applying gravity to anything that
     *  is off the ground. */
    public static void integrate(GameObject object) {
        /* With the 'f' cheat on, the original added each velocity a second
         * time before deciding anything else, so travel doubles but gravity
         * is still only one unit per frame. */
        int steps = Game.g.doubleSpeed ? 2 : 1;
        int newHeight = object.height + steps * object.velocityZ;

        object.x = worldWrap(object.x + steps * object.velocityX);
        object.y = worldWrap(object.y + steps * object.velocityY);

        if (newHeight > 0) {
            object.height = newHeight;
            object.velocityZ--;                  /* one unit of gravity */
        } else {
            object.height = 0;
            object.velocityZ = 0;
        }
    }
}
