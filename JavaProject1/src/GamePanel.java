import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.List;

// Only draws things. All the real work is done by GameEngine on the host.
// BLACK MAMBA look: matte black board, dark maroon edges, crimson snakes with gold eyes,
// and a softly glowing golden food orb. (Colours and fonts live in Theme.java.)
public class GamePanel extends JPanel {

    private static final int CELL = 22;
    private GameState state = new GameState();

    public GamePanel() {
        setPreferredSize(new Dimension(GameEngine.COLS * CELL, GameEngine.ROWS * CELL));
        setBackground(Theme.BLACK);
        setFocusable(true);   // needed so the panel can receive key presses
    }

    public void setState(GameState newState) {
        state = newState;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int boardW = GameEngine.COLS * CELL;
        int boardH = GameEngine.ROWS * CELL;

        // matte black floor with a faint maroon glow towards the edges
        g.setColor(Theme.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setPaint(new RadialGradientPaint(boardW / 2f, boardH / 2f, Math.max(boardW, boardH) * 0.72f,
                new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(78, 14, 26, 150)}));
        g.fillRect(0, 0, boardW, boardH);

        // faint grid
        g.setColor(Theme.GRID);
        for (int x = 0; x <= GameEngine.COLS; x++) {
            g.drawLine(x * CELL, 0, x * CELL, boardH);
        }
        for (int y = 0; y <= GameEngine.ROWS; y++) {
            g.drawLine(0, y * CELL, boardW, y * CELL);
        }

        // thin gold frame
        g.setColor(new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 150));
        g.drawRect(0, 0, boardW - 1, boardH - 1);

        // food
        if (state.food.x >= 0) {
            drawFood(g, state.food.x, state.food.y);
        }

        drawSnake(g, state.snake1, Theme.P1_BODY, Theme.P1_HEAD, false);
        drawSnake(g, state.snake2, Theme.P2_BODY, Theme.P2_HEAD, true);

        // big text in the middle (countdown, waiting, winner...)
        if (!state.message.isEmpty() && !state.status.equals("RUNNING")) {
            drawCenterText(g, state.message);
        }
    }

    // golden orb with a soft glow (the glow breathes very slightly)
    private void drawFood(Graphics2D g, int cellX, int cellY) {
        float cx = cellX * CELL + CELL / 2f;
        float cy = cellY * CELL + CELL / 2f;
        double pulse = 0.88 + 0.12 * Math.sin(System.currentTimeMillis() / 260.0);

        float glowR = (float) (CELL * 1.05 * pulse);
        g.setPaint(new RadialGradientPaint(cx, cy, glowR, new float[]{0f, 1f},
                new Color[]{new Color(255, 200, 70, 120), new Color(255, 200, 70, 0)}));
        g.fill(new Ellipse2D.Float(cx - glowR, cy - glowR, glowR * 2, glowR * 2));

        float r = CELL / 2f - 5f;
        g.setPaint(new RadialGradientPaint(cx - r * 0.3f, cy - r * 0.3f, r * 1.6f,
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(255, 248, 215), Theme.GOLD_LIGHT, new Color(176, 112, 18)}));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    private void drawSnake(Graphics2D g, List<Point> body, Color bodyColor, Color headColor, boolean outlined) {
        // tail first, head last, so the head is always on top
        for (int i = body.size() - 1; i >= 0; i--) {
            Point p = body.get(i);
            Color c = (i == 0) ? headColor : (i % 2 == 0 ? bodyColor : Theme.shade(bodyColor, 0.86f));
            g.setColor(c);
            g.fillRoundRect(p.x * CELL + 1, p.y * CELL + 1, CELL - 2, CELL - 2, 8, 8);
            if (outlined) {
                g.setColor(new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 140));
                g.drawRoundRect(p.x * CELL + 1, p.y * CELL + 1, CELL - 3, CELL - 3, 8, 8);
            }
        }
        if (!body.isEmpty()) {
            drawEyes(g, body);
        }
    }

    // two small gold eyes with slit pupils, on the front corners of the head
    private void drawEyes(Graphics2D g, List<Point> body) {
        Point head = body.get(0);
        int dx = 1;
        int dy = 0;
        if (body.size() > 1) {
            Point neck = body.get(1);
            dx = Integer.signum(head.x - neck.x);
            dy = Integer.signum(head.y - neck.y);
            if (dx == 0 && dy == 0) {
                dx = 1;
            }
        }
        double cx = head.x * CELL + CELL / 2.0;
        double cy = head.y * CELL + CELL / 2.0;
        for (int side = -1; side <= 1; side += 2) {
            double ex = cx + dx * 3.4 - dy * side * 4.2;
            double ey = cy + dy * 3.4 + dx * side * 4.2;
            g.setColor(new Color(255, 215, 90, 70));   // faint glow
            g.fill(new Ellipse2D.Double(ex - 5.2, ey - 5.2, 10.4, 10.4));
            g.setColor(new Color(20, 4, 8, 200));       // dark rim, so the eyes show on the gold head too
            g.fill(new Ellipse2D.Double(ex - 3.7, ey - 3.7, 7.4, 7.4));
            g.setColor(Theme.EYE);
            g.fill(new Ellipse2D.Double(ex - 2.8, ey - 2.8, 5.6, 5.6));
            double pw = (dx != 0) ? 1.6 : 4.4;     // slit across the direction of travel
            double ph = (dx != 0) ? 4.4 : 1.6;
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(ex - pw / 2, ey - ph / 2, pw, ph));
        }
    }

    private void drawCenterText(Graphics2D g, String text) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(new Color(12, 2, 5, 205));
        g.fillRect(0, h / 2 - 50, w, 100);
        g.setColor(Theme.GOLD);
        g.drawLine(0, h / 2 - 50, w, h / 2 - 50);
        g.drawLine(0, h / 2 + 49, w, h / 2 + 49);

        g.setFont(Theme.spaced(28f, 0.04f));
        FontMetrics fm = g.getFontMetrics();
        int x = (w - fm.stringWidth(text)) / 2;
        int y = h / 2 + fm.getAscent() / 3;
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(text, x + 2, y + 2);
        g.setColor(Theme.GOLD_LIGHT);
        g.drawString(text, x, y);
    }
}
