# Spider4VideoStation

群晖 Video Station 视频元数据抓取与管理的 Swing 桌面应用。

## 安装与启动

1. 从 [GitHub Releases](https://github.com/lvt4j/Spider4VideoStation/releases) 下载 `Spider4VideoStation.zip`
2. 解压到任意目录
3. 双击 `Spider4VideoStation/Spider4VideoStation.exe` 启动

绿色免安装，无需 Java 环境，内嵌 JRE。

## 功能使用指南

### 一、元数据搜索（核心功能）

**入口**：主界面左侧「搜索」面板。

**使用步骤**：

1. **选择目标文件**：顶部下拉框 + 「...」按钮，选择要处理的视频文件或文件夹。最近 20 个目标会自动记录。
2. **选择搜索源**：「抓取目标网站」下拉框，可选：
   - `Douban` — 豆瓣电影/电视剧，信息最全，需先登录（见"豆瓣登录"）
   - `BaikeBaidu` — 百度百科，中文资料丰富
   - `AV.StrictId` / `AV.Normal` — JavDB 日本 AV 数据，前者严格匹配番号
3. **选择类型**：`movie`（电影）、`tvshow`（剧集）、`tvshow_episode`（单集）
4. **输入关键词**：影片名称，支持中/日文
5. **选剧集时**填入季号（Season）和集号（Episode）
6. 点击「搜索结果」按钮

搜索结果在左下角列表展示，选中某条后右侧显示详情：标题、简介、日期、分级、类型、演员、导演、编剧、评分、海报、背景图。

---

### 二、生成元数据文件

搜索到结果后，在「应用」区域：

- **生成vsmeta**：生成群晖 Video Station 专用的 `.vsmeta` 二进制元数据文件，放在视频同目录。包含标题、简介、演员、海报、背景图等信息。
- **生成nfo**：生成 Kodi/Jellyfin/Emby 通用的 `.nfo` XML 元数据文件。movie 类型生成 `<videoname>.nfo`，tvshow 目录下生成 `<episode>.nfo`、`season.nfo`、`tvshow.nfo`。
- **标准重命名**：仅 movie 类型可用。将视频文件重命名为 `<标题> (<年份>).<扩展名>` 格式。

**批量模式**：当目标选择的是文件夹且类型为 `tvshow` 时，自动进入批量模式。程序会扫描文件夹内所有匹配 `SxxExx` 模式的剧集文件，逐集搜索并生成元数据。同时为剧集截图（使用 FFmpeg 在视频约 61.8% 位置截取）。

---

### 三、工具面板（右侧）

#### 3.1 查看元数据文件

点击「查看元数据文件」打开元数据浏览器。

- 左侧浏览目录结构，自动列出目录下的 `.vsmeta` 和 `.nfo` 文件
- 点击文件查看详情：标题、年份、简介、演员、图片等
- 支持**编辑模式**：修改字段后点击「保存」回写文件
- 支持切换 `lockdata` 锁定标记

#### 3.2 vsmeta 转 nfo

点击「vsmeta转nfo」，将目标文件夹下所有 `.vsmeta` 文件批量转换为 `.nfo`。已存在的 `.nfo` 会自动跳过。同时生成 `season.nfo` 和 `tvshow.nfo`。

#### 3.3 递归删除 nfo

点击「递归删除nfo」，递归删除目标文件夹下所有 `.nfo` 文件。有确认弹窗。

#### 3.4 vsmeta 修 season.nfo

点击「vsmeta修season.nfo」，修复 `season.nfo`：从同目录的 `.vsmeta` 文件中读取剧集标题，更新 `<title>` 标签，并确保 `<lockdata>true</lockdata>` 存在。

#### 3.5 VS 无效视频清理

**前置条件**：系统设置中配置 DSM 地址、账号、密码。

**用途**：检查 Video Station 库中的视频条目对应的磁盘文件是否还存在，清理已失效的条目。

**步骤**：
1. 点击「VS无效视频清理」打开弹窗
2. 下拉选择要检查的视频库
3. 点击「开始检查」— 遍历库中所有视频条目，批量检查磁盘文件是否存在
4. 结果表格显示所有无效条目（文件已丢失），每行有勾选框
5. 可「全选」「取消全选」「移除选中」（从列表中移除不处理的条目）
6. 点击「确认清理」— 弹窗确认后从 Video Station 数据库中删除**勾选的**条目
7. 日志区显示每条删除结果和汇总

#### 3.6 VS 未注册视频扫描

**前置条件**：系统设置中配置 DSM 地址、账号、密码。

**用途**：反向扫描 — 找出磁盘上存在但 Video Station 未注册的视频文件，并通过「移出再移回」的方式触发 VS 的自动索引。

**步骤**：
1. 点击「VS未注册视频扫描」打开弹窗
2. 下拉选择视频库
3. 填写「中转文件夹」路径（NAS 上的共享文件夹路径，如 `/video/temp`），首次填写后自动记住
4. 点击「开始扫描」：
   - 自动获取库文件夹路径
   - 加载 Video Station 中已注册的所有视频路径
   - 多线程（16 线程）并行递归扫描磁盘目录
   - 对比出未注册的文件
5. 结果表格：勾选框 ✓ | 文件名 | 完整路径 | 文件大小，支持点击列头排序
6. 可「全选」「取消全选」勾选要处理的文件
7. 点击「触发视频变动」：
   - 将文件**移入**中转文件夹
   - 等待 10 秒
   - **移回**原目录
   - 再等待 10 秒
   - 此过程会触发 Video Station 的文件监控，自动将文件录入库中
8. 日志区显示每个文件的处理结果和汇总

---

### 四、系统设置（右侧面板）

所有设置项修改后需点击对应的「设置」按钮生效，并持久化到本地配置文件。

| 设置项 | 说明 |
|--------|------|
| WebDriver地址 | Selenium RemoteWebDriver 的 URL（如 `http://192.168.0.105:4444`） |
| Javdb地址 | JavDB 网站域名（如 `https://javdb.com`） |
| 视频集号偏移量 | 本地文件集号与实际集号的差值 |
| 源站集号偏移量 | 源站集号与实际集号的差值 |
| 强制发布日期 | 强制覆盖元数据的发布日期 |
| 豆瓣结果条数 | 豆瓣搜索最大返回条数 |
| 百度百科结果条数 | 百度百科搜索最大返回条数 |
| DSM地址 | 群晖 NAS 地址（如 `http://192.168.0.105:50000/`） |
| DSM账号 | DSM 登录账号 |
| DSM密码 | DSM 登录密码 |

#### Selenium Server 启动

本应用的豆瓣、JavDB、百度百科搜索依赖 Selenium WebDriver。推荐使用 Docker 启动一个 Selenium Standalone Server：

```bash
docker run -d --name selenium-chrome \
  --restart unless-stopped \
  -p 4444:4444 \
  -p 7900:7900 \
  --shm-size="2g" \
  selenium/standalone-chrome:latest
```

- `4444` 端口为 WebDriver 连接地址，对应设置中的「WebDriver地址」
- `7900` 端口为 noVNC 网页监控，可在浏览器中 `http://<宿主机IP>:7900` 查看浏览器画面
- `--shm-size="2g"` 避免 Chrome 崩溃
- 更多配置参考官方文档：https://github.com/SeleniumHQ/docker-selenium

#### JavDB 访问说明

墙内用户可能无法直接访问 `javdb.com`。可通过以下方式获取最新可访问域名：

1. 下载 JavDB 客户端：https://github.com/bdvajstudio/javdb
2. 在客户端「公告」中获取最新网址
3. 将获取到的域名填入「Javdb地址」设置项

**豆瓣登录**：点击后弹出二维码窗口，用豆瓣手机 App 扫码登录。登录态会持久化，后续搜索豆瓣无需重复登录。

**清空缓存**：清除所有网页和图片缓存文件，释放磁盘空间。缓存位于 `cache/` 目录，每天凌晨自动清理超量缓存。

---

### 五、集号偏移系统

用于处理本地文件集号与源站集号不一致的情况。

- 例如源站从第 0 集开始编号，本地从第 1 集开始：
  - `fileEpOffset = 0`，`siteEpOffset = 0` → `siteEpIdx = fileEpIdx`
  - 若源站偏 1：设 `siteEpOffset = 1` → `siteEpIdx = fileEpIdx - 1`

公式：`标准索引 = fileEpIdx + fileEpOffset`，`siteEpIdx = 标准索引 - siteEpOffset`
