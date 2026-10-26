# 服务器回调的会话归属

`ServerTasks.Session` 在异步工作开始前捕获，完成后的回调只允许入队到同一个服务器生命周期。退出世界清空队列并使旧句柄永久失效；新世界不接收旧句柄的迟到回调。队列在回调引发重启时停止本次 drain，不在旧 tick 执行新世界任务。

`ServerWorldView` 持有自己的 `ServerRuntime`，后者绑定原服务器对象及会话，不再通过全局单例读取后来启动的服务器。独立客户端或停服时捕获的是 inactive 会话，可供 UI 无条件创建，不能在后续启动时转为可用句柄。

验证：`gradlew.bat spotlessApply test --tests '*ServerTasksLifecycleTest' --tests '*ECOPlanningLifecycleTest' --max-workers=2 --offline --console=plain`，5 项测试通过。测试以真实后台线程延迟完成后跨世界回调、旧 tick 中重启及 inactive 客户端句柄覆盖边界。UI 会话由 UI 批次接入此捕获接口。

此批不更改规划算法，也不宣称 CPU/任务/订单已接通。
