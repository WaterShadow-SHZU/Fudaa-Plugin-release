package canal.lspiv;

import canal.lspiv.ext.CanalAssist;
import canal.lspiv.ext.CanalAssistRegistry;
import canal.lspiv.ext.CanalProjectContext;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowListener;
import java.io.File;
import java.lang.reflect.Method;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/** 在宿主菜单栏挂上 Canal。不改 Fudaa 源码，靠启动类在界面出现后注入。 */
public final class CanalMenuHook {
  private static volatile Object implementation;
  private static volatile boolean timerStarted;
  private static int missLogs;

  private CanalMenuHook() {}

  public static void start() {
    if (timerStarted) {
      return;
    }
    timerStarted = true;
    SwingUtilities.invokeLater(() -> {
      javax.swing.Timer timer = new javax.swing.Timer(800, e -> install());
      timer.setRepeats(true);
      timer.start();
      install();
    });
  }

  public static Object implementation() {
    Object impl = implementation;
    return impl != null ? impl : findImplementation();
  }

  public static File projectRoot() {
    Object impl = implementation();
    if (impl == null) {
      return null;
    }
    try {
      Object project = impl.getClass().getMethod("getCurrentProject").invoke(impl);
      if (project == null) {
        return null;
      }
      Object root = project.getClass().getMethod("getRoot").invoke(project);
      return root instanceof File ? (File) root : null;
    } catch (ReflectiveOperationException ex) {
      Log.error("读取当前工程失败", ex);
      return null;
    }
  }

  public static Component parent() {
    Object impl = implementation();
    if (impl == null) {
      return null;
    }
    try {
      Object parent = impl.getClass().getMethod("getParentComponent").invoke(impl);
      if (parent instanceof Component) {
        return (Component) parent;
      }
      Object frame = impl.getClass().getMethod("getFrame").invoke(impl);
      return frame instanceof Component ? (Component) frame : null;
    } catch (ReflectiveOperationException ex) {
      return null;
    }
  }

  static void applyBrand(Object impl) {
    try {
      Object info = impl.getClass().getMethod("informationsSoftware").invoke(null);
      info.getClass().getField("name").set(info, CanalBrand.NAME);
      info.getClass().getField("version").set(info, CanalBrand.VERSION);
      impl.getClass().getMethod("refreshTitle").invoke(impl);
      Object frame = impl.getClass().getMethod("getFrame").invoke(impl);
      if (frame instanceof java.awt.Frame) {
        java.awt.Image icon = CanalBrand.icon();
        if (icon != null) {
          ((java.awt.Frame) frame).setIconImage(icon);
        }
      }
    } catch (ReflectiveOperationException ex) {
      Log.error("设置 Canal 标识失败", ex);
    }
  }

  static void install() {
    Object impl = findImplementation();
    if (impl == null) {
      if (missLogs < 4) {
        missLogs++;
        Log.info("menu wait: " + describeWindows());
      }
      return;
    }
    implementation = impl;
    try {
      Method getBar = impl.getClass().getMethod("getMainMenuBar");
      Object barObj = getBar.invoke(impl);
      if (!(barObj instanceof JMenuBar)) {
        return;
      }
      applyBrand(impl);
      JMenuBar bar = (JMenuBar) barObj;
      for (int i = 0; i < bar.getMenuCount(); i++) {
        JMenu existing = bar.getMenu(i);
        if (existing != null && "mnCanal".equals(existing.getName())) {
          return;
        }
      }
      JMenu menu = buildMenu();
      menu.setName("mnCanal");
      bar.add(menu);
      bar.revalidate();
      bar.repaint();
      Log.info("Canal menu installed " + CanalBrand.TITLE);
    } catch (ReflectiveOperationException ex) {
      Log.error("注入 Canal 菜单失败", ex);
    }
  }

  private static JMenu buildMenu() {
    CanalAssistRegistry.load();
    JMenu menu = new JMenu("Canal");
    menu.add(item(CanalI18n.t("menu.exportCalib"), CanalDialogs::exportCalib));
    menu.add(item(CanalI18n.t("menu.exportCsv"), CanalDialogs::exportCsv));
    menu.add(item(CanalI18n.t("menu.exportRgb"), CanalDialogs::exportRgb));
    menu.add(item(CanalI18n.t("menu.exportRoi"), CanalDialogs::exportRoi));
    menu.add(item(CanalI18n.t("menu.grpDraft"), CanalDialogs::grpDraft));
    menu.addSeparator();
    menu.add(item(CanalI18n.t("menu.cores"), CanalDialogs::editCores));
    menu.add(languageMenu());
    menu.addSeparator();
    menu.add(reserved(CanalAssistRegistry.AUTOGRID, CanalI18n.t("menu.autogrid")));
    menu.add(reserved(CanalAssistRegistry.AUTO_IA, CanalI18n.t("menu.autoIa")));
    menu.add(reserved(CanalAssistRegistry.SA_ASSIST, CanalI18n.t("menu.sa")));
    return menu;
  }

  private static JMenu languageMenu() {
    JMenu menu = new JMenu(CanalI18n.t("menu.language"));
    menu.add(langItem("zh", CanalI18n.t("lang.zh")));
    menu.add(langItem("en", CanalI18n.t("lang.en")));
    menu.add(langItem("fr", CanalI18n.t("lang.fr")));
    return menu;
  }

  private static JMenuItem langItem(String code, String label) {
    JMenuItem item = new JMenuItem(label);
    if (code.equals(CanalI18n.code())) {
      item.setEnabled(false);
    }
    item.addActionListener(e -> CanalLanguage.choose(code));
    return item;
  }

  private static JMenuItem item(String label, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    item.addActionListener(e -> {
      try {
        action.run();
      } catch (RuntimeException ex) {
        Log.error(label, ex);
        CanalDialogs.error(ex.getMessage() == null ? ex.toString() : ex.getMessage());
      }
    });
    return item;
  }

  private static JMenuItem reserved(String id, String label) {
    CanalAssist assist = CanalAssistRegistry.find(id);
    JMenuItem item = new JMenuItem(label);
    if (assist == null) {
      item.setEnabled(false);
      item.setToolTipText(CanalI18n.t("tip.reserved") + " canal.lspiv.ext.CanalAssist id=" + id);
      return item;
    }
    item.setToolTipText("已加载扩展 " + assist.getClass().getName());
    item.addActionListener(e -> assist.run(context()));
    return item;
  }

  private static CanalProjectContext context() {
    return new CanalProjectContext() {
      @Override
      public File projectRoot() {
        return CanalMenuHook.projectRoot();
      }

      @Override
      public File exeDirectory() {
        return CanalHome.exeDir();
      }

      @Override
      public void info(String message) {
        CanalDialogs.info(message);
      }

      @Override
      public void error(String message) {
        CanalDialogs.error(message);
      }
    };
  }

  private static String describeWindows() {
    StringBuilder sb = new StringBuilder();
    for (Window window : Window.getWindows()) {
      sb.append(window.getClass().getName());
      for (WindowListener listener : window.getWindowListeners()) {
        if (listener != null) {
          sb.append('/').append(listener.getClass().getName());
        }
      }
      sb.append(';');
    }
    return sb.toString();
  }

  private static Object findImplementation() {
    for (Window window : Window.getWindows()) {
      if (window instanceof Frame) {
        for (WindowListener listener : ((Frame) window).getWindowListeners()) {
          if (isImpl(listener)) {
            return listener;
          }
        }
      }
      try {
        Method getter = window.getClass().getMethod("getImplementation");
        Object impl = getter.invoke(window);
        if (isImpl(impl)) {
          return impl;
        }
      } catch (ReflectiveOperationException ignored) {
        // not a BuApplication
      }
    }
    return null;
  }

  private static boolean isImpl(Object obj) {
    return obj != null && "org.fudaa.fudaa.piv.PivImplementation".equals(obj.getClass().getName());
  }
}
