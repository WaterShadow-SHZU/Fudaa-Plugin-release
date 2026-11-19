package canal.lspiv;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TextFiles {
  private TextFiles() {}

  public static List<String> readLines(Path path) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;
      boolean first = true;
      while ((line = r.readLine()) != null) {
        if (first) {
          line = stripBom(line);
          first = false;
        }
        lines.add(line);
      }
    }
    return lines;
  }

  public static void writeLf(Path path, String text) throws IOException {
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    if (!normalized.endsWith("\n")) {
      normalized = normalized + "\n";
    }
    Files.writeString(path, normalized, StandardCharsets.UTF_8);
  }

  public static String stripBom(String s) {
    if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
      return s.substring(1);
    }
    return s;
  }

  public static String sha256Hex(Path path) throws IOException {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format(Locale.ROOT, "%02x", b));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IOException(ex);
    }
  }

  public static String jsonEscape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\':
          sb.append("\\\\");
          break;
        case '"':
          sb.append("\\\"");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  public static String num(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      return "null";
    }
    String s = String.format(Locale.ROOT, "%.10f", v);
    int dot = s.indexOf('.');
    if (dot >= 0) {
      int end = s.length();
      while (end > dot + 1 && s.charAt(end - 1) == '0') {
        end--;
      }
      if (end == dot + 1) {
        end = dot;
      }
      s = s.substring(0, end);
    }
    if ("-0".equals(s)) {
      return "0";
    }
    return s;
  }
}
