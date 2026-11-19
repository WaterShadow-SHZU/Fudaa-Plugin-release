package canal.lspiv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 九列表面流速 CSV。优先 vel_real。corr_raw ≤ -98 时 w_corr 必须为 0。
 * UTF-8 无 BOM，LF。x,y 保持正射局部米。
 */
public final class VelocityCsvExporter {
  public static final String HEADER = "frame_id,t_capture,x,y,us,vs,w_corr,corr_raw,camera_id";
  public static final double INVALID_CORR = -98.0;
  private static final Pattern FRAME_NO = Pattern.compile("(\\d+)");
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private VelocityCsvExporter() {}

  public static final class Request {
    public File projectRoot;
    public File outputCsv;
    public String cameraId = "cam-1";
    public OffsetDateTime t0;
    public double timeIntervalS;
  }

  public static final class Result {
    public File file;
    public int rows;
    public int frames;
    public int invalid;
    public String summary;
  }

  public static Result export(Request req) throws IOException {
    if (req.projectRoot == null || !req.projectRoot.isDirectory()) {
      throw new IOException("未打开工程。");
    }
    if (req.outputCsv == null) {
      throw new IOException("请指定 CSV 路径。");
    }
    if (req.cameraId == null || req.cameraId.isBlank()) {
      throw new IOException("camera_id 不能为空。");
    }
    if (req.t0 == null) {
      throw new IOException("请填写 t_capture 起点。");
    }
    if (req.timeIntervalS <= 0) {
      throw new IOException("时间间隔必须 > 0。");
    }
    File dir = new File(req.projectRoot, "vel_real");
    File[] files = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".dat"));
    if (files == null || files.length == 0) {
      throw new IOException("没有 vel_real/*.dat。请先完成速度换算。不使用 vel_filter / vel_scal 代替。");
    }
    Arrays.sort(files, Comparator.comparing(File::getName));
    File parent = req.outputCsv.getParentFile();
    if (parent != null) {
      Files.createDirectories(parent.toPath());
    }
    int rows = 0;
    int invalid = 0;
    try (BufferedWriter w = Files.newBufferedWriter(req.outputCsv.toPath(), StandardCharsets.UTF_8)) {
      w.write(HEADER);
      w.write('\n');
      for (File file : files) {
        String frameId = stripExt(file.getName());
        int index = frameIndex(file.getName());
        OffsetDateTime t = req.t0.plus((long) Math.round((index - 1) * req.timeIntervalS * 1000.0), ChronoUnit.MILLIS);
        String stamp = t.format(ISO);
        List<String> lines = TextFiles.readLines(file.toPath());
        for (String line : lines) {
          String trimmed = line.trim();
          if (trimmed.isEmpty()) {
            continue;
          }
          String[] tok = trimmed.split("\\s+");
          if (tok.length < 5) {
            throw new IOException(file.getName() + " 列数不足 5：" + trimmed);
          }
          double corr = Double.parseDouble(tok[4]);
          String wCorr = corr <= INVALID_CORR ? "0" : String.format(Locale.US, "%.6f", corr);
          if (corr <= INVALID_CORR) {
            invalid++;
          }
          w.write(frameId);
          w.write(',');
          w.write(stamp);
          w.write(',');
          w.write(tok[0]);
          w.write(',');
          w.write(tok[1]);
          w.write(',');
          w.write(tok[2]);
          w.write(',');
          w.write(tok[3]);
          w.write(',');
          w.write(wCorr);
          w.write(',');
          w.write(tok[4]);
          w.write(',');
          w.write(req.cameraId);
          w.write('\n');
          rows++;
        }
      }
    }
    Result result = new Result();
    result.file = req.outputCsv;
    result.rows = rows;
    result.frames = files.length;
    result.invalid = invalid;
    result.summary = "流速 CSV 已写出\n" + req.outputCsv.getAbsolutePath()
        + "\n帧数 " + files.length + "，行数 " + rows + "，无效点(corr≤-98, w_corr=0) " + invalid
        + "\n来源 vel_real，未替换 PGM。";
    return result;
  }

  public static OffsetDateTime defaultT0(File projectRoot, ZoneId zone) {
    File img = new File(projectRoot, "img_pgm");
    File[] files = img.listFiles(File::isFile);
    long best = Long.MAX_VALUE;
    if (files != null) {
      for (File f : files) {
        if (f.lastModified() > 0 && f.lastModified() < best) {
          best = f.lastModified();
        }
      }
    }
    if (best == Long.MAX_VALUE) {
      best = System.currentTimeMillis();
    }
    return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(best), zone).withNano(0);
  }

  static int frameIndex(String name) {
    Matcher m = FRAME_NO.matcher(name);
    String last = null;
    while (m.find()) {
      last = m.group(1);
    }
    if (last == null) {
      return 1;
    }
    return Integer.parseInt(last);
  }

  private static String stripExt(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  public static List<File> listVelReal(File root) {
    File dir = new File(root, "vel_real");
    File[] files = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".dat"));
    if (files == null) {
      return List.of();
    }
    Arrays.sort(files, Comparator.comparing(File::getName));
    return new ArrayList<>(Arrays.asList(files));
  }
}
