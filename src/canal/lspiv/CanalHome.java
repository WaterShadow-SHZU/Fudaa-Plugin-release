package canal.lspiv;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/** 插件目录与宿主安装根。运行时 jar 位于 appli/app/canal/。 */
public final class CanalHome {
  private CanalHome() {}

  public static File dir() {
    try {
      URL url = CanalHome.class.getProtectionDomain().getCodeSource().getLocation();
      File loc = new File(url.toURI());
      return loc.isFile() ? loc.getParentFile() : loc;
    } catch (URISyntaxException ex) {
      return new File("canal");
    }
  }

  /** 安装根（canal 的上三级：app/appli/根）。 */
  public static File installRoot() {
    File canal = dir();
    File app = parent(canal);
    File appli = parent(app);
    return parent(appli);
  }

  public static File exeDir() {
    String prop = System.getProperty("piv.exe.path");
    if (prop != null && !prop.isBlank()) {
      File dir = new File(prop);
      if (new File(dir, "PIV.exe").isFile() || new File(dir, "transf_a.exe").isFile()) {
        return dir;
      }
    }
    File root = installRoot();
    if (root != null) {
      File exes = new File(root, "exes");
      if (exes.isDirectory()) {
        return exes;
      }
    }
    return null;
  }

  public static File exe(String name) {
    File dir = exeDir();
    if (dir == null) {
      return null;
    }
    File f = new File(dir, name);
    return f.isFile() ? f : null;
  }

  private static File parent(File f) {
    return f == null ? null : f.getParentFile();
  }
}
