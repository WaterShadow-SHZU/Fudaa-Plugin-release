package canal.lspiv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * GRP / grid → 投影 XY、经纬度，以及测区四角 SW→SE→NE→NW。
 * 网格公式：X=xmin+列×分辨率，Y=ymin+行×分辨率（行自上而下，顶行对应 Ymin）。
 */
public final class RoiExporter {
  public static final double RESIDUAL_LIMIT_M = 0.001;

  private RoiExporter() {}

  public enum AxisOrder {
    X_EAST_Y_NORTH,
    X_NORTH_Y_EAST
  }

  public static final class Request {
    public File projectRoot;
    public File outputDir;
    public Cgcs2000.Crs crs;
    public AxisOrder axis = AxisOrder.X_EAST_Y_NORTH;
    public boolean localOrigin;
    public double originEasting;
    public double originNorthing;
    public String cameraId = "";
    public String siteName = "";
  }

  public static final class Result {
    public File json;
    public File gridCsv;
    public File grpCsv;
    public double maxResidualM;
    public boolean roundTripOk;
    public String summary;
  }

  public static Result export(Request req) throws IOException {
    if (req.crs == null) {
      throw new IOException("请填写 EPSG 或中央经线。");
    }
    if (req.outputDir == null) {
      throw new IOException("请选择输出目录。");
    }
    ProjectData data = ProjectData.load(req.projectRoot);
    Files.createDirectories(req.outputDir.toPath());

    double minE = Double.POSITIVE_INFINITY;
    double minN = Double.POSITIVE_INFINITY;
    double maxE = Double.NEGATIVE_INFINITY;
    double maxN = Double.NEGATIVE_INFINITY;
    File gridCsv = new File(req.outputDir, "roi_points.csv");
    try (BufferedWriter w = Files.newBufferedWriter(gridCsv.toPath(), StandardCharsets.UTF_8)) {
      w.write("col,row,x_m,y_m,easting_m,northing_m,lon_deg,lat_deg\n");
      for (ProjectData.GridPoint p : data.grid) {
        double x = p.x(data);
        double y = p.y(data);
        double[] en = toEn(req, x, y);
        double[] geo = Cgcs2000.inverse(req.crs, en[0], en[1]);
        minE = Math.min(minE, en[0]);
        minN = Math.min(minN, en[1]);
        maxE = Math.max(maxE, en[0]);
        maxN = Math.max(maxN, en[1]);
        w.write(p.col + "," + p.row + "," + TextFiles.num(x) + "," + TextFiles.num(y) + ","
            + TextFiles.num(en[0]) + "," + TextFiles.num(en[1]) + ","
            + TextFiles.num(geo[0]) + "," + TextFiles.num(geo[1]) + "\n");
      }
    }

    File grpCsv = new File(req.outputDir, "grp_points.csv");
    double maxRes = 0;
    StringBuilder grpRows = new StringBuilder();
    try (BufferedWriter w = Files.newBufferedWriter(grpCsv.toPath(), StandardCharsets.UTF_8)) {
      w.write("i,j,x_m,y_m,z_m,easting_m,northing_m,lon_deg,lat_deg,roundtrip_residual_m\n");
      for (ProjectData.GrpPoint p : data.grpPoints) {
        double[] en = toEn(req, p.x, p.y);
        double[] geo = Cgcs2000.inverse(req.crs, en[0], en[1]);
        double res = Cgcs2000.roundTripResidualM(req.crs, en[0], en[1]);
        maxRes = Math.max(maxRes, res);
        String row = p.iToken + "," + p.jToken + "," + TextFiles.num(p.x) + "," + TextFiles.num(p.y) + ","
            + TextFiles.num(p.z) + "," + TextFiles.num(en[0]) + "," + TextFiles.num(en[1]) + ","
            + TextFiles.num(geo[0]) + "," + TextFiles.num(geo[1]) + "," + String.format(Locale.US, "%.6e", res);
        w.write(row);
        w.write('\n');
        grpRows.append("    {\"i\":\"").append(TextFiles.jsonEscape(p.iToken))
            .append("\",\"j\":\"").append(TextFiles.jsonEscape(p.jToken))
            .append("\",\"residual_m\":").append(String.format(Locale.US, "%.6e", res)).append("},\n");
      }
    }
    if (grpRows.length() > 2) {
      grpRows.setLength(grpRows.length() - 2);
      grpRows.append('\n');
    }
    boolean ok = maxRes <= RESIDUAL_LIMIT_M;

    double[] sw = Cgcs2000.inverse(req.crs, minE, minN);
    double[] se = Cgcs2000.inverse(req.crs, maxE, minN);
    double[] ne = Cgcs2000.inverse(req.crs, maxE, maxN);
    double[] nw = Cgcs2000.inverse(req.crs, minE, maxN);

    File json = new File(req.outputDir, "camera_roi.json");
    String axisNote = req.axis == AxisOrder.X_EAST_Y_NORTH ? "grp_x_east_y_north" : "grp_x_north_y_east";
    String xAxis = req.axis == AxisOrder.X_EAST_Y_NORTH ? "east_m" : "north_m";
    String yAxis = req.axis == AxisOrder.X_EAST_Y_NORTH ? "north_m" : "east_m";
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"template\": false,\n");
    sb.append("  \"camera_id\": \"").append(TextFiles.jsonEscape(req.cameraId)).append("\",\n");
    sb.append("  \"site_name\": \"").append(TextFiles.jsonEscape(req.siteName)).append("\",\n");
    sb.append("  \"note\": \"四点顺序 SW,SE,NE,NW。GRP/网格 X、Y 按 crs.axis 解释（X东Y北 或 X北Y东）；local_origin=true 时已加回原点 E0/N0。经纬度为 CGCS2000；与 WGS84 差为亚毫米。\",\n");
    sb.append("  \"xy_are_projected\": true,\n");
    sb.append("  \"t_zone\": \"Asia/Shanghai\",\n");
    sb.append("  \"ortho\": {\n");
    sb.append("    \"xmin_m\": ").append(TextFiles.num(data.xmin)).append(", \"ymin_m\": ").append(TextFiles.num(data.ymin)).append(",\n");
    sb.append("    \"xmax_m\": ").append(TextFiles.num(data.xmax)).append(", \"ymax_m\": ").append(TextFiles.num(data.ymax)).append(",\n");
    sb.append("    \"resolution_m\": ").append(TextFiles.num(data.resolution)).append(",\n");
    sb.append("    \"x_axis\": \"").append(xAxis).append("\", \"y_axis\": \"").append(yAxis).append("\",\n");
    sb.append("    \"row0\": \"Ymin\"\n");
    sb.append("  },\n");
    sb.append("  \"survey_bbox\": {\n");
    sb.append("    \"source\": \"grid\",\n");
    sb.append("    \"min_easting_m\": ").append(TextFiles.num(minE)).append(", \"min_northing_m\": ").append(TextFiles.num(minN)).append(",\n");
    sb.append("    \"max_easting_m\": ").append(TextFiles.num(maxE)).append(", \"max_northing_m\": ").append(TextFiles.num(maxN)).append("\n");
    sb.append("  },\n");
    sb.append("  \"quad_wgs84\": [\n");
    corner(sb, "SW", sw, minE, minN, true);
    corner(sb, "SE", se, maxE, minN, true);
    corner(sb, "NE", ne, maxE, maxN, true);
    corner(sb, "NW", nw, minE, maxN, false);
    sb.append("  ],\n");
    sb.append("  \"crs\": {\n");
    sb.append("    \"name\": \"").append(TextFiles.jsonEscape(req.crs.name)).append("\",\n");
    sb.append("    \"epsg\": ").append(req.crs.epsg == null ? "null" : req.crs.epsg).append(",\n");
    sb.append("    \"ellipsoid\": \"CGCS2000\",\n");
    sb.append("    \"method\": \"Transverse Mercator\",\n");
    sb.append("    \"k0\": 1,\n");
    sb.append("    \"central_meridian_deg\": ").append(TextFiles.num(req.crs.centralMeridianDeg)).append(",\n");
    sb.append("    \"zone_width_deg\": ").append(req.crs.zoneWidthDeg).append(",\n");
    sb.append("    \"zone\": ").append(req.crs.zone).append(",\n");
    sb.append("    \"easting_has_zone\": ").append(req.crs.eastingHasZone).append(",\n");
    sb.append("    \"false_easting_m\": ").append(TextFiles.num(req.crs.falseEasting)).append(",\n");
    sb.append("    \"false_northing_m\": ").append(TextFiles.num(req.crs.falseNorthing)).append(",\n");
    sb.append("    \"axis\": \"").append(axisNote).append("\",\n");
    sb.append("    \"local_origin\": ").append(req.localOrigin).append(",\n");
    sb.append("    \"origin_easting_m\": ").append(TextFiles.num(req.originEasting)).append(",\n");
    sb.append("    \"origin_northing_m\": ").append(TextFiles.num(req.originNorthing)).append(",\n");
    sb.append("    \"wgs84_note\": \"CGCS2000 与 WGS84 扁率差约 1.5e-11，同一经纬度地面差亚毫米，可忽略。\"\n");
    sb.append("  },\n");
    sb.append("  \"grp_roundtrip\": {\n");
    sb.append("    \"max_abs_residual_m\": ").append(String.format(Locale.US, "%.6e", maxRes)).append(",\n");
    sb.append("    \"limit_m\": ").append(RESIDUAL_LIMIT_M).append(",\n");
    sb.append("    \"ok\": ").append(ok).append(",\n");
    sb.append("    \"points\": [\n");
    sb.append(grpRows);
    sb.append("    ]\n");
    sb.append("  },\n");
    sb.append("  \"csv_contract\": {\n");
    sb.append("    \"columns\": [\"frame_id\",\"t_capture\",\"x\",\"y\",\"us\",\"vs\",\"w_corr\",\"corr_raw\",\"camera_id\"],\n");
    sb.append("    \"xy_unit\": \"local_ortho_meters\"\n");
    sb.append("  }\n");
    sb.append("}\n");
    TextFiles.writeLf(json.toPath(), sb.toString());

    Result result = new Result();
    result.json = json;
    result.gridCsv = gridCsv;
    result.grpCsv = grpCsv;
    result.maxResidualM = maxRes;
    result.roundTripOk = ok;
    result.summary = "ROI 已写出\n" + json.getAbsolutePath()
        + "\n" + gridCsv.getAbsolutePath()
        + "\n" + grpCsv.getAbsolutePath()
        + "\n四角顺序 SW→SE→NE→NW"
        + "\nGRP 往返最大残差 " + String.format(Locale.US, "%.3e m", maxRes)
        + (ok ? "（通过 ≤1 mm）" : "（未通过，请检查投影参数）")
        + "\n" + req.crs.name;
    return result;
  }

  static double[] toEn(Request req, double x, double y) {
    double east;
    double north;
    if (req.axis == AxisOrder.X_NORTH_Y_EAST) {
      north = x;
      east = y;
    } else {
      east = x;
      north = y;
    }
    if (req.localOrigin) {
      east += req.originEasting;
      north += req.originNorthing;
    }
    return new double[] {east, north};
  }

  private static void corner(StringBuilder sb, String role, double[] geo, double e, double n, boolean comma) {
    sb.append("    {\"role\":\"").append(role).append("\",\"lon\":").append(TextFiles.num(geo[0]))
        .append(",\"lat\":").append(TextFiles.num(geo[1]))
        .append(",\"easting_m\":").append(TextFiles.num(e))
        .append(",\"northing_m\":").append(TextFiles.num(n)).append("}");
    sb.append(comma ? ",\n" : "\n");
  }
}
