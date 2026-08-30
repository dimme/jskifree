/* Numeric tables recovered verbatim from ski32.exe.
 *
 * Each block notes the address it was read from in the original image.
 * Nothing here was invented: these are the bytes the 1991 game shipped with.
 */
package skifree;

import static skifree.Consts.*;

public final class Tables {
    private Tables() {}

    /** Per-state motion parameters, from the 16-byte records at 0x0040A308. */
    public static final class Motion {
        public final int downhillAccel;    /* gained per frame while below target      */
        public final int downhillTarget;   /* steady-state speed down the fall line    */
        public final int lateralAccel;     /* gained per frame while below target      */
        public final int lateralFactor;    /* sideways speed as a fraction of downhill */
        public final int lateralDirection; /* -1 left, +1 right, 0 keep current sign   */

        Motion(int downhillAccel, int downhillTarget, int lateralAccel,
               int lateralFactor, int lateralDirection) {
            this.downhillAccel = downhillAccel;
            this.downhillTarget = downhillTarget;
            this.lateralAccel = lateralAccel;
            this.lateralFactor = lateralFactor;
            this.lateralDirection = lateralDirection;
        }
    }

    private static Motion m(int a, int b, int c, int d, int e) {
        return new Motion(a, b, c, d, e);
    }

    /** Steering table, 0x0040A258: 22 records of {left, right}. */
    public static final int[][] TURN_TABLE = {
        {  1,  4 },   /* SKIER_DOWNHILL        */
        {  2,  0 },   /* SKIER_LEFT_SLIGHT     */
        {  3,  1 },   /* SKIER_LEFT_MEDIUM     */
        {  7,  2 },   /* SKIER_LEFT_HARD       */
        {  0,  5 },   /* SKIER_RIGHT_SLIGHT    */
        {  4,  6 },   /* SKIER_RIGHT_MEDIUM    */
        {  5,  8 },   /* SKIER_RIGHT_HARD      */
        {  3,  2 },   /* SKIER_SKATE_LEFT      */
        {  5,  6 },   /* SKIER_SKATE_RIGHT     */
        {  9,  2 },   /* SKIER_CLIMB_LEFT      */
        {  5, 10 },   /* SKIER_CLIMB_RIGHT     */
        {  3,  6 },   /* SKIER_CRASHED         */
        {  3,  6 },   /* SKIER_SITTING         */
        { 14, 15 },   /* SKIER_JUMP_SPREAD     */
        { 16, 13 },   /* SKIER_JUMP_LEFT       */
        { 13, 16 },   /* SKIER_JUMP_RIGHT      */
        { 15, 14 },   /* SKIER_JUMP_TWIST      */
        { 14, 15 },   /* SKIER_TANGLED         */
        { 20, 21 },   /* SKIER_JUMP_FLIP       */
        { 20, 21 },   /* SKIER_JUMP_TUCK       */
        { 16, 13 },   /* SKIER_JUMP_ROLL_LEFT  */
        { 13, 16 },   /* SKIER_JUMP_ROLL_RIGHT */
    };

    /** Motion table, 0x0040A308: 22 records, first five fields used. */
    public static final Motion[] MOTION_TABLE = {
        m( 1, 16,  0,  0,  0 ),   /* SKIER_DOWNHILL        */
        m( 1, 12,  1,  1, -1 ),   /* SKIER_LEFT_SLIGHT     */
        m( 1,  6,  1,  4, -1 ),   /* SKIER_LEFT_MEDIUM     */
        m( 1,  0,  1,  8, -1 ),   /* SKIER_LEFT_HARD       */
        m( 1, 12,  1,  1,  1 ),   /* SKIER_RIGHT_SLIGHT    */
        m( 1,  6,  1,  4,  1 ),   /* SKIER_RIGHT_MEDIUM    */
        m( 1,  0,  1,  8,  1 ),   /* SKIER_RIGHT_HARD      */
        m( 1,  0,  1,  8, -1 ),   /* SKIER_SKATE_LEFT      */
        m( 1,  0,  1,  8,  1 ),   /* SKIER_SKATE_RIGHT     */
        m( 1,  0,  0,  0,  0 ),   /* SKIER_CLIMB_LEFT      */
        m( 1,  0,  0,  0,  0 ),   /* SKIER_CLIMB_RIGHT     */
        m( 0,  0,  0,  0,  0 ),   /* SKIER_CRASHED         */
        m( 0,  0,  0,  0,  0 ),   /* SKIER_SITTING         */
        m( 1, 24,  0,  0,  0 ),   /* SKIER_JUMP_SPREAD     */
        m( 1, 22,  0,  0,  0 ),   /* SKIER_JUMP_LEFT       */
        m( 1, 22,  0,  0,  0 ),   /* SKIER_JUMP_RIGHT      */
        m( 1, 20,  0,  0,  0 ),   /* SKIER_JUMP_TWIST      */
        m( 1, 24,  0,  0,  0 ),   /* SKIER_TANGLED         */
        m( 1, 20,  0,  0,  0 ),   /* SKIER_JUMP_FLIP       */
        m( 1, 20,  0,  0,  0 ),   /* SKIER_JUMP_TUCK       */
        m( 1, 22,  0,  0,  0 ),   /* SKIER_JUMP_ROLL_LEFT  */
        m( 1, 22,  0,  0,  0 ),   /* SKIER_JUMP_ROLL_RIGHT */
    };

    /** State -> bitmap id, 0x0040A1AC: 64 entries covering every object type. */
    public static final int[] STATE_BITMAP = {
         1,  2,  3,  4,  5,  6,  7,  8,   /* 0x00 */
         9, 10, 11, 12, 13, 14, 15, 16,   /* 0x08 */
        17, 18, 19, 20, 21, 22, 28, 29,   /* 0x10 */
        30, 31, 32, 33, 34, 35, 36, 37,   /* 0x18 */
        38, 39, 40, 41, 42, 43, 44, 65,   /* 0x20 */
        66, 67, 68, 69, 70, 71, 72, 73,   /* 0x28 */
        74, 75, 76, 77, 78, 79, 80, 81,   /* 0x30 */
        83, 84, 85, 84, 49, 87, 88, 89,   /* 0x38 */
    };

    /** First state of each animated object type, 0x0040A22C. */
    public static final int[] TYPE_INITIAL_STATE = {
        0x06,   /* OBJECT_SKIER       */
        0x16,   /* OBJECT_OTHER_SKIER */
        0x1B,   /* OBJECT_DOG         */
        0x1F,   /* OBJECT_SNOWBOARDER */
        0x27,   /* OBJECT_LIFT_CHAIR  */
        0x2A,   /* OBJECT_YETI_NORTH  */
        0x2A,   /* OBJECT_YETI_SOUTH  */
        0x2A,   /* OBJECT_YETI_WEST   */
        0x2A,   /* OBJECT_YETI_EAST   */
        0x38,   /* OBJECT_FIRE        */
        0x3C,   /* OBJECT_TREE_ANIM   */
    };

    /** Landing table, 0x0040A434: the state an airborne trick resolves to. */
    public static final int[] LANDING_STATE = new int[SKIER_STATE_COUNT];
    static {
        LANDING_STATE[SKIER_JUMP_SPREAD]     = 0x00;
        LANDING_STATE[SKIER_JUMP_LEFT]       = 0x03;
        LANDING_STATE[SKIER_JUMP_RIGHT]      = 0x06;
        LANDING_STATE[SKIER_JUMP_TWIST]      = 0x0B;
        LANDING_STATE[SKIER_TANGLED]         = 0x0B;
        LANDING_STATE[SKIER_JUMP_FLIP]       = 0x0B;
        LANDING_STATE[SKIER_JUMP_TUCK]       = 0x0B;
        LANDING_STATE[SKIER_JUMP_ROLL_LEFT]  = 0x0B;
        LANDING_STATE[SKIER_JUMP_ROLL_RIGHT] = 0x0B;
    }

    /** Motion records for the other skier, 0x0040A490, states 0x16..0x1A. */
    public static final Motion[] OTHER_SKIER_MOTION = {
        m( 1,  1,  0,  0,  0 ),   /* 0x16 */
        m( 1,  1,  1,  4, -1 ),   /* 0x17 */
        m( 1,  1,  1,  4,  1 ),   /* 0x18 */
        m( 0,  0,  0,  0,  0 ),   /* 0x19 */
        m( 0,  0,  0,  0,  0 ),   /* 0x1A */
    };

    /** Motion records for the snowboarder, 0x0040A4E0, states 0x1F..0x26. */
    public static final Motion[] SNOWBOARDER_MOTION = {
        m( 2, 18,  2,  1, -1 ),   /* 0x1F */
        m( 2, 18,  2,  1,  1 ),   /* 0x20 */
        m( 1, 22,  0,  0,  0 ),   /* 0x21 */
        m( 1,  4,  0,  0,  0 ),   /* 0x22 */
        m( 1,  4,  0,  0,  0 ),   /* 0x23 */
        m( 1,  4,  0,  0,  0 ),   /* 0x24 */
        m( 1,  4,  0,  0,  0 ),   /* 0x25 */
        m( 1,  4,  0,  0,  0 ),   /* 0x26 */
    };
}
