package canal.lspiv.ext;

/**
 * 预留扩展点。稳定 id：
 * <ul>
 * <li>{@code autogrid} 自动网格</li>
 * <li>{@code auto-ia} 自动 IA</li>
 * <li>{@code sa-assist} SA 选区辅助</li>
 * </ul>
 * 把实现打成 jar，在 {@code META-INF/services/canal.lspiv.ext.CanalAssist} 登记类名，
 * 放到 {@code appli/app/canal/ext/}，重启 Fudaa。{@link #isAvailable()} 为 true 时对应菜单才可点。
 * 本包不提供业务实现。
 */
public interface CanalAssist {
  String id();

  boolean isAvailable();

  void run(CanalProjectContext context);
}
