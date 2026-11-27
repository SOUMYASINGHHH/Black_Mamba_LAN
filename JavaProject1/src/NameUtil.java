// Cleans up player names. Names travel inside our text messages, where the characters
// | ; , have a special meaning, so a name must never contain them.
// The HOST runs every name through here, even the one that arrives from the client,
// because the host must never trust what another computer sends.
public class NameUtil {

    public static final int MAX_LENGTH = 12;

    // besides letters, digits and spaces, only these symbols are allowed
    private static final String ALLOWED_SYMBOLS = "_-.!'@#";

    // keeps only safe characters, squeezes extra spaces, cuts to MAX_LENGTH.
    // If nothing is left, the fallback name is returned.
    public static String sanitize(String raw, String fallback) {
        StringBuilder sb = new StringBuilder();
        if (raw != null) {
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                int type = Character.getType(c);
                boolean letterOrDigit = Character.isLetterOrDigit(c);
                // accents and vowel signs (for example in Hindi) are "marks", keep them too
                boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
                if (letterOrDigit || mark || c == ' ' || ALLOWED_SYMBOLS.indexOf(c) >= 0) {
                    sb.append(c);
                }
            }
        }
        String name = sb.toString().trim().replaceAll(" {2,}", " ");
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH).trim();
        }
        return name.isEmpty() ? fallback : name;
    }

    // If name is the same as the name already taken (ignoring upper/lower case),
    // add " 2", " 3"... until it is different. The result still fits in MAX_LENGTH.
    public static String makeUnique(String name, String taken) {
        if (!name.equalsIgnoreCase(taken)) {
            return name;
        }
        for (int n = 2; ; n++) {
            String suffix = " " + n;
            String base = name;
            if (base.length() + suffix.length() > MAX_LENGTH) {
                base = base.substring(0, MAX_LENGTH - suffix.length()).trim();
            }
            String candidate = base + suffix;
            if (!candidate.equalsIgnoreCase(taken)) {
                return candidate;
            }
        }
    }
}
