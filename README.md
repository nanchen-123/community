# 安邻云枢——社区安防Web管理平台

> 基于激光 SLAM 自主导航、AI 视觉识别与物联网感知的社区智慧安防解决方案

<p>  
  <img src="https://img.shields.io/badge/version-2.0-blue" alt="version">  
  <img src="https://img.shields.io/badge/JDK-17-orange" alt="jdk">  
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.1-green" alt="spring">  
  <img src="https://img.shields.io/badge/Vue-3.5-42b883" alt="vue">  
  <img src="https://img.shields.io/badge/YOLO-11-yellow" alt="yolo">  
  <img src="https://img.shields.io/badge/ROS-Noetic-22314E" alt="ros">  
</p>

---

## 一、项目介绍

本项目基于激光 SLAM 自主导航、AI 视觉识别和物联网感知相关技术，立足社区治理的实际需求，从高效、精准、智能化的角度出发，研发一款面向社区治理场景的社区安防智能巡检系统。系统旨在为物业管理人员提供高效、便捷的智能巡检工具，进一步强化社区安防保障能力，推动社区环境治理向精细化方向发展，助力社区管理实现数字化升级，为居民营造安全、舒适、便捷的社区环境。

### 1.1 解决什么问题

| 传统巡检痛点                | 本项目的做法                              |
| --------------------- | ----------------------------------- |
| 依赖保安步行巡逻，人力成本高、夜间覆盖不足 | 机器人在虚拟社区中按航点自主巡逻，7×24 小时不间断         |
| 隐患靠肉眼发现，漏检率高、发现滞后     | YOLO 双模型自动识别行人与七类安全隐患，抓拍即识别         |
| 外来人员与陌生车辆难以甄别         | 行人模型区分居民/外来人员，PaddleOCR 提取车牌并与车辆库比对 |
| 记录靠纸笔，处置过程难追溯         | 任务、抓拍、告警、处置全程落库，支持多维检索与统计分析         |

### 1.2 核心功能

- **巡检任务管理**：可视化编排航点（坐标、朝向四元数、是否自动拍照、识别类型），设置巡逻圈数，一键下发与中途停止
- **机器人自主巡逻**：ROS move_base 按航点导航、自动避障，实时回传进度与位姿，支持 1~3 秒内响应取消
- **AI 视觉识别**：行人检测（居民/外来人员）、七类安全隐患检测、车牌 OCR 识别，三模型并行
- **实时告警推送**：WebSocket 秒级推送任务进度、机器人状态、告警与车牌，前端免刷新
- **安防台账**：告警与车牌记录统一归档，支持按类型、等级、状态、时间组合筛选与处置
- **数据可视化**：巡检完成率、告警处置率、周环比、近七日趋势与类型占比，支持 CSV 导出
- **AI 智能助手**：接入 DeepSeek，支持系统问答与 Word/Excel/PDF 长文档分段摘要，输出结构化报告
- **三级权限与数据隔离**：普通用户 / 管理员 / 超级管理员，服务端强制按社区过滤数据

### 1.3 项目规模

| 项            | 数量                       |
| ------------ | ------------------------ |
| 后端控制器        | 14 个                     |
| REST 接口      | 58 个                     |
| 数据表          | 7 张                      |
| WebSocket 事件 | 10 类                     |
| 前端页面组件       | 17 个                     |
| AI 识别模型      | 3 个（行人 / 多目标检测 / 车牌 OCR） |

---

## 二、技术架构

### 2.1 总体架构

系统采用四层解耦架构，各层只通过 HTTP 与 WebSocket 通信，任一进程重启不影响其余进程，支持分机部署。

```
┌──────────────────────────────────────────────────────────────┐
│                      前端展示层（Vue 3）                      │
│  首页概览 │ 巡检任务 │ 安防台账 │ 设备/居民管理 │ 数据分析 │ AI助手 │
└───────────────┬──────────────────────────┬───────────────────┘
                │ HTTP (Axios)             │ WebSocket /ws
┌───────────────▼──────────────────────────▼───────────────────┐
│                应用服务层（Spring Boot 4.1.1）                │
│  认证鉴权：JWT 过滤器 + 角色守卫 + 数据范围过滤                │
│  业务服务：任务调度 │ 告警处置 │ 台账管理 │ AI 助手 │ 文件上传  │
│  数据访问：MyBatis-Plus  →  MySQL 8 (community_db)            │
│  消息广播：WsBroadcaster（10 类事件）                          │
└───────────────┬──────────────────────────▲───────────────────┘
                │ HTTP 转发图片             │ 轮询领任务/上报
┌───────────────▼──────────────┐  ┌────────┴───────────────────┐
│   AI 识别服务（Flask :5000）  │  │  机器人端（ROS 虚拟机）      │
│  yolo11n：行人 resident/outsider│ │  robot_client.py            │
│  yolo11s：七类安全隐患        │  │  move_base 航点导航          │
│  PaddleOCR：车牌识别          │  │  定时抓拍 + 心跳位姿上报      │
└──────────────────────────────┘  └────────┬───────────────────┘
                                            │ ROS Topic / Action
                                  ┌─────────▼──────────┐
                                  │ Gazebo 虚拟社区场景 │
                                  └────────────────────┘
```

### 2.2 技术栈

| 层次    | 技术                     | 版本     | 说明                                            |
| ----- | ---------------------- | ------ | --------------------------------------------- |
| 后端框架  | Spring Boot            | 4.1.1  | Web、WebSocket、校验、依赖注入                         |
| 运行环境  | JDK                    | 17     | LTS                                           |
| 持久层   | MyBatis-Plus           | 3.5.17 | 单表 CRUD 免 SQL，PaginationInnerInterceptor 物理分页 |
| 数据库   | MySQL                  | 8.0    | 业务数据存储                                        |
| 认证    | jjwt + BCrypt          | 0.12.6 | 无状态令牌，密码散列存储                                  |
| 实时通信  | Spring WebSocket       | 随框架    | `/ws` 端点，会话按 userId/role 分组                   |
| 接口文档  | springdoc-openapi      | 随框架    | Swagger UI                                    |
| 文档解析  | Apache POI + PDFBox    | 随框架    | docx/xlsx/pdf 文本抽取                            |
| 前端框架  | Vue                    | 3.5    | 组合式 API                                       |
| 构建工具  | Vite                   | 5.4    | 开发代理、快速热更新                                    |
| UI 组件 | Element Plus           | 2.8.4  | 表格、表单、弹窗                                      |
| 状态管理  | Pinia                  | 2.2    | 登录态持久化                                        |
| 图表    | ECharts                | 5.5.1  | 折线图、饼图                                        |
| AI 服务 | Flask                  | 3.x    | 独立推理进程                                        |
| 目标检测  | Ultralytics YOLO       | 8.x    | yolo11n / yolo11s                             |
| 文字识别  | PaddleOCR              | 2.7+   | 中文车牌                                          |
| 机器人   | ROS Noetic + move_base | —      | 导航与避障                                         |
| 视觉桥接  | cv_bridge + OpenCV     | —      | ROS 图像与 OpenCV 互转                             |

### 2.3 关键技术点

| 技术点     | 实现方式                                                                                                |
| ------- | --------------------------------------------------------------------------------------------------- |
| 四道过滤器   | `JwtAuthFilter` 认证 → `RobotTokenFilter` 机器人认证 → `RoleGuardFilter` 接口级授权 → `DataScopeFilter` 社区级数据隔离 |
| 单任务占用调度 | 机器人轮询领取，已有执行中任务则续跑，否则领取最早待执行任务，保证一车一线                                                               |
| 异步识别    | `@Async("aiTaskExecutor")` 独立线程池（核心 4 / 最大 10 / 队列 200），不阻塞任务主流程                                    |
| 拍照请求协同  | `waiting`（待确认）→ `pending`（已放行）→ `processing` → `done`，两态各 180 秒 TTL                                 |
| 长文摘要    | 按 1 万字分段、最多 10 段，逐段提炼 300 字要点后合并去重                                                                  |
| 前端聚合分析  | 后端只提供明细分页，前端 `Promise.all` 拉取后 filter/reduce 聚合，ECharts 渲染                                          |

### 2.4 业务流程

```
创建任务 → 下发（TASK_DISPATCH）→ 机器人领取 → 航点导航 → 回传进度（TASK_PROGRESS）
   → 到点拍照（PHOTO_REQUEST / PHOTO_COMMAND）→ AI 识别（RECOGNITION_RESULT）
   → 告警（ALARM_NEW）或车牌（PLATE_NEW）→ 人工处置 → 数据分析 → 报表导出
```

> 更详细的架构与实现说明见 [项目说明文档.md](./项目说明文档.md)

---

## 三、安装运行指南

### 3.1 环境要求

| 组件      | 版本要求                 | 用途                  |
| ------- | -------------------- | ------------------- |
| JDK     | 17+                  | 后端运行                |
| Maven   | 使用自带 `mvnw`          | 后端构建                |
| MySQL   | 8.0                  | 数据存储                |
| Node.js | 18+                  | 前端构建（Vite 5）        |
| Python  | 3.8+                 | AI 服务、机器人端          |
| ROS     | Noetic（Ubuntu 20.04） | move_base、cv_bridge |

### 3.2 目录结构

```
community/
├── backend/          # Spring Boot 后端（端口 8080）
│   └── src/main/java/cn/sju/backend/
│       ├── controller/  service/  mapper/  entity/
│       ├── filter/      websocket/  config/  client/
├── frontend/         # Vue 3 前端（开发端口 5173）
│   └── src/{api,router,stores,views}
├── ai_service/       # Flask AI 识别服务（端口 5000）
│   ├── recognize.py
│   └── models/{best.pt, duo.pt}
├── robot_client.py   # ROS 机器人客户端
├── 项目说明文档.md
└── README.md
```

### 3.3 启动步骤

#### 第一步：初始化数据库

```sql
CREATE DATABASE community_db DEFAULT CHARSET utf8mb4;
```

数据库连接串默认 `jdbc:mysql://127.0.0.1:3306/community_db`，已启用 `useUnicode`、`characterEncoding=utf8` 与 `serverTimezone=Asia/Shanghai`。业务表结构见 [项目说明文档.md](./项目说明文档.md) 第五章。

#### 第二步：启动后端（:8080）

```bash
cd backend
./mvnw spring-boot:run                 # 开发模式
# 或打包运行
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

启动后访问接口文档：<http://localhost:8080/swagger-ui.html>

#### 第三步：启动 AI 识别服务（:5000）

```bash
cd ai_service
pip install -r requirements.txt
python recognize.py
```

确认 `models/` 下存在 `best.pt`（行人模型）与 `duo.pt`（多隐患模型），服务监听 `0.0.0.0:5000`，健康检查 `GET /health`。

#### 第四步：启动前端（:5173）

```bash
cd frontend
npm install
npm run dev
```

Vite 已配置三条代理，无需额外处理跨域：

| 路径         | 代理目标                         |
| ---------- | ---------------------------- |
| `/api`     | `http://localhost:8080`      |
| `/uploads` | `http://localhost:8080`      |
| `/ws`      | `ws://localhost:8080`（开启 ws） |

生产部署：

```bash
npm run build      # 产出 dist/
```

将 `dist/` 交由 Nginx 托管，并把上述三条路径反向代理到后端（`/ws` 需开启 WebSocket 升级），同时把后端 `cors.allowed-origins` 改成实际访问来源。

#### 第五步：启动机器人端（ROS 虚拟机）

```bash
export API_BASE_URL=http://192.168.160.1:8080   # 指向宿主机后端
export ROBOT_TOKEN=robot-dev-token              # 与后端 robot.token 一致
export ROBOT_ID=robot01
export POLL_INTERVAL=15
python3 robot_client.py
```

确保 ROS 仿真场景已启动（`move_base` 与 Gazebo 可用），机器人会每 15 秒轮询领取任务。

### 3.4 关键配置

后端配置位于 `backend/src/main/resources/application.yaml`，常用项：

| 配置项                                       | 默认值                                            | 说明                             |
| ----------------------------------------- | ---------------------------------------------- | ------------------------------ |
| `server.port`                             | 8080                                           | 服务端口                           |
| `spring.datasource.url`                   | `jdbc:mysql://127.0.0.1:3306/community_db?...` | 支持 `DB_URL` 覆盖                 |
| `spring.datasource.username` / `password` | root / —                                       | 支持 `DB_USERNAME`、`DB_PASSWORD` |
| `spring.servlet.multipart.max-file-size`  | 30MB                                           | 单文件上传上限                        |
| `jwt.secret`                              | 无默认值                                           | **必须**通过 `JWT_SECRET` 注入       |
| `jwt.expiration`                          | 86400000                                       | 令牌有效期 24 小时                    |
| `robot.token`                             | 无默认值                                           | **必须**通过 `ROBOT_TOKEN` 注入      |
| `robot.online-ttl-ms`                     | 30000                                          | 心跳超时判离线                        |
| `upload.dir`                              | uploads                                        | 图片目录，映射 `/uploads/**`          |
| `ai.url`                                  | `http://localhost:5000/recognize`              | AI 服务地址                        |
| `deepseek.api-key`                        | 空                                              | 通过 `DEEPSEEK_API_KEY` 注入       |
| `cors.allowed-origins`                    | `http://localhost:5173`                        | 允许的前端来源                        |

> ⚠️ **安全提示**：`application-dev.yaml` 内含本机开发用的数据库口令与接口密钥，`spring.profiles.active` 默认为 `dev`，**上线前务必切换为非 dev 配置并改用环境变量注入**。

### 3.5 常见问题

| 现象          | 原因与处理                                                     |
| ----------- | --------------------------------------------------------- |
| 前端请求 401    | 令牌过期，重新登录；确认 `JWT_SECRET` 在重启后未变化                         |
| 前端跨域被拦      | `cors.allowed-origins` 未包含实际访问来源                          |
| 抓拍图片加载失败    | 检查 `upload.dir` 目录是否存在且有写权限，`/uploads/**` 已映射             |
| AI 服务返回连接失败 | AI 服务未启动或 `ai.url` 配置错误                                   |
| 机器人一直显示离线   | `API_BASE_URL` 是否为宿主机 IP、`ROBOT_TOKEN` 是否与后端一致、ROS 网络是否连通 |
| 机器人领不到任务    | 确认任务已"下发"（状态为执行中），且无其他执行中任务占用                             |
| 拍照请求一直等待    | 管理员需在前端浮窗点击"立即拍照"放行，或 180 秒后自动失效                          |

---

## 四、团队信息

| 项    | 内容         |
| ---- | ---------- |
| 团队名称 | 疯人出逃22     |
| 项目名称 | 社区安防智能巡检系统 |
| 项目版本 | 2.0        |
| 项目代号 | community  |

### 联系方式

- 项目仓库：本地工作区 `community/`
- 问题反馈：欢迎提交 Issue 或联系团队成员
- 文档索引：[项目说明文档.md](./项目说明文档.md)

---

## 五、版本记录

| 版本      | 主要变化                                                              |
| ------- | ----------------------------------------------------------------- |
| 1.x（原型） | 验证机器人航点导航与单模型识别的可行性                                               |
| 2.0（当前） | 双 YOLO 模型 + PaddleOCR、WebSocket 实时推送、告警台账、数据分析、AI 助手、三级权限与社区级数据隔离 |

---

## 六、致谢

感谢 Ultralytics、PaddleOCR、Spring、Vue、Element Plus、ROS 等开源社区提供的优秀工具与文档，本项目在模型训练与系统实现中大量受益于上述开源成果。
