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
- 持久化的 key 白名单由 `MainStage.SETTING_KEYS` 控制，额外还有 `recentTargets`、`recentDsmPaths`、`recentMetaCompleteTargets`、`recentMetaCompleteTempFolders`、`recentThumbTargets`、`recentThumbTempFolders` 等硬编码 key
- Spring `@ConfigurationProperties` 绑定到 `Config` 类，修改后通过 `ContextRefresher.refresh()` 热加载
- 个人 DSM 账密等敏感信息仅存于 `application-local.yml`，不提交到 git（已在 `.gitignore` 中）

## DSM API
- 接口文档见 [docs/dsm-api.md](docs/dsm-api.md)
- `DsmApiClient` 实现透明鉴权：`getSid()` 懒加载登录并缓存，`post()` 自动注入 `_sid`，遇错误码 119 自动重登重试
- `postRaw()` 为纯 HTTP 调用，供 `getSid()` 内部使用以避免循环依赖
- 所有业务方法无 `sid` 参数，应用层完全不感知登录态
- `DsmApiClient` 实例在 `MainStage.getDsmClient()` 中懒创建，两个弹窗共享同一实例

## VS 工具集

### VS 无效视频清理（VSCleanupDialog）
- 依赖 DSM API：通过 FileStation 批量检查文件存在性，通过 VS v2 API 批量删除无效条目
- 电影/剧集/家庭视频三类分别处理，分页拉取

### VS 未注册视频扫描（VSUnregisteredScanDialog）
- 依赖 DSM API：多线程递归扫描 NAS 目录，对比 VS 已注册列表找差异
- 通过「移出→等10s→移回→等10s」触发 VS 文件监控自动索引

### VS 追加剧集 meta 补全（VSmetaCompleterDialog）
- **纯本地操作**，不依赖 DSM API（移动触发用本地 `Files.move`）
- 深度优先遍历目标文件夹，按(目录, season)分组找基准 vsmeta（最低 episode 的可解析 vsmeta，跳过第 0 集）
- 扫描时全局缓存 `metaCache`(vsmeta文件→解析对象) 和 `templateCache`(目录|season→模板)，补全阶段直接复用
- 完整度判断：与同目录同 season 基准对比，基准有的字段目标没有才算缺失（episodeTitle 特殊：缺失一定不完整）
- 补全时逐字段判断「已有则保留，没有则从模板复制」，episodeTitle 兜底生成 `第xx集`（第0集生成 `SP`）
- 每目录处理后清理非结果非模板的缓存释放内存

### VS 剧集缩略图重刷（VSThumbRefreshDialog）
- **纯本地操作**，不依赖 DSM API
- 四种扫描模式：仅无缩略图 / 重复缩略图 / 无+重复 / 全部重刷
- 深度优先遍历（先子目录后本目录），加载 vsmeta 时显示进度，每目录即时筛选分析
- 使用 FFmpeg 在视频 61.8% 位置截取一帧，Base64 编码后写入 `episodeThumbData`

### 公共组件
- `FilePickerDialog`：支持 `folderOnly` 模式，仅展示目录
- `DSMFolderPickerDialog`：通过 FileStation API 浏览 NAS 目录（供需要 DSM 的弹窗使用）
- `FUtils`：`isVideoFile(String name)` 重载，避免不必要的 File 对象构造

## 关键设计决策
- 全局 `NotoSerifCJK.ttc` 字体用于数据展示控件兜底韩文，UI 标签/按钮保留默认字体
- 文件缓存位于 `cache/` 目录，URL→路径映射有 200 字符截断+MD5 兜底策略
- `ImageDownloadService` 自动压缩超过 4MB 的图片
- WebDriver 实例按 `(service, type)` 惰性管理，30 秒心跳保活，失败重试 1 次
- 搜索编排 `SearchOrchestratorService` 并行调用所有匹配的 SpiderService
- 元数据生成区分单文件模式与 tvshow 批量模式，批量模式逐集搜索+截图
