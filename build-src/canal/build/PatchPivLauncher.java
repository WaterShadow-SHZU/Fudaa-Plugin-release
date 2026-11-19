package canal.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 在 PivExeLauncher.computePiv 调用 FuLib.runProgram(String[], File) 之前插入 ncore 参数。
 * 只改这一处。其它求解器调用保持原样。
 */
public final class PatchPivLauncher implements Opcodes {
  static final String BRAND_NAME = "Fudaa-LSPIV";
  static final String BRAND_VERSION = "1.11.3 Canal 1.1";
  private static final String LAUNCHER = "org/fudaa/fudaa/piv/io/PivExeLauncher.class";
  private static final String IMPL = "org/fudaa/fudaa/piv/PivImplementation.class";
  private static final String PIV = "org/fudaa/fudaa/piv/Piv.class";

  private PatchPivLauncher() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("Usage: PatchPivLauncher <fudaa-lspiv.jar> <piv-core-fix.jar>");
      System.exit(2);
    }
    Path inJar = Path.of(args[0]);
    Path outJar = Path.of(args[1]);
    byte[] launcher = readEntry(inJar, LAUNCHER);
    byte[] impl = readEntry(inJar, IMPL);
    byte[] piv = readEntry(inJar, PIV);
    byte[] patchedLauncher = patchLauncher(launcher);
    byte[] patchedImpl = patchBrand(impl);
    byte[] patchedPiv = replaceStrings(piv);
    String launcherUtf = new String(patchedLauncher, java.nio.charset.StandardCharsets.ISO_8859_1);
    String implUtf = new String(patchedImpl, java.nio.charset.StandardCharsets.ISO_8859_1);
    if (!launcherUtf.contains("canal/lspiv/PivLaunch")) {
      throw new IOException("补丁未写入 PivLaunch 调用。");
    }
    if (!implUtf.contains(BRAND_NAME) || !implUtf.contains(BRAND_VERSION) || !implUtf.contains("zh,en,fr")) {
      throw new IOException("补丁未写入 Canal 名称、版本或语言列表。");
    }
    if (!new String(patchedPiv, java.nio.charset.StandardCharsets.ISO_8859_1).contains("zh,en,fr")) {
      throw new IOException("Piv.main 未放开中文语言。");
    }
    Files.createDirectories(outJar.getParent());
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outJar))) {
      writeEntry(out, LAUNCHER, patchedLauncher);
      writeEntry(out, IMPL, patchedImpl);
      writeEntry(out, PIV, patchedPiv);
    }
    System.out.println("patched " + outJar);
  }

  private static byte[] readEntry(Path jarPath, String name) throws IOException {
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      JarEntry entry = jar.getJarEntry(name);
      if (entry == null) {
        throw new IOException("jar 中没有 " + name);
      }
      try (InputStream in = jar.getInputStream(entry)) {
        return in.readAllBytes();
      }
    }
  }

  private static void writeEntry(JarOutputStream out, String name, byte[] bytes) throws IOException {
    out.putNextEntry(new JarEntry(name));
    out.write(bytes);
    out.closeEntry();
  }

  static byte[] patchBrand(byte[] original) {
    ClassReader reader = new ClassReader(original);
    ClassWriter writer = new ClassWriter(0);
    reader.accept(new ClassVisitor(ASM9, writer) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!"<clinit>".equals(name)) {
          return mv;
        }
        return new BrandVisitor(mv);
      }
    }, 0);
    return writer.toByteArray();
  }

  static final class BrandVisitor extends MethodVisitor {
    private Object held;
    private boolean holding;

    BrandVisitor(MethodVisitor mv) {
      super(ASM9, mv);
    }

    private void flush() {
      if (holding) {
        super.visitLdcInsn(held);
        holding = false;
      }
    }

    @Override
    public void visitLdcInsn(Object value) {
      flush();
      if (value instanceof String) {
        held = value;
        holding = true;
        return;
      }
      super.visitLdcInsn(value);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      if (holding && opcode == PUTFIELD && "com/memoire/bu/BuInformationsSoftware".equals(owner)) {
        if ("name".equals(name) && "LSPIV".equals(held)) {
          held = BRAND_NAME;
        } else if ("version".equals(name) && held instanceof String
            && ((String) held).matches("\\d+\\.\\d+\\.\\d+")) {
          held = BRAND_VERSION;
        } else if ("languages".equals(name) && "en,fr".equals(held)) {
          held = "zh,en,fr";
        }
      }
      flush();
      super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitInsn(int opcode) {
      flush();
      super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      flush();
      super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
      flush();
      super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      flush();
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
      flush();
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
      flush();
      super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLabel(org.objectweb.asm.Label label) {
      flush();
      super.visitLabel(label);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      flush();
      super.visitMaxs(maxStack, maxLocals);
    }

    @Override
    public void visitEnd() {
      flush();
      super.visitEnd();
    }
  }

  static byte[] replaceStrings(byte[] original) {
    ClassReader reader = new ClassReader(original);
    ClassWriter writer = new ClassWriter(0);
    reader.accept(new ClassVisitor(ASM9, writer) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
          @Override
          public void visitLdcInsn(Object value) {
            if ("en, fr".equals(value)) {
              value = "zh,en,fr";
            }
            super.visitLdcInsn(value);
          }
        };
      }
    }, 0);
    return writer.toByteArray();
  }

  static byte[] patchLauncher(byte[] original) {
    ClassReader reader = new ClassReader(original);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
      @Override
      protected String getCommonSuperClass(String type1, String type2) {
        return "java/lang/Object";
      }
    };
    reader.accept(new ClassVisitor(ASM9, writer) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!"computePiv".equals(name)) {
          return mv;
        }
        return new MethodVisitor(ASM9, mv) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
            if (opcode == INVOKESTATIC
                && "com/memoire/fu/FuLib".equals(owner)
                && "runProgram".equals(mName)
                && "([Ljava/lang/String;Ljava/io/File;)Ljava/lang/String;".equals(desc)) {
              // stack: [String[], File]
              super.visitInsn(DUP2);
              super.visitMethodInsn(INVOKESTATIC, "canal/lspiv/PivLaunch", "record",
                  "([Ljava/lang/String;Ljava/io/File;)V", false);
              super.visitInsn(SWAP);
              super.visitMethodInsn(INVOKESTATIC, "canal/lspiv/PivLaunch", "augment",
                  "([Ljava/lang/String;)[Ljava/lang/String;", false);
              super.visitInsn(SWAP);
            }
            super.visitMethodInsn(opcode, owner, mName, desc, itf);
          }
        };
      }
    }, ClassReader.SKIP_FRAMES);
    return writer.toByteArray();
  }
}
