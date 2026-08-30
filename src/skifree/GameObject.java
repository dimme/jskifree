/* One sprite record on the hill, the original's object struct. */
package skifree;

public final class GameObject {
    public GameObject next;

    public int type;           /* an OBJECT_* code */
    public int state;          /* a skier state for skiers, animation frame otherwise */
    public int bitmap;         /* index into Sprites */

    public int x, y;           /* world position of the sprite's centre-bottom */
    public int height;         /* height above the snow; 0 when grounded */

    /* Where the object stood at the start of the frame. Collisions fire on
     * the frame two objects cross rather than for as long as they overlap,
     * and that test needs the previous positions. */
    public int previousY;

    public int velocityX;
    public int velocityY;
    public int velocityZ;

    public int timer;               /* frames remaining in the current state */
    public long stateEnteredMs;     /* the yeti's meal is timed in real ms */
    public int flags;

    public boolean isDead()    { return (flags & Consts.OBJECT_FLAG_DEAD) != 0; }
    public boolean isFixture() { return (flags & Consts.OBJECT_FLAG_FIXTURE) != 0; }
}
