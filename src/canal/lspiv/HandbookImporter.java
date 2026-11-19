package canal.lspiv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * RTK 手簿 CSV（点名,编码,N,E,H）→ 场地局部 XYZ（X=E−E0、Y=N−N0、Z=H）与 GRP V2.0 草稿。
 * 输入不给原点时按「已是局部 id,X,Y,Z」处理。只做换算与落盘，不做投影反算。
 */
public final class HandbookImporter {
  /** 减去原点（或局部坐标本身）超过该值时提示疑似未减原点 / 原点有误。 */
  public static final double LOCAL_LIMIT_M = 10_000.0;
  /** GRP 点的局部工作尺度上限：未加原点且低于该值时经纬度反算不可信。 */
  public static final double SITE_SCALE_M = 1_000.0;
  /** 疑似绝对投影坐标的量级下限（米）。绝对高斯坐标通常远大于场地局部的几十米。 */
  public static final double ABS_HINT_M = 10_000.0;

  private HandbookImporter() {}

  public static final class Request {
    public File input;
    /** null = 输入已是局部 XYZ；与 originNorthing 必须同时提供或同时为 null。 */
    public Double originEasting;
    public Double originNorthing;
    /** GRP 头 W/H，null 按 0 处理，导定标包前以真实相机宽高替换。 */
    public Integer width;
    public Integer height;
    public File outLocal;
    public File outGrp;
  }

  public static final class Point {
    public final String id;
    public final double x;
    public final double y;
    public final double z;

    public Point(String id, double x, double y, double z) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }

  public static final class Result {
    public List<Point> points = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public List<String> noiseNames = new ArrayList<>();
    public File outLocal;
    public File outGrp;
    public String summary = "";
  }

  /** 手簿行 a=N b=E c=H；局部行 a=X b=Y c=Z。 */
  private static final class Raw {
    final String name;
    final double a;
    final double b;
    final double c;

    Raw(String name, double a, double b, double c) {
      this.name = name;
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }

  private static final class Parsed {
    final List<Raw> rows = new ArrayList<>();
    final List<String> noise = new ArrayList<>();
  }

  public static Result run(Request req) throws IOException {
    if (req.input == null || !req.input.isFile()) {
      throw new IOException("输入 CSV 不存在：" + req.input);
    }
    if ((req.originEasting == null) != (req.originNorthing == null)) {
      throw new IOException("原点 E0/N0 需同时提供或同时省略。");
    }
    if (req.outLocal == null) {
      throw new IOException("缺少局部 XYZ 输出路径。");
    }
    Parsed parsed = parse(req.input.toPath(), req.originEasting != null);

    Result res = new Result();
    List<String> warnings = new ArrayList<>();
    int auto = 1;
    double maxAbs = 0;
    for (Raw r : parsed.rows) {
      double x;
      double y;
      double z;
      if (req.originEasting != null) {
        x = roundUm(r.b - req.originEasting);
        y = roundUm(r.a - req.originNorthing);
        z = roundUm(r.c);
      } else {
        x = roundUm(r.a);
        y = roundUm(r.b);
        z = roundUm(r.c);
      }
      maxAbs = Math.max(maxAbs, Math.max(Math.abs(x), Math.abs(y)));
      String id = r.name.isBlank() ? Integer.toString(auto) : r.name;
      auto++;
      res.points.add(new Point(id, x, y, z));
    }
    if (res.points.isEmpty()) {
      throw new IOException("过滤后没有可用的 GRP 点（噪音规则：点名空白、基站*、test*）。");
    }
    if (req.originEasting != null) {
      Raw first = parsed.rows.get(0);
      if (first.a < 1_000_000 && first.b > first.a && first.b < 10_000_000) {
        warnings.add("N 列值小于 E 列且量级偏小，N/E 疑似颠倒，请核对表头与列序。");
      }
      if (first.a < 1000 && first.b < 1000) {
        warnings.add("N/E 量级像经纬度（度）而非投影米，请确认坐标系。");
      }
      if (maxAbs > LOCAL_LIMIT_M) {
        warnings.add("减去原点后 |X|/|Y| 最大仍有 " + Math.round(maxAbs) + " m（>" + Math.round(LOCAL_LIMIT_M)
            + " m），请核对 E0/N0。");
      }
    } else if (maxAbs > LOCAL_LIMIT_M) {
      warnings.add("局部坐标 |X|/|Y| 最大 " + Math.round(maxAbs) + " m：疑似绝对坐标未减原点。");
    }
    res.warnings = warnings;
    res.noiseNames = parsed.noise;

    File parent = req.outLocal.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    writeLocalCsv(res.points, req.outLocal);
    res.outLocal = req.outLocal;
    if (req.outGrp != null) {
      File grpParent = req.outGrp.getParentFile();
      if (grpParent != null) {
        grpParent.mkdirs();
      }
      writeGrpDraft(res.points, req.width, req.height, req.outGrp);
      res.outGrp = req.outGrp;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("保留 ").append(res.points.size()).append(" 个点");
    if (!parsed.noise.isEmpty()) {
      sb.append("；丢弃噪音 ").append(parsed.noise.size()).append(" 行：")
          .append(joinNames(parsed.noise));
    }
    sb.append('\n');
    sb.append("局部表：").append(req.outLocal.getAbsolutePath()).append('\n');
    if (req.outGrp != null) {
      sb.append("GRP 草稿：").append(req.outGrp.getAbsolutePath())
          .append("（i j 暂为 0 0，导入 Fudaa 后在图上点像点）\n");
    }
    res.summary = sb.toString();
    return res;
  }

  public static void writeLocalCsv(List<Point> points, File out) throws IOException {
    StringBuilder sb = new StringBuilder("id,X,Y,Z\n");
    for (Point p : points) {
      sb.append(p.id).append(',')
          .append(TextFiles.num(p.x)).append(',')
          .append(TextFiles.num(p.y)).append(',')
          .append(TextFiles.num(p.z)).append('\n');
    }
    TextFiles.writeLf(out.toPath(), sb.toString());
  }

  public static void writeGrpDraft(List<Point> points, Integer width, Integer height, File out) throws IOException {
    int w = width == null ? 0 : width;
    int h = height == null ? 0 : height;
    StringBuilder sb = new StringBuilder();
    sb.append("GRP V2.0 ").append(w).append(' ').append(h).append('\n');
    sb.append(points.size()).append('\n');
    sb.append("X  Y  Z  i  j\n");
    for (Point p : points) {
      sb.append(TextFiles.num(p.x)).append(' ')
          .append(TextFiles.num(p.y)).append(' ')
          .append(TextFiles.num(p.z)).append(" 0 0\n");
    }
    TextFiles.writeLf(out.toPath(), sb.toString());
  }

  /** 大数相减的浮点尾差按微米取整（远高于 RTK 毫米精度），避免输出 8.0080000004。 */
  private static double roundUm(double v) {
    return Math.round(v * 1e6) / 1e6;
  }

  private static String joinNames(List<String> names) {
    int limit = Math.min(names.size(), 5);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < limit; i++) {
      if (i > 0) {
        sb.append('、');
      }
      sb.append(names.get(i));
    }
    if (names.size() > limit) {
      sb.append(" 等");
    }
    return sb.toString();
  }

  /** 数据行布局：1 = 手簿 5 列（点名,编码,N,E,H）；2 = 局部 XYZ（3/4 列）。 */
  private static Parsed parse(Path path, boolean hasOrigin) throws IOException {
    List<String> lines = readLinesSmart(path);
    int first = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (!lines.get(i).isBlank()) {
        first = i;
        break;
      }
    }
    if (first < 0) {
      throw new IOException("CSV 为空：" + path);
    }
    char delim = detectDelimiter(lines.get(first));
    int layout = 0;
    int[] map = null;
    Parsed out = new Parsed();
    String[] header = null;
    for (int i = first; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isBlank()) {
        continue;
      }
      String[] tok = split(line, delim);
      int layoutHere = classify(tok);
      if (layout == 0) {
        if (layoutHere == 0) {
          header = tok;
          continue;
        }
        layout = layoutHere;
        map = chooseMap(header, tok.length, layout);
        if (layout == 1 && !hasOrigin) {
          throw new IOException("输入是手簿格式（点名,编码,N,E,H），需要原点：--origin-e / --origin-n（GUI 填 E0/N0）。");
        }
        if (layout == 2 && hasOrigin) {
          throw new IOException("输入已是局部 XYZ，无需原点；请去掉 --origin-e/--origin-n。");
        }
      } else if (layoutHere != layout) {
        throw new IOException("第 " + (i + 1) + " 行与已识别的数据格式不符（列数 " + tok.length + "）：" + line.trim());
      }
      if (layout == 1) {
        String name = clean(tok[map[0]]);
        if (isNoise(name)) {
          out.noise.add(name);
          continue;
        }
        out.rows.add(new Raw(name, parseNum(tok[map[1]], "N", i + 1), parseNum(tok[map[2]], "E", i + 1),
            parseNum(tok[map[3]], "H", i + 1)));
      } else {
        String name = map[0] >= 0 ? clean(tok[map[0]]) : "";
        if (isNoise(name)) {
          out.noise.add(name);
          continue;
        }
        out.rows.add(new Raw(name, parseNum(tok[map[1]], "X", i + 1), parseNum(tok[map[2]], "Y", i + 1),
            parseNum(tok[map[3]], "Z", i + 1)));
      }
    }
    if (layout == 0) {
      throw new IOException("没有可识别的数据行（手簿 5 列：点名,编码,N,E,H；或局部 XYZ 3/4 列）：" + path);
    }
    return out;
  }

  private static int classify(String[] tok) {
    if (tok.length >= 5) {
      try {
        parseNum(tok[2], "", 0);
        parseNum(tok[3], "", 0);
        parseNum(tok[4], "", 0);
        return 1;
      } catch (IOException ex) {
        return 0;
      }
    }
    if (tok.length == 4) {
      try {
        parseNum(tok[1], "", 0);
        parseNum(tok[2], "", 0);
        parseNum(tok[3], "", 0);
        return 2;
      } catch (IOException ex) {
        return 0;
      }
    }
    if (tok.length == 3) {
      try {
        parseNum(tok[0], "", 0);
        parseNum(tok[1], "", 0);
        parseNum(tok[2], "", 0);
        return 2;
      } catch (IOException ex) {
        return 0;
      }
    }
    return 0;
  }

  /** 返回 {nameIdx, c1, c2, c3}；手簿 c1=N c2=E c3=H，局部 c1=X c2=Y c3=Z。 */
  private static int[] chooseMap(String[] header, int count, int layout) {
    if (layout == 1) {
      int nIdx = findIdx(header, t -> eq(t, "n") || eq(t, "northing") || t.contains("北"));
      int eIdx = findIdx(header, t -> eq(t, "e") || eq(t, "easting") || t.contains("东"));
      int hIdx = findIdx(header, t -> eq(t, "h") || eq(t, "z") || t.contains("高程") || t.contains("elev"));
      if (nIdx > 0 && eIdx > 0 && hIdx > 0 && nIdx != eIdx && nIdx != hIdx && eIdx != hIdx) {
        return new int[] {0, nIdx, eIdx, hIdx};
      }
      return new int[] {0, 2, 3, 4};
    }
    if (header != null) {
      int xIdx = findIdx(header, t -> eq(t, "x") || eq(t, "x_m") || t.contains("东"));
      int yIdx = findIdx(header, t -> eq(t, "y") || eq(t, "y_m") || t.contains("北"));
      int zIdx = findIdx(header, t -> eq(t, "z") || eq(t, "z_m") || t.contains("高程") || t.contains("elev"));
      if (xIdx >= 0 && yIdx >= 0 && zIdx >= 0 && xIdx != yIdx && xIdx != zIdx && yIdx != zIdx) {
        return new int[] {xIdx == 0 ? -1 : 0, xIdx, yIdx, zIdx};
      }
    }
    return count == 4 ? new int[] {0, 1, 2, 3} : new int[] {-1, 0, 1, 2};
  }

  private static int findIdx(String[] header, Predicate<String> match) {
    if (header == null) {
      return -1;
    }
    for (int i = 0; i < header.length; i++) {
      String t = clean(header[i]);
      if (!t.isEmpty() && match.test(t)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean eq(String t, String s) {
    return t.equalsIgnoreCase(s);
  }

  private static boolean isNoise(String name) {
    if (name.isBlank()) {
      return true;
    }
    if (name.startsWith("基站")) {
      return true;
    }
    return name.toLowerCase(Locale.ROOT).startsWith("test");
  }

  private static String clean(String token) {
    String t = token.trim();
    if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
      t = t.substring(1, t.length() - 1).trim();
    }
    return t;
  }

  private static double parseNum(String token, String what, int lineNo) throws IOException {
    String t = clean(token);
    if (t.isEmpty()) {
      throw new IOException("第 " + lineNo + " 行 " + what + " 为空，不是数字。");
    }
    if (t.indexOf(',') >= 0 && t.indexOf('.') < 0) {
      t = t.replace(',', '.');
    }
    try {
      return Double.parseDouble(t);
    } catch (NumberFormatException ex) {
      throw new IOException("第 " + lineNo + " 行 " + what + " 不是数字：" + token);
    }
  }

  private static String[] split(String line, char delim) {
    String regex = java.util.regex.Pattern.quote(String.valueOf(delim));
    String[] parts = line.split(regex, -1);
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    return parts;
  }

  private static char detectDelimiter(String firstLine) throws IOException {
    int comma = 0;
    int semi = 0;
    int tab = 0;
    for (char c : firstLine.toCharArray()) {
      if (c == ',') {
        comma++;
      } else if (c == ';') {
        semi++;
      } else if (c == '\t') {
        tab++;
      }
    }
    int max = Math.max(comma, Math.max(semi, tab));
    if (max <= 0) {
      throw new IOException("无法识别分隔符（支持逗号 / 分号 / Tab）：" + firstLine);
    }
    if (max == tab) {
      return '\t';
    }
    if (max == semi) {
      return ';';
    }
    return ',';
  }

  /** UTF-8 优先，失败回退 GBK（手簿默认编码）；去 BOM。 */
  private static List<String> readLinesSmart(Path path) throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
    }
    String text = decode(bytes, StandardCharsets.UTF_8);
    if (text == null) {
      text = decode(bytes, Charset.forName("GBK"));
      if (text == null) {
        throw new IOException("无法解码 CSV（已尝试 UTF-8 与 GBK）：" + path);
      }
    }
    return new ArrayList<>(Arrays.asList(text.split("\r\n|\r|\n", -1)));
  }

  private static String decode(byte[] bytes, Charset cs) {
    CharsetDecoder decoder = cs.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException ex) {
      return null;
    }
  }
}
