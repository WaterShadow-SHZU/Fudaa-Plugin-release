package canal.lspiv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * PIV.exe 的 OpenMP 核数。Fortran 用 character(len=2) / 格式 i2 读取，合法范围 1–99。
 * 未传入时求解器把 ncore 留在 1。
 */
public final class PivCores {
  public static final int MIN = 1;
  public static final int MAX = 99;
  public static final String PROP = "canal.piv.ncore";

  private PivCores() {}

  public static File file() {
    return new File(CanalHome.dir(), "piv-ncore.properties");
  }

  public static int available() {
    return clamp(Runtime.getRuntime().availableProcessors());
  }

  public static int resolve() {
    String sys = System.getProperty(PROP);
    if (sys != null && !sys.isBlank()) {
      try {
        return clamp(Integer.parseInt(sys.trim()));
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    File f = file();
    if (f.isFile()) {
      Properties p = new Properties();
      try (FileInputStream in = new FileInputStream(f)) {
        p.load(in);
        String v = p.getProperty("ncore");
        if (v != null && !v.isBlank()) {
          return clamp(Integer.parseInt(v.trim()));
        }
      } catch (IOException | NumberFormatException ignored) {
        // fall through
      }
    }
    return available();
  }

  public static void save(int ncore) throws IOException {
    int n = clamp(ncore);
    Properties p = new Properties();
    p.setProperty("ncore", Integer.toString(n));
    File f = file();
    File parent = f.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    try (FileOutputStream out = new FileOutputStream(f)) {
      p.store(out, "PIV.exe OpenMP thread count passed as the first argument. Range 1-99.");
    }
  }

  public static int clamp(int n) {
    if (n < MIN) {
      return MIN;
    }
    if (n > MAX) {
      return MAX;
    }
    return n;
  }
}
