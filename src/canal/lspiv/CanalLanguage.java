package canal.lspiv;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

/** 把宿主首选项 locale.language 设为 zh / en / fr，并可选重启。 */
public final class CanalLanguage {
  private CanalLanguage() {}

  public static void choose(String code) {
    if (!"zh".equals(code) && !"en".equals(code) && !"fr".equals(code)) {
      return;
    }
    try {
      Class<?> prefsClass = Class.forName("com.memoire.bu.BuPreferences");
      Object prefs = prefsClass.getField("BU").get(null);
      prefsClass.getMethod("putStringProperty", String.class, String.class)
          .invoke(prefs, "locale.language", code);
      // Missing Chinese entries fall back to English, not to a half-translated string.
      prefsClass.getMethod("putStringProperty", String.class, String.class)
          .invoke(prefs, "translation.fallBackOnEnglish", "zh".equals(code) ? "true" : "false");
      prefsClass.getMethod("writeIniFile").invoke(prefs);
    } catch (ReflectiveOperationException ex) {
      Log.error("保存语言失败", ex);
      CanalDialogs.error(ex.getMessage() == null ? ex.toString() : ex.getMessage());
      return;
    }
    Component parent = CanalMenuHook.parent();
    int answer = JOptionPane.showConfirmDialog(parent, CanalI18n.t("lang.restart"),
        CanalBrand.NAME, JOptionPane.YES_NO_OPTION);
    if (answer == JOptionPane.YES_OPTION) {
      relaunch();
    }
  }

  static void relaunch() {
    File root = CanalHome.installRoot();
    File exe = root == null ? null : new File(new File(root, "appli"), "appli.exe");
    if (exe == null || !exe.isFile()) {
      CanalDialogs.info(CanalI18n.t("lang.saved"));
      return;
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(exe.getAbsolutePath());
    cmd.add("--exepath");
    cmd.add(new File(root, "exes").getAbsolutePath());
    cmd.add("--docpath");
    cmd.add(new File(root, "doc").getAbsolutePath());
    cmd.add("--templatepath");
    cmd.add(new File(root, "templates").getAbsolutePath());
    try {
      new ProcessBuilder(cmd).directory(root).start();
      System.exit(0);
    } catch (Exception ex) {
      Log.error("重启失败", ex);
      CanalDialogs.info(CanalI18n.t("lang.saved"));
    }
  }
}
