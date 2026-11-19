package canal.lspiv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class Log {
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private Log() {}

  public static void info(String message) {
    write("INFO", message, null);
  }

  public static void error(String message, Throwable ex) {
    write("ERROR", message, ex);
  }

  public static File file() {
    return new File(CanalHome.dir(), "canal.log");
  }

  private static synchronized void write(String level, String message, Throwable ex) {
    File f = file();
    File parent = f.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
      w.print(OffsetDateTime.now().format(TS));
      w.print(' ');
      w.print(level);
      w.print(' ');
      w.println(message == null ? "" : message);
      if (ex != null) {
        ex.printStackTrace(w);
      }
    } catch (IOException ignored) {
      System.err.println("canal log failed: " + message);
    }
  }
}
