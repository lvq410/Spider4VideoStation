# Spider4VideoStation

群晖 Video Station 视频元数据抓取与管理的 Swing 桌面应用。

## 技术栈
- Java 8 + Spring Boot 2.1.3（WebApplicationType.NONE，纯桌面）
- Gradle 5.6.4 构建
- Swing UI，Jackson JSON，Jsoup HTML 解析
- Selenium WebDriver 用于豆瓣/Javdb 等网站抓取

## 项目结构
- `ui/` — Swing 界面（MainStage 主窗口、各功能 Dialog）
- `service/` — 业务服务（搜索、配置、缓存、DSM API 客户端等）
- `metadata/` — 元数据生成（NFO、VSmeta）
- `ffmpeg/` — FFmpeg 截图/媒体信息工具

## 配置
- 本地配置文件：`config/application-local.yml`（运行时自动生成，由 `ConfigService.persist()` 写入）
- 持久化的 key 白名单由 `MainStage.SETTING_KEYS` 控制
- Spring `@ConfigurationProperties` 绑定到 `Config` 类，修改后通过 `ContextRefresher.refresh()` 热加载
- 个人 DSM 账密等敏感信息仅存于 `application-local.yml`，不提交到 git（已在 `.gitignore` 中）

## DSM API
- 接口文档见 [docs/dsm-api.md](docs/dsm-api.md)
- `DsmApiClient` 实现透明鉴权：`getSid()` 懒加载登录并缓存，`post()` 自动注入 `_sid`，遇错误码 119 自动重登重试
- `postRaw()` 为纯 HTTP 调用，供 `getSid()` 内部使用以避免循环依赖
- 所有业务方法无 `sid` 参数，应用层完全不感知登录态
- `DsmApiClient` 实例在 `MainStage.getDsmClient()` 中懒创建，两个弹窗共享同一实例

## 关键设计决策
- 全局 `NotoSerifCJK.ttc` 字体用于数据展示控件兜底韩文，UI 标签/按钮保留默认字体
- 文件缓存位于 `cache/` 目录，URL→路径映射有 200 字符截断+MD5 兜底策略
- `ImageDownloadService` 自动压缩超过 4MB 的图片
- WebDriver 实例按 `(service, type)` 惰性管理，30 秒心跳保活，失败重试 1 次
- 搜索编排 `SearchOrchestratorService` 并行调用所有匹配的 SpiderService
- 元数据生成区分单文件模式与 tvshow 批量模式，批量模式逐集搜索+截图
