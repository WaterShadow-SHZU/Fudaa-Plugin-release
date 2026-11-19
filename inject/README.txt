Fudaa-LSPIV-Canal 注入包
版本 Fudaa-LSPIV 1.11.3 Canal 1.1

用法
1. 解压原版 Fudaa-LSPIV，不要改那个目录。
2. 在本目录运行：
   inject.bat D:\路径\Fudaa-LSPIV
   或指定目标：
   inject.bat D:\路径\Fudaa-LSPIV D:\路径\Fudaa-LSPIV-Canal
3. 目标目录已存在时加 -Force 会先删除再复制。
4. 完成后双击目标目录里的 Fudaa-LSPIV-Canal.lnk 或 fudaa-lspiv-canal.bat。

注入会复制整份原版到新目录，再写入 Canal 插件、PIV 多核补丁、图标和版本名。原版目录不会被修改。

窗口标题应为 Fudaa-LSPIV 1.11.3 Canal 1.1。关于对话框使用同一名称和版本。开屏图标为已裁好的 64x64。快捷方式使用 canal.ico。
Canal 菜单中的“语言 / Language / Langue”可切换中文、English、Français，确认后重启。选中文时，没有译条的宿主字符串回退英文。

本包只含注入器，不含 Fudaa 本体。它不是 INRAE / Fudaa 的官方产品。
Canal 原创部分的许可见源码仓库 LICENSE（GPL-2.0 或更新版本）与 NOTICE。
随包的 ASM 库为 OW2 ASM 9.7.1（BSD-3-Clause）。注入包内全文见 payload\ASM-LICENSE；源码树见 lib\ASM-LICENSE。
