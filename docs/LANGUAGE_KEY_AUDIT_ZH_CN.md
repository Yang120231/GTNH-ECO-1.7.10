# 1.7.10 语言键检查（2026-10-26）

检查对象为当前工作区；检查前已刷新 `origin`，当前分支没有落后远端的提交。

## 已修复

| 问题 | 修正 |
| --- | --- |
| `zh_cn`、`zh_hk`、`zh_tw` 的自动输出按钮、开关提示及三类合成通知，共六个键被写成问号 | 恢复相应中文，并保留数量与剩余数量参数 |
| 存储类型使用 `gui.tooltips.appliedenergistics2.Items/Fluids` | 对照实际 `rv3-beta-990-GTNH` 依赖，改用 `gui.appliedenergistics2.Items/Fluids`，覆盖注册和类型说明两个入口 |
| 创造存储类型使用现代 `item.ae2.creative_cell` | 改用本版本 AE2 的 `item.appliedenergistics2.ItemCreativeStorageCell.name` |
| 指南使用不存在的 `OpenGuide` 或未定义的 ECO 键 | 统一使用自有 `gui.neoecoae.guide`，补齐六种语言 |
| 合成控制器引用 `gui.neoecoae.host.network.frequency` | 改用已有的 `gui.neoecoae.host.network_frequency.cycle` |
| 拆解非空矩阵引用 AE2 中不存在的提示键 | 使用自有 `tooltip.neoecoae.storage.empty_only`，补齐六种语言 |
| 两个库存名称键没有定义 | 补齐 `container.neoecoae` 与 `container.neoecoae.integrated_working_station` |
| 合成状态的 AE2 标签没有占位符，传入的数量被忽略 | 翻译标签后附加实际数量 |
| 倒置英文的主机行、网络组成混用隐式和显式参数，导致顺序错误 | 对倒序参数使用完整的位置编号 |
| 港繁、台繁原生文件各有 40 个键未写回 JSON | 保留这些翻译并同步 JSON；已有长度标签与长度范围键也保留 |
| 文言未完成文件的蓝冰名称仅存在于 `.lang` | 将名称补回对应 JSON |
| 优先级页面仍保留现代 AE2 提示键，通过 Java 特判转换 | 页面改用自有键，绑定直接读取页面定义 |

本机 `porting/platform-translations.json` 与语言别名生成逻辑亦已同步；`porting/` 当前被仓库忽略，不属于版本控制内的交付文件。

## 当前语言覆盖

以下按客户端会加载的 `.lang` 键计数，包含物品、方块的 `.name` 别名。缺少本地翻译的键由英文回退，并不等同于运行时缺键。

| 语言 | 当前键数 | 需要英文回退的键数 |
| --- | ---: | ---: |
| `en_US` | 1324 | 0 |
| `zh_CN` | 1326 | 0 |
| `zh_HK` | 1129 | 197 |
| `zh_TW` | 1129 | 197 |
| `lzh` | 1089 | 237 |
| `en_UD` | 982 | 347 |

部分语言还保留旧配置或附加内容的历史键。本次保留兼容条目，没有以“源码未直接引用”为理由删除动态键或旧资源键，也没有把英文回退伪装为已翻译完成。

## 自动验证

新增 `LanguageResourcesTest`：

- 检查全部 18 个语言资源文件，包括 `unfinished`，核对重复键、空值、乱码和 JSON／原生别名一致性。
- 对照英文检查占位符位置及类型。
- 扫描主源码、界面 JSON 的完整语言键和局部 `text(...)` 前缀引用，对照实际 Minecraft、AE2、ECO 英文资源。
- 检查登记目录中 65 个已启用物品、70 个方块的 `.name` 键，以及方向、存储状态、接口模式、建造状态、开关、合成通知等动态键。
- 使用 1.7.10 的 `StringTranslate.parseLangFile` 和 `ChatComponentTranslation` 逐条解析、渲染六种主语言，并验证参数没有丢失。

可单独运行：

```powershell
.\gradlew.bat test --tests '*LanguageResourcesTest' --offline --console=plain
```

此验证覆盖资源和原生文本格式，不代表游戏内页面宽度、换行或字体已经完成目视验收。

本次结果：语言专项六项测试通过；`test jar --offline` 通过，全套共 648 项测试，647 项通过、1 项跳过。成品 JAR 内全部 18 个语言文件与工作区逐字节一致。

完整 `test build --offline` 在 `spotlessJavaCheck` 阶段失败，报告工作区多处 Java 格式问题；没有因此对其他正在修改的文件执行全量格式化。新增语言测试已单独通过 Spotless 格式化。
