package canal.lspiv.ext.example;

import canal.lspiv.ext.CanalAssist;
import canal.lspiv.ext.CanalProjectContext;

/**
 * 接入示例，不要打进宿主插件 jar。
 * isAvailable 保持 false，菜单维持灰显。
 * 真正实现时：返回 true，并在 run 中使用 context.projectRoot()。
 * 登记：META-INF/services/canal.lspiv.ext.CanalAssist 写本类全名，jar 放到 appli/app/canal/ext/。
 */
public final class AutoGridReserved implements CanalAssist {
  @Override
  public String id() {
    return "autogrid";
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public void run(CanalProjectContext context) {
    throw new UnsupportedOperationException("AutoGrid 尚未实现");
  }
}
