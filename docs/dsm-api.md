# 群晖 DSM API 接口文档

本项目使用的群晖 DSM Web API，用于 Video Station 无效视频清理功能。

## 调研来源

- [Synology DSM Login Web API Guide](https://kb.synology.com/en-us/DG/DSM_Login_Web_API_Guide/2) — 官方认证流程文档
- [pmilano1/synology-dsm-api](https://github.com/pmilano1/synology-dsm-api) — FileStation API 参考（`docs/api-reference/filestation/`、`docs/getting-started/authentication.md`）
- [zzarbi/synology](https://github.com/zzarbi/synology) — VideoStation API 定义（`src/Synology/VideoStation/api.json`）
- [mikespub/synology](https://github.com/mikespub/synology) — VideoStation2 API 参数要求（PHP 封装，`src/Synology/Applications/VideoStation.php`）
- 实际 NAS 上通过 `SYNO.API.Info` query=all 接口发现和验证

## 1. 认证

### 登录

```
POST /webapi/auth.cgi
api=SYNO.API.Auth
version=6
method=login
account=<用户名>
passwd=<密码>
session=VideoStation
format=sid
```

成功响应：
```json
{"data": {"sid": "AbCdEfGh..."}, "success": true}
```

后续所有请求需携带 `_sid=<sid>` 参数。

### 登出

```
POST /webapi/auth.cgi
api=SYNO.API.Auth&version=6&method=logout&session=VideoStation&_sid=<sid>
```

## 2. Video Station API

### 2.1 列出视频库

```
POST /webapi/VideoStation/library.cgi
api=SYNO.VideoStation.Library
version=2
method=list
```

响应 `data.libraries[]`：
| 字段 | 说明 |
|------|------|
| id | 库 ID（默认库为 0） |
| title | 库名称 |
| type | 类型：`movie` / `tvshow` / `home_video` / `tv_record` |

### 2.2 列出电影（含文件路径）

```
POST /webapi/VideoStation/movie.cgi
api=SYNO.VideoStation.Movie
version=4
method=list
limit=500&offset=0
sort_by=title&sort_direction=asc
additional=["file"]
```

响应 `data.movies[]`：
| 字段 | 说明 |
|------|------|
| id | 电影 ID |
| title | 电影标题 |
| library_id | 所属库 ID |
| additional.file[].sharepath | 共享路径（如 `/video/Movie/xxx.mkv`） |
| additional.file[].path | 绝对路径（如 `/volume1/video/Movie/xxx.mkv`） |

注意：v1 API 的 list 方法返回所有库的电影，需在客户端按 `library_id` 过滤。

### 2.3 列出剧集（TV Show）

**列出所有剧集：**
```
POST /webapi/entry.cgi
api=SYNO.VideoStation2.TVShow
version=1
method=list
library_id=<库ID>
limit=500&offset=0
```

响应 `data.tvshow[]`：
| 字段 | 说明 |
|------|------|
| id | 剧集 ID |
| title | 剧集名称 |

**列出某剧集的所有集：**
```
POST /webapi/entry.cgi
api=SYNO.VideoStation2.TVShowEpisode
version=1
method=list
library_id=<库ID>
tvshow_id=<剧集ID>
limit=500&offset=0
additional=["file"]
```

响应 `data.episode[]`：
| 字段 | 说明 |
|------|------|
| id | 集 ID |
| season | 季号 |
| episode | 集号 |
| additional.file[].sharepath | 文件共享路径 |

### 2.4 列出家庭视频

```
POST /webapi/entry.cgi
api=SYNO.VideoStation2.HomeVideo
version=1
method=list
library_id=<库ID>
limit=500&offset=0
additional=["file"]
```

响应 `data.video[]` 或 `data.home_video[]`（key 名称视版本而定）。

### 2.5 删除视频条目

删除电影：
```
POST /webapi/entry.cgi
api=SYNO.VideoStation2.Movie&version=1&method=delete&id=[<id1>,<id2>]
```

删除剧集的集：
```
POST /webapi/entry.cgi
api=SYNO.VideoStation2.TVShowEpisode&version=1&method=delete&id=[<id1>]
```

删除家庭视频：
```
POST /webapi/entry.cgi
api=SYNO.VideoStation2.HomeVideo&version=1&method=delete&id=[<id1>]
```

注意：删除操作使用 **VideoStation2**（v2）API，id 参数为 JSON 数组格式。

### 2.6 搜索（参考）

```
POST /webapi/VideoStation/movie.cgi
api=SYNO.VideoStation.Movie
version=4
method=search
title=<关键词>
```

注意：search 方法不支持 `additional` 参数，不返回文件路径信息。需用 `getinfo` 获取详情：
```
api=SYNO.VideoStation.Movie&version=4&method=getinfo&id=[<id>]&additional=["file"]
```

## 3. File Station API

### 3.1 检查文件是否存在

```
POST /webapi/entry.cgi
api=SYNO.FileStation.List
version=2
method=getinfo
path=["/video/path/to/file.mkv", "/video/path/to/other.mkv"]
```

响应 `data.files[]`：
- 文件存在时：返回 `name`、`path`、`isdir` 等字段，**无 `code` 字段**
- 文件不存在时：返回 `path` 和 `code: 408`

注意：即使部分文件不存在，整体响应仍然 `success: true`，需要逐条检查 `code` 字段。

### 3.2 列出共享文件夹

```
POST /webapi/entry.cgi
api=SYNO.FileStation.List
version=2
method=list_share
additional=["real_path","size"]
```

### 3.3 列出文件夹内容

```
POST /webapi/entry.cgi
api=SYNO.FileStation.List
version=2
method=list
folder_path=/video/Movies
additional=["real_path","size","time"]
```

### 3.4 创建文件夹

```
POST /webapi/entry.cgi
api=SYNO.FileStation.CreateFolder
version=2
method=create
folder_path=["/video"]
name=["NewFolder"]
force_parent=true
```

### 3.5 上传文件

```
POST /webapi/entry.cgi (multipart/form-data)
api=SYNO.FileStation.Upload
version=2
method=upload
path=/video/target_dir
create_parents=true
file=@localfile
```

### 3.6 移动/复制文件

```
POST /webapi/entry.cgi
api=SYNO.FileStation.CopyMove
version=3
method=start
path=["/video/source.mkv"]
dest_folder_path=/video/backup
remove_src=true          # true=移动, false=复制
```

返回 `taskid`，通过 `method=status&taskid=<taskid>` 轮询完成状态。

## 4. 通用错误码

| 错误码 | 含义 |
|--------|------|
| 101 | 无效参数 / 方法不存在 |
| 102 | API 不存在 |
| 400 | 无效凭据 |
| 401 | 账号被禁用 |
| 403 | 需要二次验证（OTP） |
| 408 | 文件不存在 / 无此任务 |
| 414 | 文件已存在 |

## 5. 本项目实际使用的 API 汇总

| 功能 | API | endpoint |
|------|-----|----------|
| 登录 | SYNO.API.Auth | /webapi/auth.cgi |
| 列出视频库 | SYNO.VideoStation.Library v2 | /webapi/VideoStation/library.cgi |
| 列出电影 | SYNO.VideoStation.Movie v4 | /webapi/VideoStation/movie.cgi |
| 列出剧集 | SYNO.VideoStation2.TVShow v1 | /webapi/entry.cgi |
| 列出集 | SYNO.VideoStation2.TVShowEpisode v1 | /webapi/entry.cgi |
| 列出家庭视频 | SYNO.VideoStation2.HomeVideo v1 | /webapi/entry.cgi |
| 检查文件存在 | SYNO.FileStation.List v2 getinfo | /webapi/entry.cgi |
| 删除电影 | SYNO.VideoStation2.Movie v1 delete | /webapi/entry.cgi |
| 删除集 | SYNO.VideoStation2.TVShowEpisode v1 delete | /webapi/entry.cgi |
| 删除家庭视频 | SYNO.VideoStation2.HomeVideo v1 delete | /webapi/entry.cgi |
