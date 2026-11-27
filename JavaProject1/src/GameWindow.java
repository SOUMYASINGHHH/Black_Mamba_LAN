import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

// The game screen. The same window is used by both players:
//   host   -> runs the GameEngine and sends the state to the client
//   client -> sends its key presses to the host and draws the state it receives
//
// Phase 1: the host window now stays open for the whole session and keeps LISTENING,
// so if the opponent leaves, a new one can join without restarting the application.
public class GameWindow extends JFrame implements Connection.Listener {

    // how long a new connection has to complete the handshake (HELLO on the host,
    // the first STATE on the client) before we give up on it
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;

    // the host is always in exactly one of these phases (only touched on the Swing thread)
    private static final int PHASE_WAITING = 0;     // nobody is connected
    private static final int PHASE_HANDSHAKE = 1;   // somebody connected, we wait for his HELLO
    private static final int PHASE_PLAYING = 2;     // HELLO accepted, a game is running or finished

    private final MenuFrame menu;
    private final boolean isHost;
    private String myName;                // the client's name can be changed by the host (duplicates)

    private GameEngine engine;            // host only
    private ServerSocket serverSocket;    // host only, stays open while the window is open
    private javax.swing.Timer gameTimer;  // host only
    private javax.swing.Timer handshakeTimer;   // one-shot, see HANDSHAKE_TIMEOUT_MS
    private final AtomicBoolean opponentSlotTaken = new AtomicBoolean(false);  // host only
    private int hostPhase = PHASE_WAITING;                                      // host only
    private boolean gameAbandoned = false;   // host only: true if the opponent left in the middle of a game
    private boolean handshakeDone = false;   // client only: true after the first valid STATE
    private Connection connection;

    private final GamePanel panel = new GamePanel();
    private final JLabel p1Label = new JLabel();
    private final JLabel p2Label = new JLabel();
    private final JLabel timeLabel = new JLabel("Time: 60");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton restartButton = new Theme.Button("Restart");

    private String lastStatus = "";
    private boolean closed = false;

    // ---------- host constructor ----------
    public GameWindow(MenuFrame menu, String name, ServerSocket server) {
        this.menu = menu;
        this.isHost = true;
        this.myName = name;
        this.serverSocket = server;

        engine = new GameEngine();
        engine.setNames(name, "Player 2");
        engine.setMessage("Waiting for opponent...");

        buildWindow();
        showState(engine.getState());
        statusLabel.setText(waitingText());

        // this timer is the heartbeat of the game, it starts when the second player joins
        gameTimer = new javax.swing.Timer(GameEngine.TICK_MS, e -> hostTick());
        handshakeTimer = new javax.swing.Timer(HANDSHAKE_TIMEOUT_MS, e -> onHandshakeTimeout());
        handshakeTimer.setRepeats(false);
        startAcceptLoop();
    }

    // ---------- client constructor ----------
    public GameWindow(MenuFrame menu, String name, Socket socket) {
        this.menu = menu;
        this.isHost = false;
        this.myName = name;

        buildWindow();
        statusLabel.setText("Connecting to host...");
        handshakeTimer = new javax.swing.Timer(HANDSHAKE_TIMEOUT_MS, e -> onHandshakeTimeout());
        handshakeTimer.setRepeats(false);
        try {
            connection = new Connection(socket, this);
            connection.start();
            connection.send("HELLO " + name);
            handshakeTimer.start();   // the host must answer with a STATE soon
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not set up the connection: " + e.getMessage());
            closeWindow();
        }
    }

    // ---------- building the screen ----------
    private void buildWindow() {
        setTitle("BLACK MAMBA - " + (isHost ? "Host" : "Client") + " (" + myName + ")");
        setIconImage(AppIcon.create(64));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // top bar: separated score cards and a clear central timer
        JPanel top = new JPanel(new GridLayout(1, 3, 10, 0));
        top.setBackground(Theme.BLACK);
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        for (JLabel l : new JLabel[]{p1Label, timeLabel, p2Label}) {
            l.setForeground(Theme.CREAM);
            l.setFont(Theme.bold(16f));
            l.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
            l.setHorizontalAlignment(SwingConstants.CENTER);
        }
        p1Label.setForeground(Theme.P1_TEXT);       // player 1: crimson
        p2Label.setForeground(Theme.P2_TEXT);       // player 2: gold
        timeLabel.setForeground(Theme.GOLD_LIGHT);
        Theme.Card p1Card = new Theme.Card(new BorderLayout(), 14);
        Theme.Card timerCard = new Theme.Card(new BorderLayout(), 14);
        Theme.Card p2Card = new Theme.Card(new BorderLayout(), 14);
        p1Card.add(p1Label, BorderLayout.CENTER);
        timerCard.add(timeLabel, BorderLayout.CENTER);
        p2Card.add(p2Label, BorderLayout.CENTER);
        top.add(p1Card);
        top.add(timerCard);
        top.add(p2Card);
        add(top, BorderLayout.NORTH);

        add(panel, BorderLayout.CENTER);

        // bottom bar: status text and restart button
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBackground(Theme.MAROON_DARK);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GOLD_DEEP),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        statusLabel.setForeground(Theme.MUTED);
        statusLabel.setFont(Theme.plain(13f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        restartButton.setFocusable(false);   // so the arrow keys keep working after a click
        restartButton.setEnabled(false);
        restartButton.addActionListener(e -> onRestartClicked());
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(restartButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
        panel.requestFocusInWindow();
    }

    // ---------- host side ----------
    private String waitingText() {
        return "Waiting... tell your friend to join with IP: " + findLocalIps()
                + "  Port: " + serverSocket.getLocalPort();
    }

    // accept() blocks until someone connects, so it runs in its own thread.
    // The loop never ends while the window is open: the ServerSocket stays open, so a new
    // opponent can join after the old one left. (Closing and re-opening the port could fail
    // on Windows because the old port may still be "in use" for a short time.)
    private void startAcceptLoop() {
        Thread t = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                Socket s;
                try {
                    s = serverSocket.accept();
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;   // the host closed the window
                    }
                    try {
                        Thread.sleep(200);   // some other error: wait a bit, avoid a busy loop
                    } catch (InterruptedException ie) {
                        return;
                    }
                    continue;
                }
                // only ONE opponent at a time. compareAndSet is an atomic "if free, take it"
                if (!opponentSlotTaken.compareAndSet(false, true)) {
                    Connection.rejectAndClose(s, "FULL");
                    continue;
                }
                final Socket accepted = s;
                SwingUtilities.invokeLater(() -> opponentConnected(accepted));
            }
        }, "host-accept");
        t.setDaemon(true);
        t.start();
    }

    private void opponentConnected(Socket s) {
        if (closed) {
            try {
                s.close();
            } catch (IOException e) {
                // ignore
            }
            return;
        }
        try {
            connection = new Connection(s, this);
            connection.start();
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignore) {
                // ignore
            }
            connection = null;
            opponentSlotTaken.set(false);
            statusLabel.setText("A connection attempt failed. " + waitingText());
            return;
        }
        // The opponent now has HANDSHAKE_TIMEOUT_MS to send a proper HELLO.
        // Without this limit, a silent connection would block the only free seat forever.
        hostPhase = PHASE_HANDSHAKE;
        handshakeTimer.restart();
        statusLabel.setText("Opponent connected! Waiting for the handshake...");
        refreshRestartButton();
    }

    // the host has verified the HELLO, the game can begin
    private void acceptOpponent(String rawName) {
        handshakeTimer.stop();
        // never trust the name: clean it, then make sure it is different from the host's name
        String name = NameUtil.makeUnique(NameUtil.sanitize(rawName, "Player 2"), myName);
        hostPhase = PHASE_PLAYING;
        engine.setNames(myName, name);
        startRound();
        gameTimer.start();
        statusLabel.setText("Opponent connected!");
        refreshRestartButton();
    }

    // starts a new round (first game and restarts)
    private void startRound() {
        gameAbandoned = false;
        engine.start();
    }

    // Forget the current opponent and free the seat for the next one.
    private void releaseOpponent() {
        handshakeTimer.stop();
        Connection c = connection;
        connection = null;
        if (c != null) {
            c.close();
        }
        hostPhase = PHASE_WAITING;
        opponentSlotTaken.set(false);
    }

    // the opponent left, or the connection died
    private void opponentLeft(String reason) {
        gameTimer.stop();   // nobody to play with, stop the heartbeat of the game
        boolean wasPlaying = hostPhase == PHASE_PLAYING;
        releaseOpponent();

        // a game that was still running is cancelled. It must NOT count for the high score.
        if (wasPlaying && !engine.isOver()) {
            gameAbandoned = true;
            engine.abort("Opponent disconnected");
        }
        showState(engine.getState());
        statusLabel.setText("Opponent left (" + reason + "). " + waitingText());
    }

    private void onHandshakeTimeout() {
        if (isHost) {
            if (hostPhase != PHASE_HANDSHAKE) {
                return;
            }
            releaseOpponent();
            showState(engine.getState());
            statusLabel.setText("A connection was dropped (no valid HELLO). " + waitingText());
        } else if (!handshakeDone) {
            failAndClose("The host did not answer.\nCheck that it is running this game on that port.");
        }
    }

    private void hostTick() {
        engine.tick();
        GameState gs = engine.getState();
        showState(gs);
        if (connection != null && hostPhase == PHASE_PLAYING) {
            // "replaceable": on a slow network the old, unsent frames are skipped
            connection.sendReplaceable("STATE " + gs.toText());
        }
    }

    // ---------- messages from the other computer (already moved onto the Swing thread) ----------
    private void handleMessage(Connection source, String line) {
        if (source != connection) {
            return;   // a late message from a connection we already dropped
        }
        if (isHost) {
            handleHostMessage(line);
        } else {
            handleClientMessage(line);
        }
    }

    private void handleHostMessage(String line) {
        if (hostPhase == PHASE_HANDSHAKE) {
            // the very first message must be HELLO
            if (line.equals("HELLO") || line.startsWith("HELLO ")) {
                acceptOpponent(line.length() > 6 ? line.substring(6) : "");
            } else {
                releaseOpponent();
                statusLabel.setText("A connection was dropped (wrong handshake). " + waitingText());
            }
            return;
        }
        if (hostPhase != PHASE_PLAYING) {
            return;
        }
        if (line.startsWith("DIR ")) {
            try {
                int dir = Integer.parseInt(line.substring(4).trim());
                if (dir >= Snake.UP && dir <= Snake.RIGHT) {   // only 0..3 are real directions
                    engine.turn(2, dir);
                }
            } catch (NumberFormatException e) {
                // bad message, just ignore it
            }
        } else if (line.equals("RESTART") && engine.isOver()) {
            startRound();
        }
    }

    private void handleClientMessage(String line) {
        if (line.startsWith("STATE ")) {
            GameState gs;
            try {
                gs = GameState.fromText(line.substring(6));
            } catch (RuntimeException e) {
                return;   // broken line, skip it. The next one comes in 120 ms anyway
            }
            if (!handshakeDone) {
                // first good STATE = the host accepted us. The host may have renamed us
                // (for example if we had the same name), so use the name it gave us.
                handshakeDone = true;
                handshakeTimer.stop();
                myName = gs.name2;
                setTitle("BLACK MAMBA - Client (" + myName + ")");
                statusLabel.setText("Connected to host. Get ready!");
            }
            showState(gs);
        } else if (line.startsWith("REJECT")) {
            // we show our own text on purpose, never text sent by the other computer
            failAndClose(line.equals("REJECT FULL")
                    ? "The host already has an opponent."
                    : "The host refused the connection.");
        }
    }

    @Override
    public void onMessage(Connection source, String line) {
        SwingUtilities.invokeLater(() -> handleMessage(source, line));
    }

    @Override
    public void onDisconnect(Connection source, String reason) {
        SwingUtilities.invokeLater(() -> {
            if (closed || source != connection) {
                return;   // we closed it ourselves, or it is an old connection
            }
            if (isHost) {
                opponentLeft(reason);
            } else if (handshakeDone) {
                failAndClose("Lost connection to the host.\n(" + reason + ")");
            } else {
                failAndClose("Could not join the game.\n(" + reason + ")");
            }
        });
    }

    // client only: drop the connection, tell the user, go back to the menu
    private void failAndClose(String message) {
        if (closed) {
            return;
        }
        handshakeTimer.stop();
        Connection c = connection;
        connection = null;      // from now on, late messages of this connection are ignored
        if (c != null) {
            c.close();
        }
        JOptionPane.showMessageDialog(this, message);
        closeWindow();
    }

    // ---------- input ----------
    private void handleKey(int code) {
        int dir;
        switch (code) {
            case KeyEvent.VK_UP:    case KeyEvent.VK_W: dir = Snake.UP;    break;
            case KeyEvent.VK_DOWN:  case KeyEvent.VK_S: dir = Snake.DOWN;  break;
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: dir = Snake.LEFT;  break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: dir = Snake.RIGHT; break;
            default: return;
        }
        if (isHost) {
            engine.turn(1, dir);
        } else if (connection != null) {
            connection.send("DIR " + dir);   // only queues the message, never blocks
        }
    }

    private void onRestartClicked() {
        if (isHost) {
            if (hostPhase == PHASE_PLAYING) {
                startRound();
            }
        } else if (connection != null) {
            connection.send("RESTART");
        }
    }

    // ---------- updating the screen ----------
    private void showState(GameState gs) {
        panel.setState(gs);
        p1Label.setText(gs.name1 + (isHost ? " (You)" : "") + ": " + gs.score1);
        p2Label.setText(gs.name2 + (isHost ? "" : " (You)") + ": " + gs.score2);
        timeLabel.setText("Time: " + gs.timeLeft);

        // runs once, at the moment the game finishes
        if (gs.status.equals("OVER") && !lastStatus.equals("OVER")) {
            if (isHost && gameAbandoned) {
                // the opponent left in the middle of the game: not a real result
                statusLabel.setText("Game abandoned. Score not recorded.");
            } else {
                int myScore = isHost ? gs.score1 : gs.score2;
                boolean record = HighScore.submit(myScore);
                statusLabel.setText("Game over. Your score: " + myScore
                        + "  |  Best on this PC: " + HighScore.load() + (record ? "  (new record!)" : ""));
            }
        } else if (gs.status.equals("RUNNING") && !lastStatus.equals("RUNNING")) {
            statusLabel.setText("Arrow keys / WASD to move. Eat the golden orb!");
        }
        lastStatus = gs.status;
        refreshRestartButton();
    }

    // Restart only makes sense when the game is over AND the opponent is really there
    private void refreshRestartButton() {
        boolean opponentReady = connection != null && (!isHost || hostPhase == PHASE_PLAYING);
        restartButton.setEnabled(lastStatus.equals("OVER") && opponentReady);
    }

    // ---------- cleanup ----------
    private void closeWindow() {
        if (closed) {
            return;
        }
        closed = true;
        if (gameTimer != null) {
            gameTimer.stop();
        }
        if (handshakeTimer != null) {
            handshakeTimer.stop();
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();   // this also ends the accept loop thread
            }
        } catch (IOException e) {
            // ignore
        }
        if (connection != null) {
            connection.close();
        }
        dispose();
        menu.setVisible(true);
    }

    // The host needs to tell the friend an IP address. A PC can have many network adapters
    // (wifi, virtual ones...) so we list every normal IPv4 address we can find.
    private String findLocalIps() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface ni = nets.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            return "unknown (check ipconfig)";
        }
        return sb.length() == 0 ? "unknown (check ipconfig)" : sb.toString();
    }
}
