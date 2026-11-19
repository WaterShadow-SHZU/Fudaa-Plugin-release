package canal.lspiv.ext;

import java.io.File;

/** 后续 AutoGrid / 自动 IA / SA 选区实现可使用的只读上下文。 */
public interface CanalProjectContext {
  File projectRoot();

  File exeDirectory();

  void info(String message);

  void error(String message);
}
