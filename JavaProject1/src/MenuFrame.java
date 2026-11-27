import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;

// First screen: pick a name, then either host a game or join one.
public class MenuFrame extends JFrame {

    private final JTextField nameField = new JTextField("Player");
    private final JTextField ipField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("5000");
    private final JButton hostButton = new Theme.Button("Host Game");
    private final JButton joinButton = new Theme.Button("Join Game");

    public MenuFrame() {
        setTitle("BLACK MAMBA");
        setIconImage(AppIcon.create(64));
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new Theme.Backdrop(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        // logo + title + small gold tagline
        JLabel title = new JLabel("BLACK MAMBA", new ImageIcon(AppIcon.create(72)), SwingConstants.CENTER);
        title.setForeground(Theme.CREAM);
        title.setFont(Theme.spaced(26f, 0.12f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel tagline = new JLabel("ENTER THE ARENA. RULE THE GAME.", SwingConstants.CENTER);
        tagline.setForeground(Theme.GOLD);
        tagline.setFont(Theme.spaced(10f, 0.16f));
        tagline.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(tagline);
        root.add(header, BorderLayout.NORTH);

        // form
        Theme.Card formCard = new Theme.Card(new BorderLayout(0, 12), 18);
        formCard.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JLabel formTitle = new JLabel("  PLAYER SETUP");
        formTitle.setForeground(Theme.GOLD_LIGHT);
        formTitle.setFont(Theme.spaced(12f, 0.16f));
        formCard.add(formTitle, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridLayout(3, 2, 12, 12));
        form.setOpaque(false);
        form.add(label("Your name"));
        form.add(nameField);
        form.add(label("Host IP (to join)"));
        form.add(ipField);
        form.add(label("Port"));
        form.add(portField);
        Theme.styleField(nameField);
        Theme.styleField(ipField);
        Theme.styleField(portField);
        formCard.add(form, BorderLayout.CENTER);
        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        centerWrap.add(formCard, BorderLayout.CENTER);
        root.add(centerWrap, BorderLayout.CENTER);

        // buttons
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        buttons.add(hostButton);
        buttons.add(joinButton);
        root.add(buttons, BorderLayout.SOUTH);

        hostButton.addActionListener(e -> hostGame());
        joinButton.addActionListener(e -> joinGame());

        setContentPane(root);
        pack();
        setSize(500, Math.max(390, getHeight() + 28));
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.bold(13f));
        return l;
    }

    // keeps only safe characters (see NameUtil) and cuts the name to 12 characters
    private String cleanName() {
        return NameUtil.sanitize(nameField.getText(), "Player");
    }

    private int readPort() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port >= 1024 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // falls through to the error below
        }
        JOptionPane.showMessageDialog(this, "Port must be a number between 1024 and 65535.",
                "Invalid port", JOptionPane.WARNING_MESSAGE);
        return -1;
    }

    private void hostGame() {
        int port = readPort();
        if (port == -1) {
            return;
        }
        try {
            ServerSocket server = new ServerSocket(port);
            setVisible(false);
            new GameWindow(this, cleanName(), server);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not start the server on port " + port + ".\nIt may already be in use.\n(" + e.getMessage() + ")",
                    "Host error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinGame() {
        final String ip = ipField.getText().trim();
        final int port = readPort();
        final String name = cleanName();
        if (port == -1) {
            return;
        }
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please type the host's IP address.",
                    "Missing IP", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // connecting can take a few seconds, so do it in a thread to keep the window alive
        joinButton.setEnabled(false);
        joinButton.setText("Connecting...");
        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 4000);   // 4 second timeout
                SwingUtilities.invokeLater(() -> {
                    resetJoinButton();
                    setVisible(false);
                    new GameWindow(this, name, socket);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    resetJoinButton();
                    JOptionPane.showMessageDialog(this,
                            "Could not connect to " + ip + ":" + port + ".\n"
                            + "Check the IP, the port, and that the host already started the game.\n"
                            + "Both PCs must be on the same network.",
                            "Connection failed", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void resetJoinButton() {
        joinButton.setEnabled(true);
        joinButton.setText("Join Game");
    }
}
