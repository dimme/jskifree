/* Collision handling.
 *
 * The original tested every pair of objects each frame and ran an
 * asymmetric handler: first "what does A do to B", then, if both survived,
 * "what does B do to A". The player's rules are reproduced below, including
 * the ones nobody would guess, such as skiing over a bare tree setting it
 * alight, or the extra hundred style points for clearing the smallest bump.
 */
package jskifree;

import static jskifree.Consts.*;

public final class Collide {
    private Collide() {}

    private static final Game g = Game.g;

    /* Style awards and penalties, all from the original's handler. */
    private static final int STYLE_CLEARED_OBSTACLE   = 6;
    private static final int STYLE_HIT_SOMETHING      = -0x20;
    private static final int STYLE_RODE_THE_DOG_MESS  = -0x10;
    private static final int STYLE_CLEARED_TINY_BUMP  = 100;
    private static final int STYLE_JUMPED_A_RAMP      = 1;
    private static final int STYLE_SET_A_TREE_ALIGHT  = 0x10;
    private static final int STYLE_CLEARED_A_FIRE     = 1000;
    private static final int STYLE_FLATTENED_A_SKIER  = 0x14;
    private static final int STYLE_STARTLED_A_DOG     = 3;

    /** Height of the top of an object, measured from the snow. */
    private static int objectTopHeight(GameObject object) {
        return Sprites.height(object.bitmap) + object.height;
    }

    /** True when the two objects swapped places down the hill this frame.
     *  That is what stops a crashed skier lying against a tree from being
     *  re-crashed on every frame for as long as the two rectangles touch. */
    private static boolean crossedThisFrame(GameObject a, GameObject b) {
        int now = a.y - b.y;
        int previous = a.previousY - b.previousY;
        return Motion.signOf(now) != Motion.signOf(previous);
    }

    private static final int[] boundsA = new int[4];
    private static final int[] boundsB = new int[4];

    private static boolean rectanglesOverlap(GameObject a, GameObject b) {
        World.bounds(a, boundsA);
        World.bounds(b, boundsB);
        return boundsA[0] < boundsB[2] && boundsB[0] < boundsA[2] &&
               boundsA[1] < boundsB[3] && boundsB[1] < boundsA[3];
    }

    /** Anything the skier can plough into rather than glide past. */
    private static boolean isSolidObstacle(int type) {
        switch (type) {
        case OBJECT_OTHER_SKIER:
        case OBJECT_SNOWBOARDER:
        case OBJECT_LIFT_CHAIR:
        case OBJECT_FIRE:
        case OBJECT_TREE_ANIM:
        case OBJECT_TREE:
            return true;
        default:
            return false;
        }
    }

    /* ---------------------------------------------------------------- */
    /* The player running into something                                 */
    /* ---------------------------------------------------------------- */

    /** Taking off from the snow: forward speed becomes height and the
     *  skier straightens into the spread-eagle pose. */
    private static void launchOff(GameObject skier, int upwardSpeed) {
        skier.velocityZ = upwardSpeed;
        Skier.addStyle(STYLE_JUMPED_A_RAMP);
        Sound.play(SOUND_JUMP);
        World.setState(skier, SKIER_JUMP_SPREAD);
    }

    /** Clipping the lip of something while already airborne. Same boost,
     *  but the pose is deliberately left alone so the trick being held is
     *  not cancelled. */
    private static void boostOffLip(GameObject skier, int upwardSpeed) {
        skier.velocityZ = upwardSpeed;
        Skier.addStyle(STYLE_JUMPED_A_RAMP);
        Sound.play(SOUND_JUMP);
    }

    /** The full wipe-out: pose depends on whether the skier was on the snow. */
    private static void wipeOut(GameObject skier, GameObject obstacle) {
        boolean grounded = skier.height == 0 && skier.velocityZ == 0;
        int state = grounded ? SKIER_CRASHED : SKIER_TANGLED;

        /* Hitting a bare tree hard enough sets it on fire. */
        if (!grounded && obstacle.bitmap == BITMAP_TREE_BARE) {
            obstacle.type = OBJECT_FIRE;
            World.setState(obstacle, FIRE_FIRST);
            Skier.addStyle(STYLE_SET_A_TREE_ALIGHT);
            World.setState(skier, SKIER_TANGLED);
            return;
        }

        /* Clipping a stump while sliding backwards knocks it down to a nub.
         * The skier still goes down; only the penalty and sound are skipped. */
        if (skier.velocityY < 0 && obstacle.bitmap == BITMAP_STUMP) {
            World.setBitmap(obstacle, BITMAP_MOGUL_SMALL);
            World.setState(skier, state);
            return;
        }

        Skier.addStyle(STYLE_HIT_SOMETHING);
        Sound.play(SOUND_CRASH);
        World.setState(skier, state);
    }

    private static void playerHits(GameObject skier, GameObject obstacle) {
        boolean crossed = crossedThisFrame(skier, obstacle);
        int obstacleTop = objectTopHeight(obstacle);
        /* Cleared it by going over the top, or by passing underneath, the
         * second case is what lets the skier ride below a chairlift chair. */
        boolean cleared = skier.height > obstacleTop ||
                          Sprites.height(skier.bitmap) + skier.height < obstacle.height;

        /* Already wrapped around a tree; nothing more can go wrong this frame. */
        if (skier.state == SKIER_TANGLED)
            return;

        switch (obstacle.type) {
        /* --- things that merely slow you down --- */
        case OBJECT_DOG:
        case OBJECT_SIGN:
        case OBJECT_BANNER:
            if (crossed)
                skier.velocityY /= 2;
            if (obstacle.bitmap == BITMAP_DOG_MESS)
                Skier.addStyle(STYLE_RODE_THE_DOG_MESS);
            return;

        /* --- moguls give a little hop if you take them straight on --- */
        case OBJECT_MOGULS:
            if (skier.state == SKIER_DOWNHILL) {
                skier.velocityZ = 1;
                if (skier.velocityY > 4)
                    skier.velocityY /= 2;
                World.setState(skier, SKIER_JUMP_SPREAD);
            }
            return;

        /* --- rocks and stumps: clear them, or trip over them --- */
        case OBJECT_ROCK:
            if (skier.height > 0) {
                if (cleared) {
                    /* The smallest bump on the hill is worth a lot of style
                     * to anyone who spots it and jumps it. */
                    if (obstacle.bitmap == BITMAP_MOGUL_SMALL) {
                        World.kill(obstacle);
                        Skier.addStyle(STYLE_CLEARED_TINY_BUMP);
                    }
                    return;
                }
                if (!crossed)
                    return;
                /* Caught the lip on the way past: it acts like a ramp. */
                boostOffLip(skier, skier.velocityY / 2);
                return;
            }
            if (crossed)
                wipeOut(skier, obstacle);
            return;

        /* --- ramps --- */
        case OBJECT_RAMP:
            if (skier.height <= 0) {
                launchOff(skier, 4);
                return;
            }
            if (obstacleTop > skier.height)
                boostOffLip(skier, skier.velocityY / 2);
            return;

        /* --- the rainbow bar throws you a long way --- */
        case OBJECT_RAINBOW:
            if (crossed && skier.height < obstacleTop / 2 && skier.velocityY > 0)
                launchOff(skier, skier.velocityY);
            return;

        /* --- anything you can actually crash into --- */
        default:
            if (!isSolidObstacle(obstacle.type))
                return;

            if (cleared) {
                /* Jumping a burning tree is the single biggest score in the
                 * game, and it puts the fire out. */
                if (obstacle.type == OBJECT_FIRE) {
                    Skier.addStyle(STYLE_CLEARED_A_FIRE);
                    obstacle.type = OBJECT_TREE;
                    World.setBitmap(obstacle, BITMAP_TREE_BARE);
                    return;
                }
                Skier.addStyle(STYLE_CLEARED_OBSTACLE);
                return;
            }
            if (!crossed)
                return;

            /* A tree caught well off to one side only clips you. */
            if (obstacle.type == OBJECT_TREE) {
                int widest = Math.max(Sprites.width(obstacle.bitmap),
                                      Sprites.width(skier.bitmap));
                if (Math.abs(skier.x - obstacle.x) > widest / 2) {
                    skier.velocityY /= 2;
                    return;
                }
            }
            wipeOut(skier, obstacle);
            return;
        }
    }

    /* ---------------------------------------------------------------- */
    /* Something running into the player                                 */
    /* ---------------------------------------------------------------- */

    private static void npcHits(GameObject npc, GameObject other) {
        boolean hitByPlayer = other == g.player;

        switch (npc.type) {
        case OBJECT_OTHER_SKIER:
            if (npc.state > OTHER_SKIER_RIGHT)
                return;                              /* already flattened */
            if (hitByPlayer)
                Skier.addStyle(STYLE_FLATTENED_A_SKIER);
            World.setState(npc, other.height > 0 ? OTHER_SKIER_FLATTENED : OTHER_SKIER_DOWN);
            Sound.play(SOUND_OTHER_SKIER);
            return;

        case OBJECT_DOG:
            if (npc.state >= DOG_BARK)
                return;
            if (other.velocityX == 0 && other.velocityY == 0)
                return;                              /* the dog ignores statues */
            if (hitByPlayer)
                Skier.addStyle(STYLE_STARTLED_A_DOG);
            World.setState(npc, DOG_BARK);
            Sound.play(SOUND_DOG);
            return;

        case OBJECT_SNOWBOARDER:
            switch (other.type) {
            case OBJECT_SKIER:
                /* Flattening the player pays out even when the rider stays
                 * up: the original awarded the style before its height test. */
                Skier.addStyle(STYLE_FLATTENED_A_SKIER);
                /* fall through */
            case OBJECT_OTHER_SKIER:
            case OBJECT_SNOWBOARDER:
            case OBJECT_TREE:
            case OBJECT_ROCK:
                if (npc.height < objectTopHeight(other) &&
                    npc.state != SNOWBOARDER_CRASH_FIRST) {
                    World.setState(npc, SNOWBOARDER_CRASH_FIRST);
                    Sound.play(SOUND_SNOWBOARDER);
                }
                return;
            case OBJECT_RAMP:
            case OBJECT_RAINBOW:
                /* Snowboarders use the ramps too. */
                if (npc.height < objectTopHeight(other)) {
                    npc.velocityZ = npc.velocityY / 2;
                    World.setState(npc, SNOWBOARDER_AIRBORNE);
                    Sound.play(SOUND_SNOWBOARDER);   /* slot 5, same as its crash */
                }
                return;
            default:
                return;
            }

        case OBJECT_YETI_NORTH:
        case OBJECT_YETI_SOUTH:
        case OBJECT_YETI_WEST:
        case OBJECT_YETI_EAST:
            /* There is no escaping this one. */
            if (hitByPlayer && npc.state < YETI_EAT_FIRST)
                Npc.startEating(npc, other);
            return;

        case OBJECT_TREE_ANIM:
            /* A walking tree that gets bumped stops where it is. */
            npc.velocityX = 0;
            World.setState(npc, TREE_STILL);
            return;

        default:
            return;
        }
    }

    /* ---------------------------------------------------------------- */
    /* Frame sweep                                                       */
    /* ---------------------------------------------------------------- */

    /** Can this object be run into at all? Fixtures carry no SOLID flag but
     *  still count, so that signs slow the skier down and gates get judged. */
    private static boolean isCollidable(GameObject object) {
        return (object.flags & (OBJECT_FLAG_SOLID | OBJECT_FLAG_FIXTURE)) != 0 ||
               object.type == OBJECT_BANNER;
    }

    /** Scenery never starts a collision; only the player and the things
     *  that move under their own steam do. */
    private static boolean isAnActor(GameObject object) {
        return object == g.player || object.type < OBJECT_FIRST_STATIC;
    }

    public static void all() {
        for (GameObject actor = g.objects; actor != null; actor = actor.next) {
            if (actor.isDead() || !isAnActor(actor))
                continue;

            for (GameObject target = g.objects; target != null; target = target.next) {
                if (target == actor || target.isDead())
                    continue;
                if (!isCollidable(target))
                    continue;
                if (!rectanglesOverlap(actor, target))
                    continue;

                if (actor == g.player)
                    playerHits(actor, target);
                else
                    npcHits(actor, target);

                if (actor.isDead())
                    break;
            }
        }
    }
}
