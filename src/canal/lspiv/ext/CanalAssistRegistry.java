package canal.lspiv.ext;

import canal.lspiv.CanalHome;
import canal.lspiv.Log;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public final class CanalAssistRegistry {
  public static final String AUTOGRID = "autogrid";
  public static final String AUTO_IA = "auto-ia";
  public static final String SA_ASSIST = "sa-assist";

  private static URLClassLoader extLoader;
  private static List<CanalAssist> cached = List.of();

  private CanalAssistRegistry() {}

  public static synchronized List<CanalAssist> load() {
    File ext = new File(CanalHome.dir(), "ext");
    File[] jars = ext.isDirectory() ? ext.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".jar")) : null;
    if (jars == null || jars.length == 0) {
      cached = List.of();
      return cached;
    }
    try {
      URL[] urls = new URL[jars.length];
      for (int i = 0; i < jars.length; i++) {
        urls[i] = jars[i].toURI().toURL();
      }
      if (extLoader != null) {
        extLoader.close();
      }
      extLoader = new URLClassLoader(urls, CanalAssist.class.getClassLoader());
      List<CanalAssist> found = new ArrayList<>();
      for (CanalAssist assist : ServiceLoader.load(CanalAssist.class, extLoader)) {
        found.add(assist);
      }
      cached = Collections.unmodifiableList(found);
      Log.info("CanalAssist providers: " + cached.size());
      return cached;
    } catch (Exception ex) {
      Log.error("加载 canal/ext 失败", ex);
      cached = List.of();
      return cached;
    }
  }

  public static CanalAssist find(String id) {
    for (CanalAssist assist : cached) {
      if (id.equals(assist.id()) && assist.isAvailable()) {
        return assist;
      }
    }
    return null;
  }
}
