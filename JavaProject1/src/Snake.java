import java.awt.Point;
import java.util.ArrayDeque;
import java.util.LinkedList;

// One snake on the board. The head is always the FIRST point in the list.
public class Snake {

    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    // Phase 1: how many key presses we remember between two game steps.
    private static final int MAX_QUEUED_TURNS = 3;

    private final LinkedList<Point> body = new LinkedList<>();
    private int direction;      // direction used in the last move
    // Turns the player has asked for but that were not used yet, oldest first.
    // One turn is used per game step. Before, there was room for only ONE turn, so if you
    // pressed UP and LEFT quickly (inside one 120 ms step) the second key was lost.
    private final ArrayDeque<Integer> turnQueue = new ArrayDeque<>();
    private int score = 0;

    public Snake(int headX, int headY, int startDir, int length) {
        direction = startDir;

        // the body is built behind the head, so we go opposite to the moving direction
        for (int i = 0; i < length; i++) {
            body.add(new Point(headX - dx(startDir) * i, headY - dy(startDir) * i));
        }
    }

    private static int dx(int dir) {
        if (dir == LEFT) return -1;
        if (dir == RIGHT) return 1;
        return 0;
    }

    private static int dy(int dir) {
        if (dir == UP) return -1;
        if (dir == DOWN) return 1;
        return 0;
    }

    private static boolean isOpposite(int a, int b) {
        return (a == UP && b == DOWN) || (a == DOWN && b == UP)
            || (a == LEFT && b == RIGHT) || (a == RIGHT && b == LEFT);
    }

    // called when the player presses a key
    public void turn(int newDir) {
        if (newDir < UP || newDir > RIGHT) {
            return;   // not a real direction (a broken or fake message from the network)
        }
        // Every new turn is checked against the turn BEFORE it (the last one waiting in the
        // queue, or the direction we are moving in if the queue is empty).
        // So the snake can never turn 180 degrees into its own neck, even with fast typing.
        int previous = turnQueue.isEmpty() ? direction : turnQueue.peekLast();
        if (newDir == previous || isOpposite(newDir, previous)) {
            return;   // same direction again (key held down) or a U-turn: ignore
        }
        if (turnQueue.size() >= MAX_QUEUED_TURNS) {
            return;   // the player is mashing keys, keep the first ones
        }
        turnQueue.addLast(newDir);
    }

    // the direction the next move will use
    private int nextDirection() {
        return turnQueue.isEmpty() ? direction : turnQueue.peekFirst();
    }

    // where the head will be after the next move (we don't move yet)
    public Point nextHead() {
        Point head = body.getFirst();
        int next = nextDirection();
        return new Point(head.x + dx(next), head.y + dy(next));
    }

    // grow = true means it just ate food, so we keep the tail
    public void move(boolean grow) {
        body.addFirst(nextHead());
        if (!turnQueue.isEmpty()) {
            direction = turnQueue.pollFirst();   // this turn is now used up
        }
        if (!grow) {
            body.removeLast();
        }
    }

    public LinkedList<Point> getBody() {
        return body;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        score += points;
    }
}
