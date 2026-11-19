package canal.lspiv;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Canal 界面文案。下标 0 中文、1 英文、2 法文，跟随当前 Locale。 */
public final class CanalI18n {
  private static final Map<String, String[]> TEXT = new HashMap<>();

  static {
    put("menu.exportCalib", "导出定标包...", "Export calibration package...", "Exporter le paquet d'étalonnage...");
    put("menu.exportCsv", "导出表面流速 CSV...", "Export surface-velocity CSV...", "Exporter le CSV de vitesse...");
    put("menu.exportRgb", "导出正射 RGB...", "Export orthophoto RGB...", "Exporter l'orthophoto RVB...");
    put("menu.exportRoi", "导出测区 ROI...", "Export survey ROI...", "Exporter le ROI...");
    put("menu.grpDraft", "手簿 / 局部XYZ → GRP 草稿...", "Handbook / local XYZ → GRP draft...",
        "Carnet / XYZ local → brouillon GRP...");
    put("menu.cores", "PIV 核数...", "PIV cores...", "Cœurs PIV...");
    put("menu.autogrid", "自动网格 (AutoGrid)", "AutoGrid", "Maillage automatique (AutoGrid)");
    put("menu.autoIa", "自动 IA", "Automatic IA", "IA automatique");
    put("menu.sa", "SA 选区辅助", "SA selection aid", "Aide à la sélection SA");
    put("menu.language", "语言 / Language / Langue", "语言 / Language / Langue", "语言 / Language / Langue");
    put("lang.zh", "中文", "中文", "中文");
    put("lang.en", "English", "English", "English");
    put("lang.fr", "Français", "Français", "Français");
    put("lang.restart", "界面语言已保存。宿主菜单要在重启后才会全部切换。现在重启？",
        "Language saved. Host menus switch after restart. Restart now?",
        "Langue enregistrée. Les menus hôtes changent après redémarrage. Redémarrer maintenant ?");
    put("lang.saved", "语言已保存。请重新启动 Fudaa-LSPIV。",
        "Language saved. Please restart Fudaa-LSPIV.",
        "Langue enregistrée. Veuillez relancer Fudaa-LSPIV-Canal.");
    put("tip.reserved", "预留接口，尚未实现。", "Reserved hook, not implemented.", "Point d'extension réservé, non implémenté.");
    put("dlg.calib", "导出定标包", "Export calibration package", "Exporter l'étalonnage");
    put("dlg.csv", "导出表面流速 CSV", "Export surface-velocity CSV", "Exporter le CSV de vitesse");
    put("dlg.rgb", "导出正射 RGB", "Export orthophoto RGB", "Exporter l'orthophoto RVB");
    put("dlg.roi", "导出测区 ROI", "Export survey ROI", "Exporter le ROI");
    put("dlg.grpDraft", "手簿 / 局部XYZ → GRP 草稿", "Handbook / local XYZ → GRP draft",
        "Carnet / XYZ local → brouillon GRP");
    put("dlg.cores", "PIV 核数", "PIV cores", "Cœurs PIV");
    put("field.project", "工程", "Project", "Projet");
    put("field.output", "输出目录", "Output folder", "Dossier de sortie");
    put("field.csv", "CSV", "CSV", "CSV");
    put("field.fps", "fps", "fps", "fps");
    put("field.every", "frame_every", "frame_every", "frame_every");
    put("field.version", "calib_version", "calib_version", "calib_version");
    put("field.browse", "浏览", "Browse", "Parcourir");
    put("field.align", "时间间隔与 frame_every/fps 不一致时自动对齐",
        "Align time interval to frame_every/fps when they differ",
        "Aligner l'intervalle de temps sur frame_every/fps si besoin");
    put("field.tar", "同时写 tar.gz（包内无子目录）",
        "Also write tar.gz (no subfolders inside)",
        "Écrire aussi le tar.gz (sans sous-dossier)");
    put("field.calibNote", "不含 coeff.dat。旧 GRP 会升为 V2.0，点坐标不变。",
        "coeff.dat is excluded. Old GRP headers become V2.0; point coordinates stay.",
        "coeff.dat est exclu. Un ancien GRP passe en V2.0 ; les points ne changent pas.");
    put("field.t0", "t0", "t0", "t0");
    put("field.zone", "时区", "Time zone", "Fuseau");
    put("field.dt", "时间间隔 s", "Time interval s", "Intervalle s");
    put("field.csvNote", "只读 vel_real。corr≤-98 时 w_corr=0。UTF-8 无 BOM，LF。",
        "Reads vel_real only. w_corr=0 when corr≤-98. UTF-8, no BOM, LF.",
        "Lit seulement vel_real. w_corr=0 si corr≤-98. UTF-8 sans BOM, LF.");
    put("field.transfa", "transf_a", "transf_a", "transf_a");
    put("field.stab", "优先使用 img_stab", "Prefer img_stab", "Préférer img_stab");
    put("field.first", "只导出第一张（预览）", "First image only (preview)", "Première image seulement (aperçu)");
    put("field.rgbNote", "将调用 transf_a.exe，不写入 img_transf。",
        "Calls transf_a.exe and does not write img_transf.",
        "Appelle transf_a.exe et n'écrit pas dans img_transf.");
    put("field.epsg", "EPSG", "EPSG", "EPSG");
    put("field.cm", "中央经线", "Central meridian", "Méridien central");
    put("field.width", "带宽 °", "Zone width °", "Largeur de fuseau °");
    put("field.zonePrefix", "东坐标含带号", "Easting includes zone number", "Abscisse avec numéro de fuseau");
    put("field.axis", "轴序", "Axis order", "Ordre des axes");
    put("axis.east", "X东 Y北（推荐：正射 X=E−E0，Y=N−N0）",
        "X east, Y north (recommended: ortho X=E−E0, Y=N−N0)",
        "X est, Y nord (recommandé : ortho X=E−E0, Y=N−N0)");
    put("axis.north", "X北 Y东（测量高斯习惯 x=N、y=E）",
        "X north, Y east (surveying Gauss x=N, y=E)",
        "X nord, Y est (convention Gauss x=N, y=E)");
    put("field.originE", "原点 E0（东，m）", "Origin E0 (east, m)", "Origine E0 (est, m)");
    put("field.originN", "原点 N0（北，m）", "Origin N0 (north, m)", "Origine N0 (nord, m)");
    put("field.originNote", "局部坐标 X=E−E0、Y=N−N0 时填测区原点 E0/N0，导出会自动加回；留空=X/Y 已是绝对投影坐标。",
        "For local X=E−E0, Y=N−N0 fill the site E0/N0; it is added back on export. Leave empty if X/Y are already absolute projected coordinates.",
        "Pour un X=E−E0, Y=N−N0 local, remplir E0/N0 du site ; rajouté à l'export. Vide = X/Y déjà absolus.");
    put("field.roiNote", "GRP/网格 X、Y 已是所选投影坐标；局部坐标请填原点 E0/N0，导出时自动加回。只支持 CGCS2000 高斯-克吕格。",
        "GRP/grid X,Y are already projected coordinates. For local coordinates fill origin E0/N0; it is added back on export. CGCS2000 Gauss-Kruger only.",
        "X,Y du GRP/grille sont déjà projetés. Pour du local, remplir E0/N0 ; rajouté à l'export. Gauss-Krüger CGCS2000 seulement.");
    put("field.handbook", "输入 CSV", "Input CSV", "CSV d'entrée");
    put("field.imgW", "图像宽 W", "Image width W", "Largeur image W");
    put("field.imgH", "图像高 H", "Image height H", "Hauteur image H");
    put("field.outLocal", "局部 XYZ 输出", "Local XYZ output", "Sortie XYZ local");
    put("field.outGrp", "GRP 草稿输出", "GRP draft output", "Sortie brouillon GRP");
    put("field.grpNote", "填 E0/N0：输入为手簿 NEH，输出减原点后的局部表与 GRP 草稿。留空：输入已是局部 id,X,Y,Z。草稿 i j=0 0，导入 Fudaa 后在图上点像点。",
        "With E0/N0: input is handbook NEH; outputs are the local table and a GRP draft. Empty: input is already local id,X,Y,Z. Draft i j=0 0; click image points in Fudaa after import.",
        "Avec E0/N0 : entrée = NEH du carnet ; sorties = table locale et brouillon GRP. Vide : entrée déjà en id,X,Y,Z local. Brouillon i j=0 0 ; pointer les images dans Fudaa après import.");
    put("field.cores", "核数", "Cores", "Cœurs");
    put("field.machine", "机器", "Machine", "Machine");
    put("field.coreNote", "未修补时 GUI 调用 PIV.exe 不带参数，OpenMP 版 ncore 保持 1。此处保存的值会作为第一个参数传入。",
        "Unpatched GUI launches PIV.exe with no argument, so OpenMP stays at 1 core. The saved value is passed as the first argument.",
        "Sans correctif, l'interface lance PIV.exe sans argument : OpenMP reste à 1 cœur. La valeur enregistrée est le premier argument.");
    put("need.project", "请先打开一个 Fudaa 工程。", "Open a Fudaa project first.", "Ouvrez d'abord un projet Fudaa.");
    put("title.canal", "Canal", "Canal", "Canal");
    put("busy", "正在处理…", "Working…", "Traitement…");
    put("cancel", "取消", "Cancel", "Annuler");
    put("canceling", "正在取消…", "Canceling…", "Annulation…");
    put("missing", "未找到", "Not found", "Introuvable");
    put("cores.saved", "已保存 ncore=", "Saved ncore=", "ncore enregistré=");
  }

  private CanalI18n() {}

  private static void put(String key, String zh, String en, String fr) {
    TEXT.put(key, new String[] {zh, en, fr});
  }

  public static String code() {
    String lang = Locale.getDefault().getLanguage();
    if ("en".equals(lang) || "fr".equals(lang) || "zh".equals(lang)) {
      return lang;
    }
    return "zh";
  }

  public static String t(String key) {
    String[] row = TEXT.get(key);
    if (row == null) {
      return key;
    }
    String lang = code();
    if ("en".equals(lang)) {
      return row[1];
    }
    if ("fr".equals(lang)) {
      return row[2];
    }
    return row[0];
  }
}
