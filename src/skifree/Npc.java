/* Everything on the hill that moves but is not the player.
 *
 * The other skier, the dog, the snowboarder, a burning tree and the trees
 * that shuffle about when nobody is looking each had their own small
 * behaviour function in the original; those are reproduced here, including
 * the probabilities. The yeti is the exception and is called out below.
 */
package skifree;

import static skifree.Consts.*;

public final class Npc {
    private Npc() {}

    private static final Game g = Game.g;

    /* ---------------------------------------------------------------- */
    /* The other skier                                                   */
    /* ---------------------------------------------------------------- */

    /** Wanders between three poses, changing its mind about one frame in
     *  twelve. The knocked-over poses are only entered from the collision
     *  code, so they simply hold. */
    private static void updateOtherSkier(GameObject skier) {
        int state = skier.state;

        if (state > OTHER_SKIER_RIGHT)
            return;                              /* knocked over; stay down */

        Motion.integrate(skier);
        Motion.apply(skier, Tables.OTHER_SKIER_MOTION[state - OTHER_SKIER_FIRST_STATE]);

        if (World.random(12) == 0) {
            switch (World.random(3)) {
            case 0:  state = OTHER_SKIER_STRAIGHT; break;
            case 1:  state = OTHER_SKIER_LEFT;     break;
            default: state = OTHER_SKIER_RIGHT;    break;
            }
        }
        World.setState(skier, state);
    }

    /* ---------------------------------------------------------------- */
    /* The dog                                                           */
    /* ---------------------------------------------------------------- */

    /** Trots about in a two-frame walk cycle. Once barking it mostly keeps
     *  barking, and one time in a hundred it leaves something behind. */
    private static void updateDog(GameObject dog) {
        switch (dog.state) {
        case DOG_WALK_A:
            dog.velocityY = World.random(3) - 1;
            Motion.integrate(dog);
            World.setState(dog, DOG_WALK_B);
            return;

        case DOG_WALK_B:
            dog.velocityX = 4;
            Motion.integrate(dog);
            World.setState(dog, DOG_WALK_A);
            return;

        case DOG_BARK:
            dog.velocityX = 0;
            dog.velocityY = 0;
            Motion.integrate(dog);
            World.setState(dog, World.random(32) == 0 ? DOG_WALK_A : DOG_BARK_AGAIN);
            return;

        case DOG_BARK_AGAIN:
            if (World.random(100) != 0) {
                Motion.integrate(dog);
                World.setState(dog, DOG_BARK);
                return;
            }
            World.addBanner(BITMAP_DOG_MESS, dog.x - 4, dog.y - 2);
            Sound.play(SOUND_DOG_MESS);          /* slot 8, not the bark */
            Motion.integrate(dog);
            World.setState(dog, DOG_WALK_A);
            return;

        default:
            Motion.integrate(dog);
            return;
        }
    }

    /* ---------------------------------------------------------------- */
    /* The snowboarder                                                   */
    /* ---------------------------------------------------------------- */

    /** Cuts left and right across the hill, swapping direction about one
     *  frame in ten, and runs through a five-frame tumble when knocked over. */
    private static void updateSnowboarder(GameObject rider) {
        int state = rider.state;

        Motion.integrate(rider);
        Motion.apply(rider, Tables.SNOWBOARDER_MOTION[state - SNOWBOARDER_FIRST_STATE]);

        if (state == SNOWBOARDER_LEFT) {
            if (World.random(10) == 0)
                state = SNOWBOARDER_RIGHT;
        } else if (state == SNOWBOARDER_RIGHT) {
            if (World.random(10) == 0)
                state = SNOWBOARDER_LEFT;
        } else if (state == SNOWBOARDER_AIRBORNE) {
            if (rider.height == 0)
                state = SNOWBOARDER_RIGHT;
        } else {
            state++;                                   /* advance the tumble */
            if (state > SNOWBOARDER_CRASH_LAST)
                state = SNOWBOARDER_RIGHT;
        }
        World.setState(rider, state);
    }

    /* ---------------------------------------------------------------- */
    /* A burning tree                                                    */
    /* ---------------------------------------------------------------- */

    private static void updateFire(GameObject fire) {
        int state = fire.state + 1;
        if (state > FIRE_LAST)
            state = FIRE_FIRST;
        World.setState(fire, state);
    }

    /* ---------------------------------------------------------------- */
    /* The walking trees                                                 */
    /* ---------------------------------------------------------------- */

    /** One tree in a hundred decides to take a step sideways, keeps going
     *  for a while, then settles down again. */
    private static void updateWalkingTree(GameObject tree) {
        switch (tree.state) {
        case TREE_STILL:
            if (World.random(100) == 0) {
                tree.velocityX = World.random(2) * 2 - 1;
                Motion.integrate(tree);
                World.setState(tree, TREE_WALK);
                return;
            }
            break;

        case TREE_WALK:
            if (World.random(10) == 0) {
                tree.velocityX = 0;
                Motion.integrate(tree);
                World.setState(tree, TREE_STILL);
                return;
            }
            Motion.integrate(tree);
            World.setState(tree, tree.velocityX < 0 ? TREE_LEAN_LEFT : TREE_LEAN_RIGHT);
            return;

        case TREE_LEAN_LEFT:
        case TREE_LEAN_RIGHT:
            Motion.integrate(tree);
            World.setState(tree, TREE_WALK);
            return;

        default:
            break;
        }
        Motion.integrate(tree);
    }

    /* ---------------------------------------------------------------- */
    /* Scheduled objects: the yetis and the chairlift                    */
    /* ---------------------------------------------------------------- */

    /** The original held these on separate schedules from the rest of the
     *  hill, advanced by their own integrator, not the one the 'f' cheat
     *  doubles. So with double speed on you really can outrun the yeti. */
    private static void integrateScheduled(GameObject object) {
        object.x = worldWrap(object.x + object.velocityX);
        object.y = worldWrap(object.y + object.velocityY);
        object.height += object.velocityZ;
    }

    /* ---------------------------------------------------------------- */
    /* The chairlift                                                     */
    /* ---------------------------------------------------------------- */

    private static final int LIFT_TOP_Y        = -1024;
    private static final int LIFT_BOTTOM_Y     = 23551;
    private static final int LIFT_TURN_TOP     = -1023;
    private static final int LIFT_TURN_BOTTOM  = 23551;
    private static final int LIFT_UP_X         = -112;
    private static final int LIFT_DOWN_X       = -144;
    private static final int LIFT_SPEED        = 2;
    private static final int LIFT_CHAIR_HEIGHT = 32;

    /** Is this chair inside the area where the original would have given
     *  its schedule record a sprite? */
    private static boolean chairIsVisible(GameObject chair) {
        return chair.y >= g.viewTop - SPAWN_MARGIN &&
               chair.y <= g.viewBottom + SPAWN_MARGIN;
    }

    /** Chairs ride up the left-hand side of the hill loaded and come back
     *  down empty. About one frame in a thousand a rider falls out of a
     *  chair on the way up and lands on the slope as a snowboarder. */
    private static void updateLiftChair(GameObject chair) {
        integrateScheduled(chair);

        if (chair.y < LIFT_TURN_TOP) {
            World.setState(chair, LIFT_CHAIR_LAST);      /* empty, heading down */
            chair.velocityY = LIFT_SPEED;
            chair.x = LIFT_DOWN_X;
            return;
        }
        if (chair.y > LIFT_TURN_BOTTOM) {
            World.setState(chair, LIFT_CHAIR_FIRST);     /* loaded, heading up */
            chair.velocityY = -LIFT_SPEED;
            chair.x = LIFT_UP_X;
            return;
        }
        /* Only a chair that is actually on screen can lose a rider. */
        if (chair.state == LIFT_CHAIR_FIRST && chairIsVisible(chair) &&
            World.random(1000) == 0) {
            GameObject faller = World.add(OBJECT_SNOWBOARDER, SNOWBOARDER_AIRBORNE,
                                          chair.x, chair.y);
            if (faller != null) {
                faller.height = chair.height;
                World.setState(chair, LIFT_CHAIR_FIRST + 1);   /* a seat spare */
            }
        }
    }

    /* ---------------------------------------------------------------- */
    /* The yeti                                                          */
    /* ---------------------------------------------------------------- */

    /** Has the yeti settled back on its own patch? */
    private static boolean yetiIsHome(int yeti, int x, int y) {
        switch (yeti) {
        case OBJECT_YETI_NORTH: return y < YETI_HOME_NORTH;
        case OBJECT_YETI_SOUTH: return y > YETI_HOME_SOUTH;
        case OBJECT_YETI_WEST:  return x < YETI_HOME_WEST;
        default:                return x > YETI_HOME_EAST;
        }
    }

    /** Has the skier strayed far enough for this one to take an interest? */
    private static boolean skierHasTrespassed(int yeti, int x, int y) {
        switch (yeti) {
        case OBJECT_YETI_NORTH: return y < YETI_TRESPASS_NORTH;
        case OBJECT_YETI_SOUTH: return y > YETI_TRESPASS_SOUTH;
        case OBJECT_YETI_WEST:  return x < YETI_TRESPASS_WEST;
        default:                return x > YETI_TRESPASS_EAST;
        }
    }

    private static int clamp(int value, int low, int high) {
        return value < low ? low : (value > high ? high : value);
    }

    /** Six frames on a real-time schedule: grab and chew, lift, swallow,
     *  then stand there looking pleased before going back on watch. */
    private static void updateEating(GameObject yeti) {
        long elapsed = g.nowMs - yeti.stateEnteredMs;
        int state = yeti.state;

        switch (state) {
        case 0x32: state = 0x33; break;
        case 0x33: state = elapsed > 499 ? 0x34 : 0x32; break;
        case 0x34: if (elapsed > 700)  state = 0x35; break;
        case 0x35: if (elapsed > 1000) state = 0x36; break;
        case 0x36: state = 0x37; break;
        case 0x37: state = elapsed > 2999 ? YETI_APPEAR_FIRST : 0x36; break;
        default:   break;
        }
        World.setState(yeti, state);
    }

    public static void startEating(GameObject yeti, GameObject skier) {
        yeti.velocityX = 0;
        yeti.velocityY = 0;
        yeti.stateEnteredMs = g.nowMs;
        World.setState(yeti, YETI_EAT_FIRST);
        World.kill(skier);
        g.eaten = true;
        Sound.play(SOUND_EATEN);
    }

    private static void updateYeti(GameObject yeti) {
        GameObject skier = g.player;
        int towardX, towardY;
        int state = yeti.state;

        /* The schedule was advanced before the behaviour ran, so last
         * frame's velocity is applied first and this frame's chosen after. */
        integrateScheduled(yeti);

        /* Then gravity. While off the ground the pose is held. */
        if (yeti.height < 1) {
            yeti.velocityZ = 0;
            yeti.height = 0;
        } else {
            yeti.velocityZ--;
        }
        if (yeti.height != 0)
            return;

        if (state >= YETI_EAT_FIRST && state <= YETI_FULL_LAST) {
            updateEating(yeti);
            return;
        }

        if (!yetiIsHome(yeti.type, yeti.x, yeti.y)) {
            /* Wandered off its patch; walk back. */
            towardX = 0;
            towardY = 0;
            switch (yeti.type) {
            case OBJECT_YETI_NORTH: towardY = YETI_SPEED_UPHILL;    break;
            case OBJECT_YETI_SOUTH: towardY = YETI_SPEED_DOWNHILL;  break;
            case OBJECT_YETI_WEST:  towardX = -YETI_SPEED_SIDEWAYS; break;
            default:                towardX = YETI_SPEED_SIDEWAYS;  break;
            }
        } else if (skier != null && skierHasTrespassed(yeti.type, skier.x, skier.y)) {
            int deltaX = skier.x - yeti.x;
            int deltaY = skier.y - yeti.y;

            /* More than a screen away, it simply steps to the edge of the
             * view. This is why the yeti seems to appear out of nowhere. */
            if (deltaX > g.viewWidth)
                yeti.x = worldWrap(skier.x - g.viewWidth);
            else if (deltaX < -g.viewWidth)
                yeti.x = worldWrap(skier.x + g.viewWidth);
            if (deltaY > g.viewHeight)
                yeti.y = worldWrap(skier.y - g.viewHeight);
            else if (deltaY < -g.viewHeight)
                yeti.y = worldWrap(skier.y + g.viewHeight);

            towardX = clamp(deltaX, -YETI_SPEED_SIDEWAYS, YETI_SPEED_SIDEWAYS);
            towardY = clamp(deltaY, YETI_SPEED_UPHILL, YETI_SPEED_DOWNHILL);
            /* The original re-triggered its growl on every frame of the
             * chase; Sound.play ignores a request for a clip already playing. */
            Sound.play(SOUND_YETI);
        } else {
            towardX = 0;
            towardY = 0;
        }

        /* Any movement at all puts a one-unit bounce in its stride. */
        if (towardX != 0 || towardY != 0)
            yeti.velocityZ = 1;
        yeti.velocityX = towardX;
        yeti.velocityY = towardY;

        if (towardY < 0)
            state = (state == 0x30) ? 0x31 : 0x30;              /* running uphill */
        else if (towardX < 0)
            state = (state == YETI_RUN_FIRST) ? 0x2D : YETI_RUN_FIRST;
        else if (towardX < 1 && towardY < 1) {
            if (World.random(10) == 0) {                        /* an idle hop */
                yeti.velocityZ = 4;
                state = YETI_APPEAR_LAST;
            } else {
                state = YETI_APPEAR_FIRST;
            }
        } else {
            state = (state == 0x2E) ? 0x2F : 0x2E;              /* running at you */
        }

        World.setState(yeti, state);
    }

    /** Place the four yetis on their patches and thread the chairlift up
     *  the left-hand side, at the coordinates the original used. */
    public static void placeYetisAndLift() {
        int[][] yetis = {
            { OBJECT_YETI_WEST,  -16060,     0 },
            { OBJECT_YETI_EAST,   16060,     0 },
            { OBJECT_YETI_NORTH,      0, -2060 },
            { OBJECT_YETI_SOUTH,      0, 32060 },
        };
        for (int[] yeti : yetis)
            World.addScheduled(yeti[0], YETI_APPEAR_FIRST, yeti[1], yeti[2]);

        /* Slots every 2048 from -1024 up to and including 0x5C00. */
        for (int y = LIFT_TOP_Y; y <= 0x5C00; y += 2048) {
            GameObject chair;
            if (y > LIFT_TOP_Y) {
                chair = World.addScheduled(OBJECT_LIFT_CHAIR, LIFT_CHAIR_FIRST, LIFT_UP_X, y);
                if (chair != null) {
                    chair.velocityY = -LIFT_SPEED;
                    chair.height = LIFT_CHAIR_HEIGHT;
                }
            }
            if (y < LIFT_BOTTOM_Y) {
                chair = World.addScheduled(OBJECT_LIFT_CHAIR, LIFT_CHAIR_LAST, LIFT_DOWN_X, y);
                if (chair != null) {
                    chair.velocityY = LIFT_SPEED;
                    chair.height = LIFT_CHAIR_HEIGHT;
                }
            }
        }
    }

    public static void updateScheduled(GameObject object) {
        switch (object.type) {
        case OBJECT_LIFT_CHAIR: updateLiftChair(object); break;
        case OBJECT_YETI_NORTH:
        case OBJECT_YETI_SOUTH:
        case OBJECT_YETI_WEST:
        case OBJECT_YETI_EAST:  updateYeti(object);      break;
        default:                                          break;
        }
    }

    /* ---------------------------------------------------------------- */
    /* Dispatch                                                          */
    /* ---------------------------------------------------------------- */

    public static void update(GameObject object) {
        switch (object.type) {
        case OBJECT_OTHER_SKIER: updateOtherSkier(object);  break;
        case OBJECT_DOG:         updateDog(object);         break;
        case OBJECT_SNOWBOARDER: updateSnowboarder(object); break;
        case OBJECT_FIRE:        updateFire(object);        break;
        case OBJECT_TREE_ANIM:   updateWalkingTree(object); break;
        case OBJECT_LIFT_CHAIR:
        case OBJECT_YETI_NORTH:
        case OBJECT_YETI_SOUTH:
        case OBJECT_YETI_WEST:
        case OBJECT_YETI_EAST:   updateScheduled(object);   break;
        default:                                            break;
        }
    }
}
