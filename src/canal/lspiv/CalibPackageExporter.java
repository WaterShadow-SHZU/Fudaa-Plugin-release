package canal.lspiv;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 一键定标包：平铺目录 + tar.gz。七件套，不含 coeff.dat。
 */
public final class CalibPackageExporter {
  public static final String[] PACKAGE_FILES = {
      "GRP.dat", "img_ref.dat", "PIV_param.dat", "grid.dat", "h.dat", "site.json", "SHA256SUMS"
  };
  private static final String[] HASHED = {
      "GRP.dat", "img_ref.dat", "PIV_param.dat", "grid.dat", "h.dat", "site.json"
  };
  private static final Pattern SITE_ID = Pattern.compile("^[a-z0-9-]{1,32}$");
  private static final Pattern VERSION = Pattern.compile("^\\d{8}-\\d{4}(-[a-z0-9])?$");
  public static final double DT_TOLERANCE_S = 1e-4;

  private CalibPackageExporter() {}

  public static final class Request {
    public File projectRoot;
    public File outputParent;
    public String siteId = "cam-1";
    public String cameraId = "cam-1";
    public String calibVersion;
    public int fps;
    public int frameEvery = 1;
    public boolean alignTimeInterval = true;
    public boolean writeTar = true;
  }

  public static final class Result {
    public File directory;
    public File tarGz;
    public File report;
    public boolean timeIntervalRewritten;
    public double timeIntervalS;
    public int gridPoints;
    public String summary;
  }

  public static Result export(Request req) throws IOException {
    validate(req);
    ProjectData data = ProjectData.load(req.projectRoot);
    int frameEvery = req.frameEvery;
    if (frameEvery < 1) {
      throw new IOException("pipeline.frame_every 必须 ≥ 1。");
    }
    double expected = frameEvery / (double) req.fps;
    double dt = data.timeInterval;
    boolean rewrite = false;
    if (Math.abs(dt - expected) > DT_TOLERANCE_S) {
      if (!req.alignTimeInterval) {
        throw new IOException(String.format(Locale.ROOT,
            "PIV_param 时间间隔 %.7f s 与 frame_every/fps=%.7f s 相差超过 1e-4。请勾选对齐或改正 fps。",
            dt, expected));
      }
      dt = expected;
      rewrite = true;
    }
    if (data.grpV2 && (data.grpWidth != data.cameraWidth() || data.grpHeight != data.cameraHeight())) {
      throw new IOException(String.format(Locale.ROOT,
          "GRP V2.0 宽高 %d×%d 与 img_ref ni nj（高 %d 宽 %d）不一致。",
          data.grpWidth, data.grpHeight, data.srcHeight, data.srcWidth));
    }

    File dir = new File(req.outputParent, req.calibVersion);
    if (dir.exists()) {
      throw new IOException("版本目录已存在，拒绝覆盖：" + dir.getAbsolutePath());
    }
    Files.createDirectories(dir.toPath());
    try {
      TextFiles.writeLf(dir.toPath().resolve("GRP.dat"), data.renderGrp());
      Files.copy(data.imgRefFile.toPath(), dir.toPath().resolve("img_ref.dat"));
      TextFiles.writeLf(dir.toPath().resolve("PIV_param.dat"), data.renderPivParam(dt, frameEvery));
      Files.copy(data.gridFile.toPath(), dir.toPath().resolve("grid.dat"));
      Files.copy(data.hFile.toPath(), dir.toPath().resolve("h.dat"));
      TextFiles.writeLf(dir.toPath().resolve("site.json"), siteJson(req, data, dt));
      if (new File(dir, "coeff.dat").exists()) {
        throw new IOException("内部错误：coeff.dat 被写入定标目录。");
      }
      StringBuilder sums = new StringBuilder();
      for (String name : HASHED) {
        String hex = TextFiles.sha256Hex(dir.toPath().resolve(name));
        sums.append(hex).append("  ").append(name).append('\n');
      }
      TextFiles.writeLf(dir.toPath().resolve("SHA256SUMS"), sums.toString());
      assertPackage(dir);

      File tar = null;
      if (req.writeTar) {
        tar = new File(req.outputParent, "calib_" + req.siteId + "_" + req.calibVersion + ".tar.gz");
        Path[] members = new Path[PACKAGE_FILES.length];
        for (int i = 0; i < PACKAGE_FILES.length; i++) {
          members[i] = dir.toPath().resolve(PACKAGE_FILES[i]);
        }
        Ustar.writeGzip(tar.toPath(), members);
      }
      File report = new File(req.outputParent, "calib_" + req.siteId + "_" + req.calibVersion + ".report.txt");
      String summary = summary(req, data, dir, tar, rewrite, dt);
      TextFiles.writeLf(report.toPath(), summary);
      Result result = new Result();
      result.directory = dir;
      result.tarGz = tar;
      result.report = report;
      result.timeIntervalRewritten = rewrite;
      result.timeIntervalS = dt;
      result.gridPoints = data.grid.size();
      result.summary = summary;
      return result;
    } catch (IOException | RuntimeException ex) {
      deleteTree(dir);
      throw ex;
    }
  }

  public static void validate(Request req) throws IOException {
    if (req.projectRoot == null) {
      throw new IOException("未打开工程。");
    }
    if (req.outputParent == null) {
      throw new IOException("请选择输出目录。");
    }
    if (req.siteId == null || !SITE_ID.matcher(req.siteId).matches()) {
      throw new IOException("site_id 必须匹配 ^[a-z0-9-]{1,32}$。");
    }
    if (req.cameraId == null || req.cameraId.isBlank()) {
      throw new IOException("camera_id 不能为空。");
    }
    if (req.calibVersion == null || !VERSION.matcher(req.calibVersion).matches()) {
      throw new IOException("calib_version 须为 YYYYMMDD-HHMM 或 YYYYMMDD-HHMM-a。");
    }
    if (req.fps < 1) {
      throw new IOException("fps 必须是正整数。");
    }
    Files.createDirectories(req.outputParent.toPath());
  }

  static String siteJson(Request req, ProjectData data, double dt) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"schema\": \"canal.site/v1\",\n");
    sb.append("  \"site_id\": \"").append(TextFiles.jsonEscape(req.siteId)).append("\",\n");
    sb.append("  \"camera_id\": \"").append(TextFiles.jsonEscape(req.cameraId)).append("\",\n");
    sb.append("  \"calib_version\": \"").append(TextFiles.jsonEscape(req.calibVersion)).append("\",\n");
    sb.append("  \"camera\": { \"width\": ").append(data.cameraWidth())
        .append(", \"height\": ").append(data.cameraHeight())
        .append(", \"fps\": ").append(req.fps).append(" },\n");
    sb.append("  \"pipeline\": { \"frame_every\": ").append(req.frameEvery).append(" },\n");
    sb.append("  \"piv\": { \"grid_points\": ").append(data.grid.size())
        .append(", \"time_interval_s\": ").append(String.format(Locale.US, "%.7f", dt)).append(" }\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static void assertPackage(File dir) throws IOException {
    for (String name : PACKAGE_FILES) {
      if (!new File(dir, name).isFile()) {
        throw new IOException("定标包缺少 " + name);
      }
    }
    File[] kids = dir.listFiles();
    if (kids == null || kids.length != PACKAGE_FILES.length) {
      throw new IOException("定标目录必须恰好 7 个文件，实际 " + (kids == null ? 0 : kids.length));
    }
    for (File kid : kids) {
      if (kid.getName().equalsIgnoreCase("coeff.dat")) {
        throw new IOException("coeff.dat 不得进入定标包。");
      }
    }
  }

  private static String summary(Request req, ProjectData data, File dir, File tar, boolean rewrite, double dt) {
    StringBuilder sb = new StringBuilder();
    sb.append("定标包已写出（平铺，无 coeff.dat）\n");
    sb.append("目录: ").append(dir.getAbsolutePath()).append('\n');
    if (tar != null) {
      sb.append("tar: ").append(tar.getAbsolutePath()).append('\n');
    }
    sb.append("site_id: ").append(req.siteId).append('\n');
    sb.append("camera: ").append(data.cameraWidth()).append('x').append(data.cameraHeight())
        .append(" fps=").append(req.fps).append('\n');
    sb.append("grid_points: ").append(data.grid.size()).append('\n');
    sb.append("time_interval_s: ").append(String.format(Locale.US, "%.7f", dt));
    if (rewrite) {
      sb.append(" （已从 ").append(String.format(Locale.US, "%.7f", data.timeInterval)).append(" 对齐到 frame_every/fps）");
    }
    sb.append('\n');
    sb.append("GRP 头: ").append(data.grpHeader());
    if (!data.grpV2) {
      sb.append(" （由旧格式升级，点坐标未改）");
    }
    sb.append('\n');
    sb.append("SHA256SUMS 为 GNU sha256sum 文本格式（64 位十六进制、两个空格、文件名），不含自身、不含 coeff.dat。\n");
    return sb.toString();
  }

  private static void deleteTree(File dir) {
    File[] kids = dir.listFiles();
    if (kids != null) {
      for (File kid : kids) {
        kid.delete();
      }
    }
    dir.delete();
  }

  public static List<String> packageNames() {
    List<String> names = new ArrayList<>();
    for (String n : PACKAGE_FILES) {
      names.add(n);
    }
    return names;
  }
}
