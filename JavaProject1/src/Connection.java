import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Wraps a Socket so both host and client can use the same code.
//
// Phase 1 changes (why this class grew):
//  - a READER thread reads lines, so the GUI never freezes while waiting for data
//  - a WRITER thread sends lines from a queue, so a slow network can never freeze the GUI
//  - HEARTBEAT: if we have sent nothing for 1 second, the writer sends "PING".
//    The other side swallows it. If NOTHING arrives for 6 seconds the connection is
//    declared dead (this catches a pulled cable or a dropped Wi-Fi, which TCP alone
//    would take minutes to notice)
//  - a line longer than MAX_LINE_LENGTH is treated as an attack/bug and the connection is dropped
public class Connection {

    public interface Listener {
        // Both methods are called from a background thread.
        // "source" tells which connection it was, so the window can ignore calls
        // coming from an old connection that was already replaced.
        void onMessage(Connection source, String line);
        void onDisconnect(Connection source, String reason);
    }

    public static final int HEARTBEAT_INTERVAL_MS = 1000;
    public static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_LINE_LENGTH = 16384;   // a full STATE line is at most about 5 KB
    private static final int MAX_QUEUED_MESSAGES = 256; // protects memory if the other side stops reading
    private static final String HEARTBEAT = "PING";

    // one message waiting to be sent
    private static final class Outgoing {
        final String text;
        final boolean replaceable;   // true = an older waiting copy is useless once a newer one exists
        Outgoing(String text, boolean replaceable) {
            this.text = text;
            this.replaceable = replaceable;
        }
    }

    private final Socket socket;
    private final BufferedReader in;
    private final OutputStream out;
    private final Listener listener;
    private final LinkedBlockingQueue<Outgoing> queue = new LinkedBlockingQueue<>(MAX_QUEUED_MESSAGES);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private Thread writerThread;

    public Connection(Socket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        socket.setTcpNoDelay(true);            // send small messages immediately, less lag
        socket.setSoTimeout(READ_TIMEOUT_MS);  // read() gives up after 6 s of silence
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();
    }

    public void start() {
        Thread reader = new Thread(this::readLoop, "connection-reader");
        reader.setDaemon(true);
        reader.start();

        writerThread = new Thread(this::writeLoop, "connection-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    // ---------- receiving ----------
    private void readLoop() {
        try {
            String line;
            while ((line = readLine()) != null) {
                if (line.equals(HEARTBEAT)) {
                    continue;   // only proves the other side is alive, the game never sees it
                }
                listener.onMessage(this, line);
            }
            fail("The other side closed the connection");
        } catch (SocketTimeoutException e) {
            fail("No data for " + (READ_TIMEOUT_MS / 1000) + " seconds (connection timed out)");
        } catch (IOException e) {
            fail("Network error" + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    // Like BufferedReader.readLine(), but with a size limit. readLine() would keep
    // collecting characters forever if the other side never sends a line break.
    // Returns null at end of stream (a half-received last line is thrown away).
    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') {   // Windows sends \r\n
                    sb.setLength(len - 1);
                }
                return sb.toString();
            }
            if (sb.length() >= MAX_LINE_LENGTH) {
                throw new IOException("Received a line that is too long");
            }
            sb.append((char) c);
        }
        return null;
    }

    // ---------- sending ----------
    // Both send methods only put the text in a queue and return at once,
    // the writer thread does the real (possibly slow) network write.

    // normal message: it is always delivered, in order (HELLO, DIR, RESTART...)
    public void send(String message) {
        enqueue(new Outgoing(message, false));
    }

    // for messages like STATE where only the newest one matters:
    // if the network is slow and a newer one is already waiting, the older one is skipped
    public void sendReplaceable(String message) {
        enqueue(new Outgoing(message, true));
    }

    private void enqueue(Outgoing msg) {
        if (!open.get()) {
            return;
        }
        if (!queue.offer(msg)) {
            fail("The other side stopped receiving data");
        }
    }

    private void writeLoop() {
        try {
            while (open.get()) {
                Outgoing msg = queue.poll(HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                String text;
                if (msg == null) {
                    text = HEARTBEAT;   // nothing to send for a second -> prove we are alive
                } else {
                    // skip stale frames: only this thread removes items, so peek/poll are safe
                    while (msg.replaceable) {
                        Outgoing next = queue.peek();
                        if (next == null || !next.replaceable) {
                            break;
                        }
                        msg = queue.poll();
                    }
                    text = msg.text;
                }
                // one single write per message; we add "\n" ourselves so it is the same on every OS
                out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException e) {
            // close() woke us up, just leave
        } catch (IOException e) {
            fail("Could not send data" + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    // ---------- closing ----------
    // called when the connection breaks by itself. Reports it exactly once.
    private void fail(String reason) {
        if (open.compareAndSet(true, false)) {
            closeSocket();
            listener.onDisconnect(this, reason);
        }
    }

    // closing on purpose does NOT call onDisconnect
    public void close() {
        if (open.compareAndSet(true, false)) {
            closeSocket();
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }

    // Used by the host for a THIRD computer that connects while the game is already taken.
    // We tell it "REJECT FULL" and hang up. A plain close() could turn into a "connection
    // reset" that destroys our message before the other side reads it, so we first say
    // goodbye (shutdownOutput) and read away whatever it already sent (its HELLO).
    public static void rejectAndClose(Socket socket, String code) {
        Thread t = new Thread(() -> {
            try {
                socket.getOutputStream().write(("REJECT " + code + "\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                socket.shutdownOutput();
                socket.setSoTimeout(1000);
                InputStream is = socket.getInputStream();
                byte[] buf = new byte[512];
                long deadline = System.currentTimeMillis() + 1500;   // never wait longer than this
                while (System.currentTimeMillis() < deadline && is.read(buf) != -1) {
                    // throw the data away
                }
            } catch (IOException e) {
                // the other side is already gone, fine
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }, "reject-and-close");
        t.setDaemon(true);
        t.start();
    }
}
