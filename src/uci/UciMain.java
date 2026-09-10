package uci;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Standard chess UCI entry point; intentionally independent of the Swing GUI. */
public final class UciMain {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try (UciController controller = new UciController(
                new InputStreamReader(System.in, StandardCharsets.UTF_8),
                new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8))) {
            controller.run();
        }
    }
}
