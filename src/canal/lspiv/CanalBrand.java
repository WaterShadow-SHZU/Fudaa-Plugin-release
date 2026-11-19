package canal.lspiv;

import java.awt.Image;
import java.awt.Toolkit;
import java.net.URL;

/** 产品标识权威源（NAME/VERSION/TITLE）。改 VERSION 后同步 PatchPivLauncher 与 build/inject 脚本。 */
public final class CanalBrand {
  public static final String NAME = "Fudaa-LSPIV";
  public static final String VERSION = "1.11.3 Canal 1.1";
  public static final String TITLE = NAME + " " + VERSION;

  private static Image icon;

  private CanalBrand() {}

  public static Image icon() {
    if (icon != null) {
      return icon;
    }
    URL url = CanalBrand.class.getResource("canal-icon.png");
    if (url == null) {
      url = CanalBrand.class.getResource("/org/fudaa/fudaa/piv/icone-lspiv.png");
    }
    if (url == null) {
      return null;
    }
    icon = Toolkit.getDefaultToolkit().createImage(url);
    return icon;
  }
}
