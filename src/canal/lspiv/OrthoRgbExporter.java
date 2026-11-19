package canal.lspiv;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

/**
 * 用宿主 transf_a.exe 对 R/G/B 各做一次正射，合成 PNG。
 * 写到 img_ortho_rgb，不替换 img_transf 里的 PIV 用 PGM。
 */
public final class OrthoRgbExporter {
  private OrthoRgbExporter() {}

  public interface Progress {
    void onProgress(String message);
  }

  public static final class Request {
    public File projectRoot;
    public File outputDir;
    public File transfExe;
    public boolean preferStabilized;
    public int limit;
    public int ncore = 1;
    public AtomicBoolean cancel = new AtomicBoolean();
    public Progress progress = message -> { };
  }

  public static final class Result {
    public File outputDir;
    public File sidecar;
    public int images;
    public String summary;
  }

  public static Result export(Request req) throws IOException {
    if (req.projectRoot == null) {
      throw new IOException("未打开工程。");
    }
    ProjectData data = ProjectData.load(req.projectRoot);
    if (data.coeffFile == null) {
      throw new IOException("缺少 outputs.dir/coeff.dat。请先在 Fudaa 中完成正射（系数不打进定标包，但彩色正射需要它）。");
    }
    if (req.transfExe == null || !req.transfExe.isFile()) {
      throw new IOException("找不到 transf_a.exe。");
    }
    File outDir = req.outputDir != null ? req.outputDir : new File(req.projectRoot, "img_ortho_rgb");
    if (outDir.getName().equalsIgnoreCase("img_transf")) {
      throw new IOException("拒绝把彩色正射写入 img_transf，以免替换 PIV 用的 PGM。");
    }
    List<File> sources = ProjectData.colorSources(req.projectRoot, req.preferStabilized);
    if (sources.isEmpty() && req.preferStabilized) {
      sources = ProjectData.colorSources(req.projectRoot, false);
    }
    if (sources.isEmpty()) {
      throw new IOException("img_pgm（或 img_stab）中没有 bmp/jpg/png/tif。");
    }
    if (req.limit > 0 && sources.size() > req.limit) {
      sources = sources.subList(0, req.limit);
    }
    Files.createDirectories(outDir.toPath());
    int ncore = PivCores.clamp(req.ncore);
    List<String> written = new ArrayList<>();
    int index = 1;
    for (File src : sources) {
      if (req.cancel.get()) {
        break;
      }
      req.progress.onProgress("正射 RGB " + index + "/" + sources.size() + " " + src.getName());
      String name = String.format(Locale.ROOT, "image%04d_ortho.png", index);
      File png = new File(outDir, name);
      warpOne(req, data, src, png, ncore);
      written.add(name);
      index++;
    }
    File sidecar = new File(outDir, "ortho_rgb.json");
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"product\": \"ortho-rgb\",\n");
    sb.append("  \"replaces_piv_pgm\": false,\n");
    sb.append("  \"solver\": \"transf_a.exe\",\n");
    sb.append("  \"pgm_dir\": \"img_transf\",\n");
    sb.append("  \"rgb_dir\": \"").append(TextFiles.jsonEscape(outDir.getName())).append("\",\n");
    sb.append("  \"orientation\": \"row 0 is Ymin, same as solver PGM and grid contract\",\n");
    sb.append("  \"formula\": \"X=xmin+col*resolution, Y=ymin+row*resolution\",\n");
    sb.append("  \"xmin_m\": ").append(TextFiles.num(data.xmin)).append(",\n");
    sb.append("  \"ymin_m\": ").append(TextFiles.num(data.ymin)).append(",\n");
    sb.append("  \"xmax_m\": ").append(TextFiles.num(data.xmax)).append(",\n");
    sb.append("  \"ymax_m\": ").append(TextFiles.num(data.ymax)).append(",\n");
    sb.append("  \"resolution_m\": ").append(TextFiles.num(data.resolution)).append(",\n");
    sb.append("  \"images\": [");
    for (int i = 0; i < written.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append('"').append(written.get(i)).append('"');
    }
    sb.append("]\n}\n");
    TextFiles.writeLf(sidecar.toPath(), sb.toString());

    Result result = new Result();
    result.outputDir = outDir;
    result.sidecar = sidecar;
    result.images = written.size();
    result.summary = "彩色正射已写出（未改 img_transf）\n" + outDir.getAbsolutePath()
        + "\n张数 " + written.size()
        + (req.cancel.get() ? "（已取消，已完成的保留）" : "")
        + "\n几何与 transf_a.exe 灰度正射一致，PNG 为并行产物。";
    return result;
  }

  private static void warpOne(Request req, ProjectData data, File source, File png, int ncore) throws IOException {
    BufferedImage image = ImageIO.read(source);
    if (image == null) {
      throw new IOException("无法读取图像 " + source.getAbsolutePath());
    }
    int w = image.getWidth();
    int h = image.getHeight();
    if (w != data.srcWidth || h != data.srcHeight) {
      throw new IOException(source.getName() + " 为 " + w + "×" + h
          + "，img_ref 要求宽 " + data.srcWidth + " 高 " + data.srcHeight + "。");
    }
    Path tmp = Files.createTempDirectory("canal-ortho-");
    try {
      Path outputs = tmp.resolve("outputs.dir");
      Path pgmDir = tmp.resolve("img_pgm");
      Path transfDir = tmp.resolve("img_transf");
      Files.createDirectories(outputs);
      Files.createDirectories(pgmDir);
      Files.createDirectories(transfDir);
      Files.copy(data.coeffFile.toPath(), outputs.resolve("coeff.dat"));
      Files.copy(data.imgRefFile.toPath(), outputs.resolve("img_ref.dat"));
      Files.copy(data.hFile.toPath(), outputs.resolve("h.dat"));
      byte[] red = new byte[w * h];
      byte[] green = new byte[w * h];
      byte[] blue = new byte[w * h];
      int k = 0;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int rgb = image.getRGB(x, y);
          red[k] = (byte) ((rgb >> 16) & 0xff);
          green[k] = (byte) ((rgb >> 8) & 0xff);
          blue[k] = (byte) (rgb & 0xff);
          k++;
        }
      }
      int[][] rr = runChannel(req, tmp, pgmDir, transfDir, red, w, h, ncore);
      int[][] gg = runChannel(req, tmp, pgmDir, transfDir, green, w, h, ncore);
      int[][] bb = runChannel(req, tmp, pgmDir, transfDir, blue, w, h, ncore);
      int ow = rr[0].length;
      int oh = rr.length;
      if (gg.length != oh || bb.length != oh || gg[0].length != ow || bb[0].length != ow) {
        throw new IOException("三分量正射尺寸不一致。");
      }
      BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < oh; y++) {
        for (int x = 0; x < ow; x++) {
          int rgb = ((rr[y][x] & 0xff) << 16) | ((gg[y][x] & 0xff) << 8) | (bb[y][x] & 0xff);
          out.setRGB(x, y, rgb);
        }
      }
      if (!ImageIO.write(out, "png", png)) {
        throw new IOException("ImageIO 无法写 PNG：" + png.getAbsolutePath());
      }
    } finally {
      deleteTree(tmp.toFile());
    }
  }

  private static int[][] runChannel(Request req, Path tmp, Path pgmDir, Path transfDir, byte[] plane,
      int w, int h, int ncore) throws IOException {
    Path in = pgmDir.resolve("image1.pgm");
    // 2027 transf_a.exe 只接受 P2。P5 会报 read_PGM_P2 is not a PGM P2 image。
    writeP2(in, plane, w, h);
    File produced = transfDir.resolve("image1_transf.pgm").toFile();
    if (produced.exists() && !produced.delete()) {
      throw new IOException("无法清理临时正射结果。");
    }
    ProcessBuilder pb = new ProcessBuilder(req.transfExe.getAbsolutePath(), Integer.toString(ncore));
    pb.directory(tmp.toFile());
    pb.redirectErrorStream(true);
    Process proc = pb.start();
    String output;
    try (InputStream inStream = proc.getInputStream()) {
      output = new String(inStream.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
    }
    int code;
    try {
      code = proc.waitFor();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      proc.destroyForcibly();
      throw new IOException("transf_a 被中断。", ex);
    }
    if (code != 0 || !produced.isFile() || produced.length() == 0) {
      throw new IOException("transf_a.exe 失败，exit=" + code + "\n" + tail(output));
    }
    int[][] gray = readPgm(produced.toPath());
    if (!produced.delete()) {
      produced.deleteOnExit();
    }
    return gray;
  }

  static void writeP2(Path path, byte[] samples, int width, int height) throws IOException {
    if (samples.length != width * height) {
      throw new IOException("PGM 像素数不匹配。");
    }
    // 与宿主 PivPGMWriter 默认 P2 相同：一行一个扫描行，空格分隔。
    StringBuilder sb = new StringBuilder(width * height * 4);
    sb.append("P2\n").append(width).append(' ').append(height).append("\n255\n");
    int k = 0;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        sb.append(samples[k++] & 0xff).append(' ');
      }
      sb.append('\n');
    }
    Files.writeString(path, sb.toString(), java.nio.charset.StandardCharsets.US_ASCII);
  }

  static int[][] readPgm(Path path) throws IOException {
    byte[] raw = Files.readAllBytes(path);
    if (raw.length < 3 || raw[0] != 'P' || (raw[1] != '2' && raw[1] != '5')) {
      throw new IOException("不是 P2/P5：" + path);
    }
    boolean binary = raw[1] == '5';
    int i = 2;
    int[] header = new int[3];
    for (int t = 0; t < 3; t++) {
      i = skipWsAndComments(raw, i);
      int start = i;
      while (i < raw.length && raw[i] >= '0' && raw[i] <= '9') {
        i++;
      }
      if (start == i) {
        throw new IOException("PGM 头损坏：" + path);
      }
      header[t] = Integer.parseInt(new String(raw, start, i - start, java.nio.charset.StandardCharsets.US_ASCII));
    }
    int width = header[0];
    int height = header[1];
    int maxval = header[2];
    if (width < 1 || height < 1) {
      throw new IOException("PGM 尺寸非法。");
    }
    int[][] img = new int[height][width];
    if (binary) {
      if (i < raw.length && (raw[i] == ' ' || raw[i] == '\n' || raw[i] == '\r' || raw[i] == '\t')) {
        i++;
      }
      if (raw.length < i + (long) width * height) {
        throw new IOException("P5 数据截断。");
      }
      int k = i;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int v = raw[k++] & 0xff;
          img[y][x] = maxval == 255 ? v : v * 255 / maxval;
        }
      }
      return img;
    }
    int k = i;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        k = skipWsAndComments(raw, k);
        int start = k;
        while (k < raw.length && raw[k] >= '0' && raw[k] <= '9') {
          k++;
        }
        int v = Integer.parseInt(new String(raw, start, k - start, java.nio.charset.StandardCharsets.US_ASCII));
        img[y][x] = maxval == 255 ? v : v * 255 / maxval;
      }
    }
    return img;
  }

  private static int skipWsAndComments(byte[] raw, int i) {
    while (i < raw.length) {
      byte b = raw[i];
      if (b == '#') {
        while (i < raw.length && raw[i] != '\n') {
          i++;
        }
        continue;
      }
      if (b == ' ' || b == '\n' || b == '\r' || b == '\t') {
        i++;
        continue;
      }
      break;
    }
    return i;
  }

  private static String tail(String s) {
    if (s == null) {
      return "";
    }
    String t = s.replace('\r', ' ').trim();
    return t.length() > 800 ? t.substring(t.length() - 800) : t;
  }

  private static void deleteTree(File f) {
    File[] kids = f.listFiles();
    if (kids != null) {
      for (File kid : kids) {
        deleteTree(kid);
      }
    }
    f.delete();
  }
}
