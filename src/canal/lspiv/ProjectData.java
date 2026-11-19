package canal.lspiv;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从已保存的 Fudaa 工程目录读取定标契约所需文件。不改 img_transf。 */
public final class ProjectData {
  private static final Pattern GRP_V2 = Pattern.compile("(?i)^GRP\\s+V2\\.0\\s+(\\d+)\\s+(\\d+)\\s*$");

  public final File root;
  public final File outputs;
  public final File grpFile;
  public final File imgRefFile;
  public final File pivParamFile;
  public final File gridFile;
  public final File hFile;
  public final File coeffFile;
  public final double xmin;
  public final double ymin;
  public final double xmax;
  public final double ymax;
  public final double resolution;
  public final int srcHeight;
  public final int srcWidth;
  public final double timeInterval;
  public final int underSampling;
  public final int timeIntervalLine;
  public final List<String> pivParamLines;
  public final List<GridPoint> grid;
  public final List<GrpPoint> grpPoints;
  public final boolean grpV2;
  public final int grpWidth;
  public final int grpHeight;
  public final double waterLevel;

  private ProjectData(File root, File outputs, File grpFile, File imgRefFile, File pivParamFile, File gridFile,
      File hFile, File coeffFile, double xmin, double ymin, double xmax, double ymax, double resolution,
      int srcHeight, int srcWidth, double timeInterval, int underSampling, int timeIntervalLine,
      List<String> pivParamLines, List<GridPoint> grid, List<GrpPoint> grpPoints, boolean grpV2, int grpWidth,
      int grpHeight, double waterLevel) {
    this.root = root;
    this.outputs = outputs;
    this.grpFile = grpFile;
    this.imgRefFile = imgRefFile;
    this.pivParamFile = pivParamFile;
    this.gridFile = gridFile;
    this.hFile = hFile;
    this.coeffFile = coeffFile;
    this.xmin = xmin;
    this.ymin = ymin;
    this.xmax = xmax;
    this.ymax = ymax;
    this.resolution = resolution;
    this.srcHeight = srcHeight;
    this.srcWidth = srcWidth;
    this.timeInterval = timeInterval;
    this.underSampling = underSampling;
    this.timeIntervalLine = timeIntervalLine;
    this.pivParamLines = pivParamLines;
    this.grid = grid;
    this.grpPoints = grpPoints;
    this.grpV2 = grpV2;
    this.grpWidth = grpWidth;
    this.grpHeight = grpHeight;
    this.waterLevel = waterLevel;
  }

  public static ProjectData load(File root) throws IOException {
    if (root == null || !root.isDirectory()) {
      throw new IOException("工程目录不存在。");
    }
    File outputs = new File(root, "outputs.dir");
    File grp = require(new File(outputs, "GRP.dat"));
    File imgRef = require(new File(outputs, "img_ref.dat"));
    File piv = require(new File(outputs, "PIV_param.dat"));
    File grid = require(new File(outputs, "grid.dat"));
    File h = require(new File(outputs, "h.dat"));
    File coeff = new File(outputs, "coeff.dat");

    ImgRef ref = parseImgRef(imgRef.toPath());
    PivParam pp = parsePivParam(piv.toPath());
    List<GridPoint> points = parseGrid(grid.toPath());
    Grp grpData = parseGrp(grp.toPath());
    double water = parseH(h.toPath());
    if (points.isEmpty()) {
      throw new IOException("grid.dat 没有有效点。");
    }
    if (grpData.points.isEmpty()) {
      throw new IOException("GRP.dat 没有控制点。");
    }
    return new ProjectData(root, outputs, grp, imgRef, piv, grid, h, coeff.isFile() ? coeff : null,
        ref.xmin, ref.ymin, ref.xmax, ref.ymax, ref.resolution, ref.height, ref.width,
        pp.timeInterval, pp.underSampling, pp.dataLine, pp.lines, points, grpData.points,
        grpData.v2, grpData.width, grpData.height, water);
  }

  public int cameraWidth() {
    return srcWidth;
  }

  public int cameraHeight() {
    return srcHeight;
  }

  public static final class GridPoint {
    public final int col;
    public final int row;

    public GridPoint(int col, int row) {
      this.col = col;
      this.row = row;
    }

    public double x(ProjectData d) {
      return d.xmin + col * d.resolution;
    }

    public double y(ProjectData d) {
      return d.ymin + row * d.resolution;
    }
  }

  public static final class GrpPoint {
    public final double x;
    public final double y;
    public final double z;
    public final String iToken;
    public final String jToken;
    public final String raw;

    public GrpPoint(double x, double y, double z, String iToken, String jToken, String raw) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.iToken = iToken;
      this.jToken = jToken;
      this.raw = raw;
    }
  }

  public String grpHeader() {
    return "GRP V2.0 " + cameraWidth() + " " + cameraHeight();
  }

  public String renderGrp() {
    StringBuilder sb = new StringBuilder();
    sb.append(grpHeader()).append('\n');
    sb.append(grpPoints.size()).append('\n');
    sb.append("X  Y  Z  i  j").append('\n');
    for (GrpPoint p : grpPoints) {
      sb.append(p.raw).append('\n');
    }
    return sb.toString();
  }

  public String renderPivParam(double timeInterval, int frameEvery) {
    List<String> lines = new ArrayList<>(pivParamLines);
    if (timeIntervalLine < 0 || timeIntervalLine >= lines.size()) {
      throw new IllegalStateException("PIV_param.dat 找不到 Time interval 数据行。");
    }
    String raw = lines.get(timeIntervalLine).trim();
    String[] parts = raw.isEmpty() ? new String[0] : raw.split("\\s+");
    StringBuilder joined = new StringBuilder();
    joined.append(String.format(Locale.US, "%.7f", timeInterval));
    joined.append(' ').append(frameEvery);
    for (int i = 2; i < parts.length; i++) {
      joined.append(' ').append(parts[i]);
    }
    lines.set(timeIntervalLine, joined.toString());
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append('\n');
    }
    return sb.toString();
  }

  public static int inferFps(double timeInterval, int frameEvery) {
    if (timeInterval <= 0 || frameEvery < 1) {
      return 0;
    }
    double fps = frameEvery / timeInterval;
    int rounded = (int) Math.round(fps);
    if (rounded < 1) {
      return 0;
    }
    if (Math.abs(fps - rounded) > 0.02 * Math.max(1.0, fps)) {
      return 0;
    }
    return rounded;
  }

  public static List<File> colorSources(File root, boolean preferStab) {
    File dir = new File(root, preferStab ? "img_stab" : "img_pgm");
    if (!dir.isDirectory()) {
      return List.of();
    }
    File[] files = dir.listFiles(f -> {
      if (!f.isFile()) {
        return false;
      }
      String n = f.getName().toLowerCase(Locale.ROOT);
      return n.endsWith(".bmp") || n.endsWith(".jpg") || n.endsWith(".jpeg")
          || n.endsWith(".png") || n.endsWith(".tif") || n.endsWith(".tiff");
    });
    if (files == null || files.length == 0) {
      return List.of();
    }
    Arrays.sort(files, Comparator.comparing(File::getName));
    return List.of(files);
  }

  private static File require(File f) throws IOException {
    if (!f.isFile()) {
      throw new IOException("缺少 " + f.getAbsolutePath() + "。请先在 Fudaa 中完成对应步骤并写出 outputs.dir。");
    }
    return f;
  }

  private static final class ImgRef {
    double xmin, ymin, xmax, ymax, resolution;
    int height, width;
  }

  private static ImgRef parseImgRef(Path path) throws IOException {
    List<String> lines = nonempty(TextFiles.readLines(path));
    ImgRef ref = new ImgRef();
    boolean sawSize = false;
    for (int i = 0; i < lines.size(); i++) {
      String key = lines.get(i).toLowerCase(Locale.ROOT);
      if (key.contains("xmin") && i + 1 < lines.size()) {
        double[] xy = twoDoubles(lines.get(++i), "img_ref xmin,ymin");
        ref.xmin = xy[0];
        ref.ymin = xy[1];
      } else if (key.contains("xmax") && i + 1 < lines.size()) {
        double[] xy = twoDoubles(lines.get(++i), "img_ref xmax,ymax");
        ref.xmax = xy[0];
        ref.ymax = xy[1];
      } else if (key.contains("resolution") && i + 1 < lines.size()) {
        ref.resolution = Double.parseDouble(lines.get(++i).trim().split("\\s+")[0]);
      } else if ((key.contains("ni") || key.contains("taille")) && i + 1 < lines.size()) {
        String[] p = lines.get(++i).trim().split("\\s+");
        if (p.length < 2) {
          throw new IOException("img_ref.dat 末行应为 ni nj（高 宽）。");
        }
        ref.height = Integer.parseInt(p[0]);
        ref.width = Integer.parseInt(p[1]);
        sawSize = true;
      }
    }
    if (!sawSize || ref.resolution <= 0 || ref.width < 1 || ref.height < 1) {
      throw new IOException("img_ref.dat 无法解析范围、分辨率或 ni nj。");
    }
    return ref;
  }

  private static final class PivParam {
    List<String> lines;
    double timeInterval;
    int underSampling = 1;
    int dataLine = -1;
  }

  private static PivParam parsePivParam(Path path) throws IOException {
    PivParam pp = new PivParam();
    pp.lines = TextFiles.readLines(path);
    int label = -1;
    for (int i = 0; i < pp.lines.size(); i++) {
      if (pp.lines.get(i).toLowerCase(Locale.ROOT).contains("time interval")) {
        label = i;
        break;
      }
    }
    if (label < 0) {
      throw new IOException("PIV_param.dat 没有 Time interval 行。");
    }
    for (int i = label + 1; i < pp.lines.size(); i++) {
      if (!pp.lines.get(i).isBlank()) {
        pp.dataLine = i;
        break;
      }
    }
    if (pp.dataLine < 0) {
      throw new IOException("PIV_param.dat 的 Time interval 后没有数值。");
    }
    String[] parts = pp.lines.get(pp.dataLine).trim().split("\\s+");
    pp.timeInterval = Double.parseDouble(parts[0]);
    if (parts.length > 1) {
      pp.underSampling = Integer.parseInt(parts[1]);
    }
    if (pp.underSampling < 1) {
      pp.underSampling = 1;
    }
    return pp;
  }

  private static List<GridPoint> parseGrid(Path path) throws IOException {
    List<GridPoint> pts = new ArrayList<>();
    for (String line : TextFiles.readLines(path)) {
      String t = line.trim();
      if (t.isEmpty()) {
        continue;
      }
      String[] p = t.split("\\s+");
      if (p.length < 2) {
        continue;
      }
      pts.add(new GridPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
    }
    return pts;
  }

  private static final class Grp {
    boolean v2;
    int width;
    int height;
    List<GrpPoint> points = new ArrayList<>();
  }

  private static Grp parseGrp(Path path) throws IOException {
    List<String> lines = TextFiles.readLines(path);
    int i = 0;
    while (i < lines.size() && lines.get(i).isBlank()) {
      i++;
    }
    if (i >= lines.size()) {
      throw new IOException("GRP.dat 为空。");
    }
    Grp g = new Grp();
    String header = lines.get(i).trim();
    Matcher m = GRP_V2.matcher(header);
    if (m.matches()) {
      g.v2 = true;
      g.width = Integer.parseInt(m.group(1));
      g.height = Integer.parseInt(m.group(2));
    } else if (!header.toUpperCase(Locale.ROOT).startsWith("GRP")) {
      throw new IOException("GRP.dat 首行不是 GRP 头：" + header);
    }
    i++;
    while (i < lines.size() && lines.get(i).isBlank()) {
      i++;
    }
    if (i < lines.size() && lines.get(i).trim().matches("\\d+")) {
      i++;
    }
    while (i < lines.size() && lines.get(i).isBlank()) {
      i++;
    }
    if (i < lines.size() && lines.get(i).toUpperCase(Locale.ROOT).contains("X")) {
      i++;
    }
    for (; i < lines.size(); i++) {
      String raw = lines.get(i).trim();
      if (raw.isEmpty()) {
        continue;
      }
      String[] p = raw.split("\\s+");
      if (p.length < 5) {
        throw new IOException("GRP 点行列数不足：" + raw);
      }
      g.points.add(new GrpPoint(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
          p[3], p[4], raw));
    }
    return g;
  }

  private static double parseH(Path path) throws IOException {
    for (String line : TextFiles.readLines(path)) {
      if (!line.isBlank()) {
        return Double.parseDouble(line.trim().split("\\s+")[0]);
      }
    }
    throw new IOException("h.dat 没有水位数值。");
  }

  private static List<String> nonempty(List<String> lines) {
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      if (!line.isBlank()) {
        out.add(line.trim());
      }
    }
    return out;
  }

  private static double[] twoDoubles(String line, String what) throws IOException {
    String[] p = line.trim().split("\\s+");
    if (p.length < 2) {
      throw new IOException(what + " 需要两个数。");
    }
    return new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1])};
  }
}
