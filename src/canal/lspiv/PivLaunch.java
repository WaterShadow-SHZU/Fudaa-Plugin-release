package canal.lspiv;

import java.io.File;
import java.util.Locale;

/**
 * 由补丁后的 PivExeLauncher.computePiv 调用。
 * 只给 PIV.exe 补第一个参数（ncore），其它求解器命令原样返回。
 */
public final class PivLaunch {
  private static volatile int augmentCount;

  private PivLaunch() {}

  public static int augmentCount() {
    return augmentCount;
  }

  /** 记录补丁命中。栈上与 augment 分开，避免改 FuLib 签名。 */
  public static void record(String[] cmd, File workDir) {
    if (cmd == null || cmd.length == 0 || !isPiv(cmd[0])) {
      return;
    }
    int n = PivCores.resolve();
    String cwd = workDir == null ? "" : workDir.getAbsolutePath();
    Log.info("PIV launch workDir=" + cwd + " argv0=" + cmd[0] + " incomingArgs=" + (cmd.length - 1) + " ncore=" + n);
  }

  public static String[] augment(String[] cmd) {
    if (cmd == null || cmd.length == 0 || !isPiv(cmd[0])) {
      return cmd;
    }
    if (cmd.length >= 2 && isCoreToken(cmd[1])) {
      return cmd;
    }
    int n = PivCores.resolve();
    String[] out = new String[cmd.length + 1];
    out[0] = cmd[0];
    out[1] = Integer.toString(n);
    if (cmd.length > 1) {
      System.arraycopy(cmd, 1, out, 2, cmd.length - 1);
    }
    augmentCount++;
    Log.info("PIV argv patched to ncore=" + n + " (was " + cmd.length + " token(s), now " + out.length + ")");
    return out;
  }

  static boolean isPiv(String path) {
    if (path == null) {
      return false;
    }
    String name = new File(path).getName();
    return name.equalsIgnoreCase("PIV.exe") || name.equalsIgnoreCase("PIV");
  }

  static boolean isCoreToken(String token) {
    if (token == null) {
      return false;
    }
    return token.matches("\\d{1,2}");
  }

  /** 供交付说明引用的说明，不参与调用。 */
  public static String contractNote() {
    return String.format(Locale.ROOT,
        "PIV.exe OpenMP build reads ncore with read(param3,'(i2)'); missing argument leaves ncore=1.");
  }
}
