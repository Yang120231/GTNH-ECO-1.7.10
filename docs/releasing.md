# 构建与发布

普通提交、Pull Request 自动执行 Gradle build；Actions 的 CI 工作流也可手动运行并下载构建产物。

## 手动发布

1. 将改动推送到 GitHub 默认分支。
2. Actions → Release Neo ECO AE Extension GTNH Port → Run workflow。
3. 选择待发布分支，填写版本标签（例如 v7.3.0-beta3）。
4. draft 默认开启：构建完成后到 Releases 检查草稿并发布；关闭则直接发布。
5. 若标签已存在，将构建该标签代码；否则基于所选分支本次运行的提交创建标签。

## 标签自动发布

推送 v7.3.0-beta3 这样的标签会自动构建并创建预发布；v7.3.0 这样的标签会创建正式发布。普通分支提交不会自动发布。

也支持在 GitHub 手动创建 Release 后自动构建并补充附件。版本号由标签传入 Gradle，游戏版本信息与产物文件名保持一致。

每个版本可提交 docs/release-<标签>.md 作为更新日志；未提供时使用 GitHub 自动生成的日志。发布使用内置 GITHUB_TOKEN，无需额外配置令牌。工作流文件需先进入默认分支，手动运行入口才会出现。
