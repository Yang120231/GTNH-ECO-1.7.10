# 原生文字绘制状态

上一轮真实客户端的优先级弹窗在输入框之后，把负数字和说明文字画成实心方块。接口页的相同文字控件正常，缺陷与输入框的 stencil 绘制顺序相关。

锁定的 MUI2 `b29dd89b52766541dcb995f56d492156572b4d55` 中，`TextRenderer` 和 `GuiDraw.drawText` 都先调用 `Platform.setupDrawFont()`，再调用原生 `FontRenderer`。自定义 `NativeText` 与存储页文字转接补上相同前置，恢复纹理／混合等原生字体绘制状态，避免沿用前一个控件留下的渲染状态。

编译与 7 项定向测试已随 `ui-placement-foundation-build.log` 通过；本次没有为简单绘制前置编写镜像测试。**修复后的实际截图仍待重跑，不宣称视觉验收已完成。**
