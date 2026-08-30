/* The whole game state, the original's globals. */
package skifree;

public final class Game {
    public GameObject objects;     /* singly linked list, newest first */
    public GameObject player;

    /* The skier's last known position. The camera and the distance readout
     * follow these rather than the player object, so that both hold still
     * when the yeti eats him. */
    public int skierX, skierY;

    /* Viewport, in world coordinates. The player is kept near the middle. */
    public int viewLeft, viewTop, viewRight, viewBottom;
    public int viewWidth, viewHeight;

    /* Fractional scroll accumulators driving edge spawning. */
    public int scrollDebtX, scrollDebtY;

    /* Total sprite area currently alive, used to cap object density. */
    public int coveredArea;

    public long nowMs;             /* clock at the start of this frame */
    public long previousMs;
    public long frameMs;           /* elapsed time for this frame */

    public int course;             /* COURSE_* currently being run */
    public long courseStartMs;
    public long courseTimeMs;
    public int stylePoints;
    public int nextGate;           /* index into the gate list */

    /* The game runs only while the window is active and not paused. */
    public boolean focused;
    public boolean paused;
    public boolean running;
    /* The hidden 'f' cheat: every velocity is applied twice. */
    public boolean doubleSpeed;
    public boolean eaten;
    public boolean quit;

    public int rngState;

    /** The single instance, matching the original's global. */
    public static final Game g = new Game();
}
