package canal.lspiv;

import java.util.Arrays;

/**
 * appli.cfg 的主类。先挂菜单钩子，再进入原 Fudaa 主类。
 * {@code --canal-probe} 只写日志并退出，供启动链自检。
 */
public final class CanalBootstrap {
  private CanalBootstrap() {}

  public static void main(String[] args) {
    Log.info("bootstrap " + Arrays.toString(args));
    if (args != null) {
      for (String arg : args) {
        if ("--canal-probe".equals(arg)) {
          Log.info("PROBE_OK home=" + CanalHome.dir().getAbsolutePath());
          System.out.println("PROBE_OK");
          return;
        }
      }
    }
    try {
      Class<?> launcher = Class.forName("org.fudaa.fudaa.piv.io.PivExeLauncher");
      Log.info("PivExeLauncher " + launcher.getProtectionDomain().getCodeSource().getLocation());
    } catch (Throwable ex) {
      Log.error("PivExeLauncher 未装入", ex);
    }
    CanalMenuHook.start();
    org.fudaa.fudaa.piv.Piv.main(args);
  }
}
