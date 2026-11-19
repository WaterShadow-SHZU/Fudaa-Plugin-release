package canal.lspiv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** 最小 ustar + gzip。条目平铺在包根，无子目录。 */
public final class Ustar {
  private Ustar() {}

  public static void writeGzip(Path tarGz, Path[] files) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    for (Path file : files) {
      byte[] data = Files.readAllBytes(file);
      raw.write(header(file.getFileName().toString(), data.length));
      raw.write(data);
      int pad = (512 - (data.length % 512)) % 512;
      if (pad > 0) {
        raw.write(new byte[pad]);
      }
    }
    raw.write(new byte[1024]);
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tarGz))) {
      raw.writeTo(out);
    }
  }

  static byte[] header(String name, long size) {
    byte[] h = new byte[512];
    put(h, 0, name, 100);
    putOctal(h, 100, 0644, 8);
    putOctal(h, 108, 0, 8);
    putOctal(h, 116, 0, 8);
    putOctal(h, 124, size, 12);
    putOctal(h, 136, System.currentTimeMillis() / 1000L, 12);
    for (int i = 148; i < 156; i++) {
      h[i] = (byte) ' ';
    }
    h[156] = (byte) '0';
    put(h, 257, "ustar", 6);
    h[263] = (byte) '0';
    h[264] = (byte) '0';
    int sum = 0;
    for (byte b : h) {
      sum += b & 0xff;
    }
    String oct = String.format("%06o", sum);
    byte[] octBytes = oct.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(octBytes, 0, h, 148, 6);
    h[154] = 0;
    h[155] = (byte) ' ';
    return h;
  }

  private static void put(byte[] h, int off, String s, int len) {
    byte[] b = s.getBytes(StandardCharsets.UTF_8);
    int n = Math.min(len, b.length);
    System.arraycopy(b, 0, h, off, n);
  }

  private static void putOctal(byte[] h, int off, long value, int len) {
    String s = String.format("%0" + (len - 1) + "o", value);
    if (s.length() > len - 1) {
      s = s.substring(s.length() - (len - 1));
    }
    byte[] b = s.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(b, 0, h, off, b.length);
    h[off + len - 1] = 0;
  }
}
