import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

// Draws the app logo with Java2D, so we don't need any image file inside the project.
// Run "java AppIcon" once to create logo.png and icon.ico (the .ico is needed for the installer).
public class AppIcon {

    public static BufferedImage create(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 256.0, size / 256.0);   // everything below is drawn on a 256 x 256 canvas

        // matte black rounded tile with a dark maroon glow and a thin gold edge
        g.setPaint(new GradientPaint(0, 0, new Color(10, 8, 9), 0, 256, new Color(52, 10, 18)));
        g.fillRoundRect(6, 6, 244, 244, 64, 64);
        g.setColor(new Color(212, 175, 55));
        g.setStroke(new BasicStroke(5));
        g.drawRoundRect(9, 9, 238, 238, 60, 60);

        // the snake: one thick curvy line shaped like an S (crimson, with a darker core line)
        Path2D path = new Path2D.Double();
        path.moveTo(190, 62);
        path.curveTo(190, 20, 66, 24, 66, 84);
        path.curveTo(66, 142, 190, 116, 190, 174);
        path.curveTo(190, 232, 90, 232, 66, 200);
        g.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(200, 16, 48));
        g.draw(path);
        g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(130, 8, 30));
        g.draw(path);

        // head + gold slit eyes
        g.setColor(new Color(255, 84, 106));
        g.fillOval(190 - 24, 62 - 24, 48, 48);
        g.setColor(new Color(255, 226, 120));
        g.fillOval(172, 52, 14, 14);
        g.fillOval(194, 52, 14, 14);
        g.setColor(Color.BLACK);
        g.fillOval(177, 54, 4, 10);
        g.fillOval(199, 54, 4, 10);

        // golden glowing food orb
        g.setPaint(new RadialGradientPaint(211, 213, 34, new float[]{0f, 1f},
                new Color[]{new Color(255, 200, 70, 150), new Color(255, 200, 70, 0)}));
        g.fillOval(177, 179, 68, 68);
        g.setPaint(new RadialGradientPaint(207, 209, 22, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 248, 215), new Color(255, 214, 92), new Color(176, 112, 18)}));
        g.fillOval(194, 196, 34, 34);

        g.dispose();
        return img;
    }

    // An .ico file can hold a PNG inside it, so we write a tiny header and then the PNG bytes.
    public static void main(String[] args) throws IOException {
        BufferedImage big = create(256);
        ImageIO.write(big, "png", new File("logo.png"));

        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(big, "png", pngBytes);
        byte[] png = pngBytes.toByteArray();

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream("icon.ico"))) {
            out.write(new byte[]{0, 0, 1, 0, 1, 0});          // header: 1 image
            out.write(new byte[]{0, 0, 0, 0, 1, 0, 32, 0});   // 256x256 (0 means 256), 32 bit colour
            out.write(littleEndian(png.length));              // size of the PNG data
            out.write(littleEndian(22));                      // where the PNG data starts
            out.write(png);
        }
        System.out.println("Created logo.png and icon.ico");
    }

    private static byte[] littleEndian(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }
}
