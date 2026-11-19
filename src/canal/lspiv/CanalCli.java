package canal.lspiv;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 与 GUI 同一套导出类的命令行验收入口。不是交付形态。 */
public final class CanalCli {
  private CanalCli() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      usage();
      return;
    }
    String cmd = args[0];
    Map<String, String> opt = options(args);
    switch (cmd) {
      case "selfcheck":
        selfCheck();
        break;
      case "export-calib":
        exportCalib(opt);
        break;
      case "export-csv":
        exportCsv(opt);
        break;
      case "export-roi":
        exportRoi(opt);
        break;
      case "export-rgb":
        exportRgb(opt);
        break;
      case "grp-draft":
        grpDraft(opt);
        break;
      default:
        usage();
        System.exit(2);
    }
  }

  static void selfCheck() throws Exception {
    Cgcs2000.Crs crs = Cgcs2000.fromEpsg(4548);
    double[] onCm = Cgcs2000.forward(crs, 117.0, 0.0);
    if (Math.abs(onCm[0] - crs.falseEasting) > 1e-4 || Math.abs(onCm[1]) > 1e-4) {
      throw new IllegalStateException("中央经线原点不正：" + onCm[0] + "," + onCm[1]);
    }
    double[] en = Cgcs2000.forward(crs, 117.02, 31.2);
    double res = Cgcs2000.roundTripResidualM(crs, en[0], en[1]);
    if (res > 1e-3) {
      throw new IllegalStateException("往返残差过大 " + res);
    }
    Cgcs2000.Crs zoned = Cgcs2000.fromEpsg(4526);
    if (zoned.zone != 38 || Math.abs(zoned.centralMeridianDeg - 114) > 1e-9
        || Math.abs(zoned.falseEasting - 38_500_000.0) > 1e-6) {
      throw new IllegalStateException("EPSG:4526 解析错误 " + zoned.falseEasting);
    }
    String[] augmented = PivLaunch.augment(new String[] {"C:\\exes\\PIV.exe"});
    if (augmented.length != 2 || !augmented[1].matches("\\d{1,2}")) {
      throw new IllegalStateException("ncore 参数未插入");
    }
    String[] other = PivLaunch.augment(new String[] {"C:\\exes\\filter.exe"});
    if (other.length != 1) {
      throw new IllegalStateException("不应改写非 PIV 命令");
    }
    checkRoiOrigin();
    checkHandbook();
    System.out.println("SELFCHECK_OK residual_m=" + res + " ncore=" + augmented[1] + " crs=" + crs.name
        + " roi-origin=ok handbook=ok");
  }

  /**
   * ROI 反算加回原点：X东Y北与X北Y东两条路径都要闭合。
   * 数字取虚构「示例场地」（E0=500000，N0=4000000），与 docs/fixtures 一致。
   */
  private static void checkRoiOrigin() {
    RoiExporter.Request east = new RoiExporter.Request();
    east.axis = RoiExporter.AxisOrder.X_EAST_Y_NORTH;
    east.localOrigin = true;
    east.originEasting = 500000;
    east.originNorthing = 4000000;
    double[] en = RoiExporter.toEn(east, 3.242, 8.008);
    if (Math.abs(en[0] - 500003.242) > 1e-9 || Math.abs(en[1] - 4000008.008) > 1e-9) {
      throw new IllegalStateException("X东Y北加原点错误 " + en[0] + "," + en[1]);
    }
    RoiExporter.Request north = new RoiExporter.Request();
    north.axis = RoiExporter.AxisOrder.X_NORTH_Y_EAST;
    north.localOrigin = true;
    north.originEasting = 500000;
    north.originNorthing = 4000000;
    double[] en2 = RoiExporter.toEn(north, 8.008, 3.242);
    if (Math.abs(en2[0] - 500003.242) > 1e-9 || Math.abs(en2[1] - 4000008.008) > 1e-9) {
      throw new IllegalStateException("X北Y东加原点错误 " + en2[0] + "," + en2[1]);
    }
  }

  /** 手簿 → 局部 / GRP 草稿：内存样例端到端（含噪音过滤）。坐标为虚构示例场地。 */
  private static void checkHandbook() throws Exception {
    java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("canal-selfcheck");
    try {
      java.nio.file.Path hb = tmp.resolve("handbook.csv");
      String csv = "点名,编码,N,E,H\r\n"
          + "基站_1,,4001819.874,498648.686,47.661\r\n"
          + "test,,3999998.437,499997.453,11.342\r\n"
          + "1,,4000008.008,500003.242,10.197\r\n"
          + "2,,4000006.121,500007.385,10.048\r\n";
      java.nio.file.Files.writeString(hb, csv, StandardCharsets.UTF_8);
      HandbookImporter.Request req = new HandbookImporter.Request();
      req.input = hb.toFile();
      req.originEasting = 500000.0;
      req.originNorthing = 4000000.0;
      req.width = 1920;
      req.height = 1080;
      req.outLocal = tmp.resolve("local.csv").toFile();
      req.outGrp = tmp.resolve("GRP.dat").toFile();
      HandbookImporter.Result result = HandbookImporter.run(req);
      if (result.points.size() != 2 || Math.abs(result.points.get(0).x - 3.242) > 1e-9
          || Math.abs(result.points.get(0).y - 8.008) > 1e-9
          || Math.abs(result.points.get(0).z - 10.197) > 1e-9) {
        throw new IllegalStateException("手簿换算错误");
      }
      List<String> grpLines = java.nio.file.Files.readAllLines(tmp.resolve("GRP.dat"));
      if (grpLines.size() < 4 || !"GRP V2.0 1920 1080".equals(grpLines.get(0))
          || !"2".equals(grpLines.get(1).trim())
          || !grpLines.get(2).toUpperCase(Locale.ROOT).contains("X")
          || !"3.242 8.008 10.197 0 0".equals(grpLines.get(3))) {
        throw new IllegalStateException("GRP 草稿格式错误 " + grpLines);
      }
      List<String> localLines = java.nio.file.Files.readAllLines(tmp.resolve("local.csv"));
      if (localLines.size() < 3 || !"id,X,Y,Z".equals(localLines.get(0))
          || !"1,3.242,8.008,10.197".equals(localLines.get(1))) {
        throw new IllegalStateException("局部表格式错误 " + localLines);
      }
      if (!result.noiseNames.contains("基站_1") || !result.noiseNames.contains("test")) {
        throw new IllegalStateException("噪音行未过滤 " + result.noiseNames);
      }
    } finally {
      File dir = tmp.toFile();
      File[] kids = dir.listFiles();
      if (kids != null) {
        for (File k : kids) {
          k.delete();
        }
      }
      dir.delete();
    }
  }

  private static void exportCalib(Map<String, String> opt) throws Exception {
    CalibPackageExporter.Request req = new CalibPackageExporter.Request();
    req.projectRoot = new File(required(opt, "project"));
    req.outputParent = new File(required(opt, "out"));
    req.siteId = opt.getOrDefault("site-id", "cam-1");
    req.cameraId = opt.getOrDefault("camera-id", req.siteId);
    req.calibVersion = opt.getOrDefault("version", "20261011-1200");
    ProjectData data = ProjectData.load(req.projectRoot);
    req.frameEvery = Integer.parseInt(opt.getOrDefault("frame-every", Integer.toString(data.underSampling)));
    int fps = opt.containsKey("fps") ? Integer.parseInt(opt.get("fps")) : ProjectData.inferFps(data.timeInterval, req.frameEvery);
    if (fps < 1) {
      fps = 30;
    }
    req.fps = fps;
    req.alignTimeInterval = !opt.containsKey("no-align");
    req.writeTar = true;
    CalibPackageExporter.Result result = CalibPackageExporter.export(req);
    System.out.println(result.summary);
  }

  private static void exportCsv(Map<String, String> opt) throws Exception {
    File project = new File(required(opt, "project"));
    VelocityCsvExporter.Request req = new VelocityCsvExporter.Request();
    req.projectRoot = project;
    req.outputCsv = new File(required(opt, "out"));
    req.cameraId = opt.getOrDefault("camera-id", "cam-1");
    ProjectData data = ProjectData.load(project);
    req.timeIntervalS = data.timeInterval;
    if (opt.containsKey("t0")) {
      req.t0 = OffsetDateTime.parse(opt.get("t0"));
    } else {
      req.t0 = OffsetDateTime.parse("2026-10-11T00:00:00+08:00");
    }
    req.t0 = req.t0.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    VelocityCsvExporter.Result result = VelocityCsvExporter.export(req);
    System.out.println(result.summary);
  }

  private static void exportRoi(Map<String, String> opt) throws Exception {
    RoiExporter.Request req = new RoiExporter.Request();
    req.projectRoot = new File(required(opt, "project"));
    req.outputDir = new File(required(opt, "out"));
    req.cameraId = opt.getOrDefault("camera-id", "cam-1");
    if (opt.containsKey("epsg")) {
      req.crs = Cgcs2000.fromEpsg(Integer.parseInt(opt.get("epsg")));
    } else {
      double cm = Double.parseDouble(opt.getOrDefault("cm", "117"));
      int width = Integer.parseInt(opt.getOrDefault("zone-width", "3"));
      boolean zoned = Boolean.parseBoolean(opt.getOrDefault("easting-has-zone", "false"));
      req.crs = Cgcs2000.manual(cm, width, zoned);
    }
    req.axis = "x-east".equals(opt.get("axis"))
        ? RoiExporter.AxisOrder.X_EAST_Y_NORTH : RoiExporter.AxisOrder.X_NORTH_Y_EAST;
    req.localOrigin = opt.containsKey("origin-e");
    if (req.localOrigin) {
      req.originEasting = Double.parseDouble(opt.get("origin-e"));
      req.originNorthing = Double.parseDouble(opt.getOrDefault("origin-n", "0"));
    }
    RoiExporter.Result result = RoiExporter.export(req);
    if (!result.roundTripOk) {
      throw new IllegalStateException(result.summary);
    }
    System.out.println(result.summary);
  }

  private static void exportRgb(Map<String, String> opt) throws Exception {
    OrthoRgbExporter.Request req = new OrthoRgbExporter.Request();
    req.projectRoot = new File(required(opt, "project"));
    req.outputDir = new File(required(opt, "out"));
    String exe = opt.get("exe");
    req.transfExe = exe != null ? new File(exe) : CanalHome.exe("transf_a.exe");
    req.limit = Integer.parseInt(opt.getOrDefault("limit", "1"));
    req.ncore = Integer.parseInt(opt.getOrDefault("ncore", Integer.toString(Math.min(4, PivCores.available()))));
    req.progress = System.out::println;
    OrthoRgbExporter.Result result = OrthoRgbExporter.export(req);
    if (result.images < 1 || !result.sidecar.isFile()) {
      throw new IllegalStateException("彩色正射未生成");
    }
    Path png = result.outputDir.toPath().resolve("image0001_ortho.png");
    if (!Files.isRegularFile(png) || Files.size(png) < 1000) {
      throw new IllegalStateException("PNG 过小或不存在 " + png);
    }
    System.out.println(result.summary);
  }

  /** 手簿 CSV + 原点 → 局部 id,X,Y,Z 与 GRP V2.0 草稿；不给原点时输入按局部 XYZ 处理。 */
  private static void grpDraft(Map<String, String> opt) throws Exception {
    HandbookImporter.Request req = new HandbookImporter.Request();
    req.input = new File(required(opt, "csv"));
    if (opt.containsKey("origin-e") || opt.containsKey("origin-n")) {
      req.originEasting = Double.parseDouble(required(opt, "origin-e"));
      req.originNorthing = Double.parseDouble(required(opt, "origin-n"));
    }
    if (opt.containsKey("width")) {
      req.width = Integer.parseInt(opt.get("width"));
    }
    if (opt.containsKey("height")) {
      req.height = Integer.parseInt(opt.get("height"));
    }
    req.outLocal = new File(required(opt, "out-local"));
    req.outGrp = opt.containsKey("out-grp") ? new File(opt.get("out-grp")) : null;
    HandbookImporter.Result result = HandbookImporter.run(req);
    for (String warn : result.warnings) {
      System.out.println("警告: " + warn);
    }
    System.out.println(result.summary);
  }

  private static String required(Map<String, String> opt, String key) {
    String v = opt.get(key);
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException("缺少 --" + key);
    }
    return v;
  }

  private static Map<String, String> options(String[] args) {
    Map<String, String> map = new HashMap<>();
    for (int i = 1; i < args.length; i++) {
      String a = args[i];
      if (!a.startsWith("--")) {
        continue;
      }
      String key = a.substring(2);
      if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
        map.put(key, args[++i]);
      } else {
        map.put(key, "true");
      }
    }
    return map;
  }

  private static void usage() {
    System.out.println("CanalCli selfcheck | export-calib | export-csv | export-roi | export-rgb | grp-draft");
  }
}
