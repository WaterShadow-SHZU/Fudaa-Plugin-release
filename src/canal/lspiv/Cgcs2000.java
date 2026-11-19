package canal.lspiv;

/**
 * CGCS2000 高斯-克吕格（横轴墨卡托，k0=1）。
 * 椭球 a=6378137，1/f=298.257222101。与 WGS84（1/f=298.257223563）的地面差在亚毫米级，
 * 前端按 WGS84 使用经纬度时可忽略。
 *
 * <p>EPSG 端点已核对：4491 带13、4501 带23、4502 CM75E、4512 CM135E、
 * 4513 三度带25、4533 三度带45、4534 CM75E、4554 CM135E。
 * 带号系列东坐标含带号（带号×1e6+500000）；CM 系列假东为 500000。
 * EPSG 轴序为北、东；本类正算返回 [easting, northing]，轴序由调用方映射。
 */
public final class Cgcs2000 {
  public static final double A = 6378137.0;
  public static final double INV_F = 298.257222101;
  public static final double F = 1.0 / INV_F;
  public static final double E2 = F * (2.0 - F);
  public static final double EP2 = E2 / (1.0 - E2);
  public static final double K0 = 1.0;

  private Cgcs2000() {}

  public static final class Crs {
    public final String name;
    public final Integer epsg;
    public final double centralMeridianDeg;
    public final int zoneWidthDeg;
    public final int zone;
    public final boolean eastingHasZone;
    public final double falseEasting;
    public final double falseNorthing;

    public Crs(String name, Integer epsg, double centralMeridianDeg, int zoneWidthDeg, int zone,
        boolean eastingHasZone, double falseEasting, double falseNorthing) {
      this.name = name;
      this.epsg = epsg;
      this.centralMeridianDeg = centralMeridianDeg;
      this.zoneWidthDeg = zoneWidthDeg;
      this.zone = zone;
      this.eastingHasZone = eastingHasZone;
      this.falseEasting = falseEasting;
      this.falseNorthing = falseNorthing;
    }
  }

  public static Crs fromEpsg(int code) {
    if (code >= 4491 && code <= 4501) {
      int zone = 13 + (code - 4491);
      double cm = zone * 6.0 - 3.0;
      return zoned("CGCS2000 / Gauss-Kruger zone " + zone, code, cm, 6, zone);
    }
    if (code >= 4502 && code <= 4512) {
      double cm = 75.0 + (code - 4502) * 6.0;
      int zone = (int) Math.round((cm + 3.0) / 6.0);
      return cmOnly("CGCS2000 / Gauss-Kruger CM " + (int) cm + "E", code, cm, 6, zone);
    }
    if (code >= 4513 && code <= 4533) {
      int zone = 25 + (code - 4513);
      double cm = zone * 3.0;
      return zoned("CGCS2000 / 3-degree Gauss-Kruger zone " + zone, code, cm, 3, zone);
    }
    if (code >= 4534 && code <= 4554) {
      double cm = 75.0 + (code - 4534) * 3.0;
      int zone = (int) Math.round(cm / 3.0);
      return cmOnly("CGCS2000 / 3-degree Gauss-Kruger CM " + (int) cm + "E", code, cm, 3, zone);
    }
    throw new IllegalArgumentException("不支持的 EPSG:" + code
        + "。请改填中央经线，或使用 CGCS2000 高斯带号/CM 系列（4491–4554）。");
  }

  public static Crs manual(double centralMeridianDeg, int zoneWidthDeg, boolean eastingHasZone) {
    if (zoneWidthDeg != 3 && zoneWidthDeg != 6) {
      throw new IllegalArgumentException("带宽只能是 3 或 6 度。");
    }
    if (centralMeridianDeg < 70 || centralMeridianDeg > 140) {
      throw new IllegalArgumentException("中央经线应在 70–140°E（中国高斯带）。当前 " + centralMeridianDeg);
    }
    int zone = zoneWidthDeg == 3
        ? (int) Math.round(centralMeridianDeg / 3.0)
        : (int) Math.round((centralMeridianDeg + 3.0) / 6.0);
    double fe = eastingHasZone ? zone * 1_000_000.0 + 500_000.0 : 500_000.0;
    String name = "CGCS2000 GK CM " + trimNum(centralMeridianDeg) + "E / " + zoneWidthDeg + "°"
        + (eastingHasZone ? " / 含带号" : " / 不含带号");
    return new Crs(name, null, centralMeridianDeg, zoneWidthDeg, zone, eastingHasZone, fe, 0.0);
  }

  /** @return [easting_m, northing_m] */
  public static double[] forward(Crs crs, double lonDeg, double latDeg) {
    return forwardTm(lonDeg, latDeg, crs.centralMeridianDeg, crs.falseEasting, crs.falseNorthing);
  }

  /** @return [lon_deg, lat_deg] */
  public static double[] inverse(Crs crs, double easting, double northing) {
    return inverseTm(easting, northing, crs.centralMeridianDeg, crs.falseEasting, crs.falseNorthing);
  }

  public static double roundTripResidualM(Crs crs, double easting, double northing) {
    double[] geo = inverse(crs, easting, northing);
    double[] back = forward(crs, geo[0], geo[1]);
    double de = back[0] - easting;
    double dn = back[1] - northing;
    return Math.hypot(de, dn);
  }

  static double[] forwardTm(double lonDeg, double latDeg, double cmDeg, double fe, double fn) {
    double lat = Math.toRadians(latDeg);
    double lon = Math.toRadians(lonDeg);
    double lam0 = Math.toRadians(cmDeg);
    double sinLat = Math.sin(lat);
    double cosLat = Math.cos(lat);
    double tanLat = Math.tan(lat);
    double n = A / Math.sqrt(1.0 - E2 * sinLat * sinLat);
    double t = tanLat * tanLat;
    double c = EP2 * cosLat * cosLat;
    double aa = (lon - lam0) * cosLat;
    double e4 = E2 * E2;
    double e6 = e4 * E2;
    double m = A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat
        - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * lat)
        + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * lat)
        - (35 * e6 / 3072) * Math.sin(6 * lat));
    double a2 = aa * aa;
    double a3 = a2 * aa;
    double a4 = a2 * a2;
    double a5 = a4 * aa;
    double a6 = a3 * a3;
    double east = fe + K0 * n * (aa + (1 - t + c) * a3 / 6.0
        + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a5 / 120.0);
    double north = fn + K0 * (m + n * tanLat * (a2 / 2.0
        + (5 - t + 9 * c + 4 * c * c) * a4 / 24.0
        + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a6 / 720.0));
    return new double[] {east, north};
  }

  static double[] inverseTm(double east, double north, double cmDeg, double fe, double fn) {
    double e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));
    double e4 = E2 * E2;
    double e6 = e4 * E2;
    double m = (north - fn) / K0;
    double mu = m / (A * (1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256));
    double e12 = e1 * e1;
    double e13 = e12 * e1;
    double e14 = e12 * e12;
    double phi1 = mu
        + (3 * e1 / 2 - 27 * e13 / 32) * Math.sin(2 * mu)
        + (21 * e12 / 16 - 55 * e14 / 32) * Math.sin(4 * mu)
        + (151 * e13 / 96) * Math.sin(6 * mu)
        + (1097 * e14 / 512) * Math.sin(8 * mu);
    double sin1 = Math.sin(phi1);
    double cos1 = Math.cos(phi1);
    double tan1 = Math.tan(phi1);
    double c1 = EP2 * cos1 * cos1;
    double t1 = tan1 * tan1;
    double n1 = A / Math.sqrt(1 - E2 * sin1 * sin1);
    double r1 = A * (1 - E2) / Math.pow(1 - E2 * sin1 * sin1, 1.5);
    double d = (east - fe) / (n1 * K0);
    double d2 = d * d;
    double d3 = d2 * d;
    double d4 = d2 * d2;
    double d5 = d4 * d;
    double d6 = d3 * d3;
    double lat = phi1 - (n1 * tan1 / r1) * (d2 / 2.0
        - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d4 / 24.0
        + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d6 / 720.0);
    double lon = Math.toRadians(cmDeg) + (d
        - (1 + 2 * t1 + c1) * d3 / 6.0
        + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d5 / 120.0) / cos1;
    return new double[] {Math.toDegrees(lon), Math.toDegrees(lat)};
  }

  private static Crs zoned(String name, int epsg, double cm, int width, int zone) {
    double fe = zone * 1_000_000.0 + 500_000.0;
    return new Crs(name, epsg, cm, width, zone, true, fe, 0.0);
  }

  private static Crs cmOnly(String name, int epsg, double cm, int width, int zone) {
    return new Crs(name, epsg, cm, width, zone, false, 500_000.0, 0.0);
  }

  static String trimNum(double v) {
    String s = Double.toString(v);
    if (s.endsWith(".0")) {
      return s.substring(0, s.length() - 2);
    }
    return s;
  }
}
