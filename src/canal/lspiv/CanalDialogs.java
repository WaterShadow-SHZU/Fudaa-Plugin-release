package canal.lspiv;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

public final class CanalDialogs {
  private static final DateTimeFormatter T0_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
  private static final DateTimeFormatter VER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

  private CanalDialogs() {}

  public static void info(String message) {
    JOptionPane.showMessageDialog(CanalMenuHook.parent(), message, "Canal", JOptionPane.INFORMATION_MESSAGE);
  }

  public static void error(String message) {
    JOptionPane.showMessageDialog(CanalMenuHook.parent(), message, "Canal", JOptionPane.ERROR_MESSAGE);
  }

  public static void exportCalib() {
    File root = requireProject();
    if (root == null) {
      return;
    }
    ProjectData data;
    try {
      data = ProjectData.load(root);
    } catch (IOException ex) {
      error(ex.getMessage());
      return;
    }
    Properties prefs = prefs();
    JTextField site = new JTextField(prefs.getProperty("site_id", "cam-1"), 18);
    JTextField camera = new JTextField(prefs.getProperty("camera_id", "cam-1"), 18);
    JTextField version = new JTextField(LocalDateTime.now().format(VER), 18);
    int inferred = ProjectData.inferFps(data.timeInterval, data.underSampling);
    int fps0 = inferred > 0 ? inferred : Integer.parseInt(prefs.getProperty("fps", "30"));
    JSpinner fps = new JSpinner(new SpinnerNumberModel(fps0, 1, 240, 1));
    JSpinner every = new JSpinner(new SpinnerNumberModel(data.underSampling, 1, 1000, 1));
    JCheckBox align = new JCheckBox("时间间隔与 frame_every/fps 不一致时自动对齐", true);
    JCheckBox tar = new JCheckBox("同时写 tar.gz（包内无子目录）", true);
    JTextField out = new JTextField(prefs.getProperty("out_dir", root.getAbsolutePath()), 28);
    JPanel form = form();
    align.setText(CanalI18n.t("field.align"));
    tar.setText(CanalI18n.t("field.tar"));
    add(form, 0, CanalI18n.t("field.project"), new JLabel(root.getAbsolutePath()));
    add(form, 1, "site_id", site);
    add(form, 2, "camera_id", camera);
    add(form, 3, CanalI18n.t("field.version"), version);
    add(form, 4, CanalI18n.t("field.fps"), fps);
    add(form, 5, CanalI18n.t("field.every"), every);
    add(form, 6, CanalI18n.t("field.output"), row(out, browseDir(out)));
    add(form, 7, "", align);
    add(form, 8, "", tar);
    add(form, 9, "", new JLabel(CanalI18n.t("field.calibNote")));
    if (!confirm(form, CanalI18n.t("dlg.calib"))) {
      return;
    }
    CalibPackageExporter.Request req = new CalibPackageExporter.Request();
    req.projectRoot = root;
    req.outputParent = new File(out.getText().trim());
    req.siteId = site.getText().trim();
    req.cameraId = camera.getText().trim();
    req.calibVersion = version.getText().trim();
    req.fps = ((Number) fps.getValue()).intValue();
    req.frameEvery = ((Number) every.getValue()).intValue();
    req.alignTimeInterval = align.isSelected();
    req.writeTar = tar.isSelected();
    prefs.setProperty("site_id", req.siteId);
    prefs.setProperty("camera_id", req.cameraId);
    prefs.setProperty("fps", Integer.toString(req.fps));
    prefs.setProperty("out_dir", req.outputParent.getAbsolutePath());
    savePrefs(prefs);
    runAsync("导出定标包", () -> CalibPackageExporter.export(req).summary);
  }

  public static void exportCsv() {
    File root = requireProject();
    if (root == null) {
      return;
    }
    ProjectData data = null;
    try {
      data = ProjectData.load(root);
    } catch (IOException ignored) {
      // 流速导出只强制 vel_real；时间间隔可手填。
    }
    Properties prefs = prefs();
    ZoneId zone = ZoneId.of(prefs.getProperty("t_zone", "Asia/Shanghai"));
    JTextField camera = new JTextField(prefs.getProperty("camera_id", "cam-1"), 18);
    JTextField t0 = new JTextField(VelocityCsvExporter.defaultT0(root, zone).format(T0_FMT), 24);
    double dt0 = data != null ? data.timeInterval : Double.parseDouble(prefs.getProperty("dt", "0.0333333"));
    JTextField dt = new JTextField(String.format(java.util.Locale.US, "%.7f", dt0), 12);
    JTextField zoneField = new JTextField(zone.getId(), 16);
    JTextField out = new JTextField(new File(root, "velocity.csv").getAbsolutePath(), 28);
    JPanel form = form();
    add(form, 0, CanalI18n.t("field.project"), new JLabel(root.getAbsolutePath()));
    add(form, 1, "camera_id", camera);
    add(form, 2, CanalI18n.t("field.t0"), t0);
    add(form, 3, CanalI18n.t("field.zone"), zoneField);
    add(form, 4, CanalI18n.t("field.dt"), dt);
    add(form, 5, CanalI18n.t("field.csv"), row(out, browseSave(out)));
    add(form, 6, "", new JLabel(CanalI18n.t("field.csvNote")));
    if (!confirm(form, CanalI18n.t("dlg.csv"))) {
      return;
    }
    VelocityCsvExporter.Request req = new VelocityCsvExporter.Request();
    req.projectRoot = root;
    req.outputCsv = new File(out.getText().trim());
    req.cameraId = camera.getText().trim();
    req.timeIntervalS = Double.parseDouble(dt.getText().trim());
    ZoneId z = ZoneId.of(zoneField.getText().trim());
    req.t0 = OffsetDateTime.parse(t0.getText().trim());
    prefs.setProperty("camera_id", req.cameraId);
    prefs.setProperty("t_zone", z.getId());
    prefs.setProperty("dt", dt.getText().trim());
    savePrefs(prefs);
    runAsync("导出 CSV", () -> VelocityCsvExporter.export(req).summary);
  }

  public static void exportRgb() {
    File root = requireProject();
    if (root == null) {
      return;
    }
    File exe = CanalHome.exe("transf_a.exe");
    Properties prefs = prefs();
    JTextField out = new JTextField(new File(root, "img_ortho_rgb").getAbsolutePath(), 28);
    JCheckBox stab = new JCheckBox(CanalI18n.t("field.stab"), Boolean.parseBoolean(prefs.getProperty("prefer_stab", "false")));
    JCheckBox firstOnly = new JCheckBox(CanalI18n.t("field.first"), false);
    JLabel status = new JLabel(CanalI18n.t("field.rgbNote"));
    JPanel form = form();
    add(form, 0, CanalI18n.t("field.project"), new JLabel(root.getAbsolutePath()));
    add(form, 1, CanalI18n.t("field.transfa"), new JLabel(exe == null ? CanalI18n.t("missing") : exe.getAbsolutePath()));
    add(form, 2, CanalI18n.t("field.output"), row(out, browseDir(out)));
    add(form, 3, "", stab);
    add(form, 4, "", firstOnly);
    add(form, 5, "", status);
    if (!confirm(form, CanalI18n.t("dlg.rgb"))) {
      return;
    }
    OrthoRgbExporter.Request req = new OrthoRgbExporter.Request();
    req.projectRoot = root;
    req.outputDir = new File(out.getText().trim());
    req.transfExe = exe;
    req.preferStabilized = stab.isSelected();
    req.limit = firstOnly.isSelected() ? 1 : 0;
    req.ncore = PivCores.resolve();
    prefs.setProperty("prefer_stab", Boolean.toString(stab.isSelected()));
    savePrefs(prefs);
    OrthoRgbExporter.Progress[] slot = new OrthoRgbExporter.Progress[1];
    req.progress = message -> {
      if (slot[0] != null) {
        slot[0].onProgress(message);
      }
    };
    runCancellable("导出正射 RGB", req.cancel, updater -> slot[0] = updater, () -> OrthoRgbExporter.export(req).summary);
  }

  public static void exportRoi() {
    File root = requireProject();
    if (root == null) {
      return;
    }
    Properties prefs = prefs();
    JTextField epsg = new JTextField(prefs.getProperty("epsg", ""), 8);
    JTextField cm = new JTextField(prefs.getProperty("cm", "117"), 8);
    JComboBox<String> width = new JComboBox<>(new String[] {"3", "6"});
    width.setSelectedItem(prefs.getProperty("zone_width", "3"));
    JCheckBox zoneInE = new JCheckBox(CanalI18n.t("field.zonePrefix"), Boolean.parseBoolean(prefs.getProperty("easting_has_zone", "false")));
    JComboBox<String> axis = new JComboBox<>(new String[] {
        CanalI18n.t("axis.east"),
        CanalI18n.t("axis.north")
    });
    if ("x_north".equals(prefs.getProperty("axis", "x_east"))) {
      axis.setSelectedIndex(1);
    }
    JTextField originE = new JTextField(prefs.getProperty("origin_e", ""), 12);
    JTextField originN = new JTextField(prefs.getProperty("origin_n", ""), 12);
    JTextField camera = new JTextField(prefs.getProperty("camera_id", "cam-1"), 16);
    JTextField out = new JTextField(new File(root, "canal_roi").getAbsolutePath(), 28);
    JPanel form = form();
    add(form, 0, CanalI18n.t("field.epsg"), epsg);
    add(form, 1, CanalI18n.t("field.cm"), cm);
    add(form, 2, CanalI18n.t("field.width"), width);
    add(form, 3, "", zoneInE);
    add(form, 4, CanalI18n.t("field.axis"), axis);
    add(form, 5, CanalI18n.t("field.originE"), originE);
    add(form, 6, CanalI18n.t("field.originN"), originN);
    add(form, 7, "", new JLabel("<html>" + CanalI18n.t("field.originNote") + "</html>"));
    add(form, 8, "camera_id", camera);
    add(form, 9, CanalI18n.t("field.output"), row(out, browseDir(out)));
    add(form, 10, "", new JLabel("<html>" + CanalI18n.t("field.roiNote") + "</html>"));
    if (!confirm(form, CanalI18n.t("dlg.roi"))) {
      return;
    }
    String epsgText = epsg.getText().trim();
    String eText = originE.getText().trim();
    String nText = originN.getText().trim();
    if (eText.isEmpty() != nText.isEmpty()) {
      error("原点 E0/N0 需同时填写或同时留空（留空=X/Y 已是绝对投影坐标）。");
      return;
    }
    boolean local = !eText.isEmpty();
    double originEast = 0;
    double originNorth = 0;
    RoiExporter.Request req = new RoiExporter.Request();
    req.projectRoot = root;
    req.outputDir = new File(out.getText().trim());
    req.cameraId = camera.getText().trim();
    req.axis = axis.getSelectedIndex() == 0
        ? RoiExporter.AxisOrder.X_EAST_Y_NORTH : RoiExporter.AxisOrder.X_NORTH_Y_EAST;
    req.localOrigin = local;
    try {
      if (local) {
        originEast = Double.parseDouble(eText);
        originNorth = Double.parseDouble(nText);
        req.originEasting = originEast;
        req.originNorthing = originNorth;
      }
      if (!epsgText.isEmpty()) {
        req.crs = Cgcs2000.fromEpsg(Integer.parseInt(epsgText));
      } else {
        req.crs = Cgcs2000.manual(Double.parseDouble(cm.getText().trim()),
            Integer.parseInt(String.valueOf(width.getSelectedItem())), zoneInE.isSelected());
      }
    } catch (RuntimeException ex) {
      error(ex.getMessage());
      return;
    }
    try {
      ProjectData data = ProjectData.load(root);
      double maxAbs = 0;
      for (ProjectData.GrpPoint p : data.grpPoints) {
        maxAbs = Math.max(maxAbs, Math.abs(p.x));
        maxAbs = Math.max(maxAbs, Math.abs(p.y));
      }
      String warn = roiMagnitudeGuard(maxAbs, local);
      if (warn != null && JOptionPane.showConfirmDialog(CanalMenuHook.parent(), warn, CanalI18n.t("dlg.roi"),
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
        return;
      }
    } catch (IOException ignored) {
      // 工程数据未就绪时跳过量级卫兵，交由导出阶段报错。
    }
    prefs.setProperty("epsg", epsgText);
    prefs.setProperty("cm", cm.getText().trim());
    prefs.setProperty("zone_width", String.valueOf(width.getSelectedItem()));
    prefs.setProperty("easting_has_zone", Boolean.toString(zoneInE.isSelected()));
    prefs.setProperty("axis", req.axis == RoiExporter.AxisOrder.X_NORTH_Y_EAST ? "x_north" : "x_east");
    prefs.setProperty("origin_e", eText);
    prefs.setProperty("origin_n", nText);
    prefs.setProperty("camera_id", req.cameraId);
    savePrefs(prefs);
    runAsync("导出 ROI", () -> RoiExporter.export(req).summary);
  }

  /** GRP X/Y 量级与原点组合的卫兵提示；null = 无需提示。 */
  private static String roiMagnitudeGuard(double maxAbs, boolean local) {
    long m = Math.round(maxAbs);
    if (local && maxAbs >= HandbookImporter.ABS_HINT_M) {
      return "GRP 的 X/Y 量级很大（约 ±" + m + " m）却又填了原点：疑似重复加回 E0/N0，结果将错。\n"
          + "若 X/Y 已是绝对投影坐标，请清空原点后重试。\n仍要继续吗？";
    }
    if (!local && maxAbs >= HandbookImporter.ABS_HINT_M) {
      return "GRP 的 X/Y 量级很大（约 ±" + m + " m），疑似已是绝对投影坐标（本次直接反算、不加原点）。\n"
          + "若本意是局部坐标（X=E−E0），请取消并填写原点 E0/N0。\n仍要继续吗？";
    }
    if (!local && maxAbs <= HandbookImporter.SITE_SCALE_M) {
      return "GRP 的 X/Y 量级很小（约 ±" + m + " m）且未填原点：导出的经纬度将不可信。\n"
          + "若 X/Y 为局部坐标，请取消并填写测区原点 E0/N0。\n仍要继续吗？";
    }
    return null;
  }

  public static void grpDraft() {
    Properties prefs = prefs();
    JTextField in = new JTextField(prefs.getProperty("handbook_csv", ""), 28);
    JTextField originE = new JTextField(prefs.getProperty("origin_e", ""), 12);
    JTextField originN = new JTextField(prefs.getProperty("origin_n", ""), 12);
    JTextField imgW = new JTextField(prefs.getProperty("img_w", ""), 6);
    JTextField imgH = new JTextField(prefs.getProperty("img_h", ""), 6);
    JTextField outLocal = new JTextField(prefs.getProperty("out_local", ""), 28);
    JTextField outGrp = new JTextField(prefs.getProperty("out_grp", ""), 28);
    JPanel form = form();
    add(form, 0, CanalI18n.t("field.handbook"), row(in, browseOpen(in, () -> fillDraftOutputs(in, outLocal, outGrp))));
    add(form, 1, CanalI18n.t("field.originE"), originE);
    add(form, 2, CanalI18n.t("field.originN"), originN);
    add(form, 3, CanalI18n.t("field.imgW"), imgW);
    add(form, 4, CanalI18n.t("field.imgH"), imgH);
    add(form, 5, CanalI18n.t("field.outLocal"), row(outLocal, browseSave(outLocal)));
    add(form, 6, CanalI18n.t("field.outGrp"), row(outGrp, browseSave(outGrp)));
    add(form, 7, "", new JLabel("<html>" + CanalI18n.t("field.grpNote") + "</html>"));
    if (!confirm(form, CanalI18n.t("dlg.grpDraft"))) {
      return;
    }
    String inText = in.getText().trim();
    File input = new File(inText);
    if (!input.isFile()) {
      error("输入 CSV 不存在。");
      return;
    }
    String eText = originE.getText().trim();
    String nText = originN.getText().trim();
    if (eText.isEmpty() != nText.isEmpty()) {
      error("原点 E0/N0 需同时填写或同时留空（留空=输入已是局部 XYZ）。");
      return;
    }
    HandbookImporter.Request req = new HandbookImporter.Request();
    req.input = input;
    try {
      if (!eText.isEmpty()) {
        req.originEasting = Double.parseDouble(eText);
        req.originNorthing = Double.parseDouble(nText);
      }
      if (!imgW.getText().trim().isEmpty()) {
        req.width = Integer.parseInt(imgW.getText().trim());
      }
      if (!imgH.getText().trim().isEmpty()) {
        req.height = Integer.parseInt(imgH.getText().trim());
      }
    } catch (NumberFormatException ex) {
      error("原点与图像宽高需为数字。");
      return;
    }
    File local = outLocal.getText().trim().isEmpty()
        ? new File(input.getParentFile(), "grp_local_xyz.csv") : new File(outLocal.getText().trim());
    File grp = outGrp.getText().trim().isEmpty()
        ? new File(input.getParentFile(), "GRP_draft.dat") : new File(outGrp.getText().trim());
    req.outLocal = local;
    req.outGrp = grp;
    prefs.setProperty("handbook_csv", inText);
    prefs.setProperty("origin_e", eText);
    prefs.setProperty("origin_n", nText);
    prefs.setProperty("img_w", imgW.getText().trim());
    prefs.setProperty("img_h", imgH.getText().trim());
    prefs.setProperty("out_local", local.getAbsolutePath());
    prefs.setProperty("out_grp", grp.getAbsolutePath());
    savePrefs(prefs);
    runAsync(CanalI18n.t("dlg.grpDraft"), () -> {
      HandbookImporter.Result r = HandbookImporter.run(req);
      StringBuilder sb = new StringBuilder(r.summary);
      for (String warn : r.warnings) {
        sb.append("\n⚠ ").append(warn);
      }
      return sb.toString();
    });
  }

  private static void fillDraftOutputs(JTextField in, JTextField outLocal, JTextField outGrp) {
    File parent = new File(in.getText().trim()).getParentFile();
    if (parent == null) {
      return;
    }
    if (outLocal.getText().trim().isEmpty()) {
      outLocal.setText(new File(parent, "grp_local_xyz.csv").getAbsolutePath());
    }
    if (outGrp.getText().trim().isEmpty()) {
      outGrp.setText(new File(parent, "GRP_draft.dat").getAbsolutePath());
    }
  }

  public static void editCores() {
    int current = PivCores.resolve();
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(current, 1, 99, 1));
    JPanel form = form();
    add(form, 0, CanalI18n.t("field.cores"), spinner);
    add(form, 1, CanalI18n.t("field.machine"), new JLabel(Integer.toString(PivCores.available())));
    add(form, 2, "", new JLabel("<html>" + CanalI18n.t("field.coreNote") + "</html>"));
    if (!confirm(form, CanalI18n.t("dlg.cores"))) {
      return;
    }
    try {
      PivCores.save(((Number) spinner.getValue()).intValue());
      info(CanalI18n.t("cores.saved") + PivCores.resolve() + "\n" + PivCores.file().getAbsolutePath());
    } catch (IOException ex) {
      error(ex.getMessage());
    }
  }

  private static File requireProject() {
    File root = CanalMenuHook.projectRoot();
    if (root == null || !root.isDirectory()) {
      error(CanalI18n.t("need.project"));
      return null;
    }
    return root;
  }

  private static void runAsync(String title, Exporter exporter) {
    runCancellable(title, new AtomicBoolean(), updater -> { }, exporter);
  }

  private static void runCancellable(String title, AtomicBoolean cancel, Consumer<OrthoRgbExporter.Progress> bind,
      Exporter exporter) {
    Component parent = CanalMenuHook.parent();
    JDialog dlg = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title);
    dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    dlg.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
    JLabel label = new JLabel(CanalI18n.t("busy"));
    JButton stop = new JButton(CanalI18n.t("cancel"));
    stop.addActionListener(e -> {
      cancel.set(true);
      label.setText(CanalI18n.t("canceling"));
    });
    JPanel box = new JPanel(new BorderLayout(8, 8));
    box.add(label, BorderLayout.CENTER);
    box.add(stop, BorderLayout.EAST);
    box.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
    dlg.setContentPane(box);
    dlg.pack();
    dlg.setLocationRelativeTo(parent);
    OrthoRgbExporter.Progress updater = message -> SwingUtilities.invokeLater(() -> label.setText(message));
    if (bind != null) {
      bind.accept(updater);
    }
    SwingWorker<String, Void> worker = new SwingWorker<>() {
      @Override
      protected String doInBackground() throws Exception {
        return exporter.run();
      }

      @Override
      protected void done() {
        dlg.dispose();
        try {
          info(get());
        } catch (Exception ex) {
          Throwable cause = ex.getCause() == null ? ex : ex.getCause();
          Log.error(title + " 失败", cause);
          error(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }
      }
    };
    worker.execute();
    dlg.setVisible(true);
  }

  private interface Exporter {
    String run() throws Exception;
  }

  private static boolean confirm(JPanel form, String title) {
    return JOptionPane.showConfirmDialog(CanalMenuHook.parent(), form, title,
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
  }

  private static JPanel form() {
    JPanel p = new JPanel(new GridBagLayout());
    return p;
  }

  private static void add(JPanel form, int row, String label, Component field) {
    GridBagConstraints lc = new GridBagConstraints();
    lc.gridx = 0;
    lc.gridy = row;
    lc.anchor = GridBagConstraints.WEST;
    lc.insets = new Insets(3, 4, 3, 8);
    form.add(new JLabel(label), lc);
    GridBagConstraints fc = new GridBagConstraints();
    fc.gridx = 1;
    fc.gridy = row;
    fc.weightx = 1;
    fc.fill = GridBagConstraints.HORIZONTAL;
    fc.anchor = GridBagConstraints.WEST;
    fc.insets = new Insets(3, 0, 3, 4);
    form.add(field, fc);
  }

  private static JPanel row(JTextField field, JButton button) {
    JPanel p = new JPanel(new BorderLayout(4, 0));
    p.add(field, BorderLayout.CENTER);
    p.add(button, BorderLayout.EAST);
    return p;
  }

  private static JButton browseDir(JTextField field) {
    JButton b = new JButton(CanalI18n.t("field.browse"));
    b.addActionListener(e -> {
      JFileChooser ch = new JFileChooser(field.getText());
      ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (ch.showOpenDialog(CanalMenuHook.parent()) == JFileChooser.APPROVE_OPTION) {
        field.setText(ch.getSelectedFile().getAbsolutePath());
      }
    });
    return b;
  }

  private static JButton browseOpen(JTextField field, Runnable afterPick) {
    JButton b = new JButton(CanalI18n.t("field.browse"));
    b.addActionListener(e -> {
      File start = new File(field.getText().trim());
      JFileChooser ch = new JFileChooser(start.isFile() ? start.getParentFile() : null);
      ch.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (ch.showOpenDialog(CanalMenuHook.parent()) == JFileChooser.APPROVE_OPTION) {
        field.setText(ch.getSelectedFile().getAbsolutePath());
        if (afterPick != null) {
          afterPick.run();
        }
      }
    });
    return b;
  }

  private static JButton browseSave(JTextField field) {
    JButton b = new JButton(CanalI18n.t("field.browse"));
    b.addActionListener(e -> {
      JFileChooser ch = new JFileChooser(new File(field.getText()).getParentFile());
      ch.setSelectedFile(new File(field.getText()));
      if (ch.showSaveDialog(CanalMenuHook.parent()) == JFileChooser.APPROVE_OPTION) {
        field.setText(ch.getSelectedFile().getAbsolutePath());
      }
    });
    return b;
  }

  private static File prefsFile() {
    return new File(CanalHome.dir(), "ui.properties");
  }

  private static Properties prefs() {
    Properties p = new Properties();
    File f = prefsFile();
    if (f.isFile()) {
      try (FileInputStream in = new FileInputStream(f)) {
        p.load(in);
      } catch (IOException ignored) {
        // defaults
      }
    }
    return p;
  }

  private static void savePrefs(Properties p) {
    try {
      File f = prefsFile();
      File parent = f.getParentFile();
      if (parent != null) {
        parent.mkdirs();
      }
      try (FileOutputStream out = new FileOutputStream(f)) {
        p.store(out, "Canal export dialog defaults");
      }
    } catch (IOException ex) {
      Log.error("保存界面默认值失败", ex);
    }
  }
}
