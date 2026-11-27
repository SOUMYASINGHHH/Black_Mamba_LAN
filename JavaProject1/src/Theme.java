import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

// BLACK MAMBA look: every colour and font lives here, in ONE place.
// This class only decides how things LOOK. It has no game rules and no networking.
public final class Theme {

    private Theme() { }

    // ----- backgrounds -----
    public static final Color BLACK        = new Color(10, 8, 9);       // matte black
    public static final Color BLACK_SOFT   = new Color(20, 13, 15);     // text fields
    public static final Color MAROON_DARK  = new Color(38, 9, 15);      // top / bottom bars
    public static final Color MAROON       = new Color(74, 14, 26);
    public static final Color GRID         = new Color(38, 15, 20);     // faint board grid

    // ----- accents -----
    public static final Color CRIMSON      = new Color(220, 20, 60);
    public static final Color CRIMSON_DARK = new Color(150, 14, 42);
    public static final Color GOLD         = new Color(212, 175, 55);
    public static final Color GOLD_LIGHT   = new Color(255, 214, 92);
    public static final Color GOLD_DEEP    = new Color(138, 108, 28);

    // ----- text -----
    public static final Color CREAM        = new Color(240, 228, 210);
    public static final Color MUTED        = new Color(204, 184, 178);
    public static final Color P1_TEXT      = new Color(255, 96, 118);   // player 1 = bright crimson
    public static final Color P2_TEXT      = GOLD_LIGHT;                // player 2 = gold-trimmed

    // ----- snakes (both are red; player 2 is darker with a gold outline and a gold head) -----
    public static final Color P1_BODY      = new Color(200, 16, 48);
    public static final Color P1_HEAD      = new Color(255, 84, 106);
    public static final Color P2_BODY      = new Color(122, 16, 34);
    public static final Color P2_HEAD      = new Color(255, 205, 80);
    public static final Color EYE          = new Color(255, 226, 120);  // gold snake eyes

    // ----- fonts -----
    public static Font bold(float size) {
        return new Font("SansSerif", Font.BOLD, 12).deriveFont(size);
    }

    public static Font plain(float size) {
        return new Font("SansSerif", Font.PLAIN, 12).deriveFont(size);
    }

    // bold font with extra space between the letters (used for the big titles)
    public static Font spaced(float size, float tracking) {
        Map<TextAttribute, Object> a = new HashMap<>();
        a.put(TextAttribute.TRACKING, tracking);
        return bold(size).deriveFont(a);
    }

    // a slightly darker copy of a colour (used to make the snake "scales")
    public static Color shade(Color c, float factor) {
        return new Color(Math.round(c.getRed() * factor), Math.round(c.getGreen() * factor),
                Math.round(c.getBlue() * factor), c.getAlpha());
    }

    // ----- small styled components -----

    // a panel with a matte black to dark maroon gradient behind its content
    public static class Backdrop extends JPanel {
        public Backdrop(LayoutManager layout) {
            super(layout);
            setBackground(BLACK);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setPaint(new GradientPaint(0, 0, BLACK, 0, getHeight(), MAROON_DARK));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.dispose();
        }
    }

    // Rounded translucent surface used for menu forms and score panels.
    public static class Card extends JPanel {
        private final int radius;
        public Card(LayoutManager layout, int radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1, h = getHeight() - 1;
            g.setColor(new Color(12, 8, 11, 225));
            g.fillRoundRect(0, 0, w, h, radius, radius);
            g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 105));
            g.drawRoundRect(0, 0, w, h, radius, radius);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // a crimson button with a gold edge (a dull one when it is disabled)
    public static class Button extends JButton {
        public Button(String text) {
            super(text);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setForeground(Color.WHITE);
            setFont(bold(14f));
            setBorder(BorderFactory.createEmptyBorder(11, 20, 11, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isEnabled();
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            Color fill;
            if (!on) {
                fill = new Color(38, 22, 26);
            } else if (getModel().isPressed()) {
                fill = shade(CRIMSON_DARK, 0.75f);
            } else if (getModel().isRollover()) {
                fill = CRIMSON;
            } else {
                fill = CRIMSON_DARK;
            }
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(1, 3, w - 1, h - 2, 14, 14);
            g.setPaint(new GradientPaint(0, 0, on && getModel().isRollover() ? CRIMSON : fill,
                    0, h, shade(fill, 0.68f)));
            g.fillRoundRect(0, 0, w - 1, h - 3, 14, 14);
            g.setColor(on ? GOLD : new Color(84, 66, 66));
            g.drawRoundRect(0, 0, w - 1, h - 3, 14, 14);

            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(on ? Color.WHITE : new Color(120, 100, 100));
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // dark text field with cream text, a gold caret and a gold edge
    public static void styleField(JTextField f) {
        f.setBackground(BLACK_SOFT);
        f.setForeground(CREAM);
        f.setCaretColor(GOLD_LIGHT);
        f.setSelectionColor(CRIMSON_DARK);
        f.setSelectedTextColor(Color.WHITE);
        f.setFont(plain(14f));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_DEEP, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }
}
