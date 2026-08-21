# 企业招聘简历审查系统（lion-resume-review-agent）

基于 **Spring AI Alibaba Graph（状态机工作流）+ 阿里千问大模型 + SSE 流式通信 + Langfuse 可观测性** 的简历 AI 审查系统。
前端通过「流程追踪面板」实时可视化 Graph 各节点的执行状态（进度条 / 节点高亮 / 打字机报告），支持历史报告管理、报告导出与重新审查。

---

## 目录

- [主要功能点](#主要功能点)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [Graph 工作流设计](#graph-工作流设计)
- [项目结构](#项目结构)
- [快速启动](#快速启动)
- [环境变量配置](#环境变量配置)
- [API 一览](#api-一览)
- [SSE 事件协议](#sse-事件协议)
- [核心实现机制](#核心实现机制)
- [Langfuse 可观测性](#langfuse-可观测性)
- [常见问题](#常见问题)
- [生产化建议](#生产化建议)

---

## 主要功能点

### 1. 登录鉴权
- 独立全屏登录页，深蓝渐变背景 + 居中卡片，未登录不渲染主面板（两个独立页面切换）。
- 基于 **Sa-Token** 的无状态 Token 认证，默认 30 天有效期。
- 退出登录后自动清理前端所有业务状态并关闭 SSE 连接，回到登录页。

### 2. 简历上传
- 支持 PDF / TXT / MD / DOC 等简历文件上传（限制 20MB）。
- 上传成功后隐藏上传框，展示文件信息卡片（文件名 / 大小），支持「重新上传」。
- 上传即解析（OCR/文档解析 → 结构化 JSON），解析状态入库（PENDING/PARSED/FAILED）。

### 3. AI 审查工作流（Graph 编排）
- 七个节点状态机：解析 → 技能评分 / 项目评分 / 文化契合度（并行）→ 综合诊断 → 人工复核 → 生成报告。
- 前端流程追踪面板实时高亮节点状态：执行中（蓝）、完成（绿）、异常（红）、重试中（橙）。
- 右侧 **ECharts 雷达图** 实时展示六维评分，随节点完成动态更新，面板可拖拽调整尺寸。
- 连线带箭头指向，活跃路径蓝色高亮 + 动画流动，视觉更直观。

### 4. 报告生成与展示
- LLM 以 **SSE 流式（打字机效果）** 生成详细诊断报告。
- 报告包含：综合评分、六维雷达图、文字诊断摘要、结构化 Markdown 详报。
- 报告支持一键**导出**为独立 HTML / Markdown 文件（含评分 + 六维表格）。

### 5. 人机协同（Human-in-the-Loop）
- 当 AI 六维均分低于阈值时，任务进入 `human_review` 挂起状态。
- 前端自动弹出 HR 复核弹窗，HR 可查看雷达图、调整各维度评分、填写复核意见。
- HR 确认后任务恢复，驱动**后置子图**重新计算综合评分并继续生成报告。

### 6. 历史报告管理
- 所有审查报告自动保存，独立「历史报告」页面分页展示。
- 表格列：简历文件 | 评分 | 摘要 | 状态 | **生成耗时** | 生成时间 | 操作。
- 支持**查看详情**（抽屉内展示完整报告 + 雷达图）、**删除**（二次确认 + 级联删除任务）、**关键词搜索**（摘要 / 简历文件名）。
- 显示**生成耗时**（从任务创建到报告完成的秒数，格式化为 `xx 分 xx 秒`）。

### 7. 重新审查
- 历史报告支持对同一简历**重新走完整流程**，可输入新的岗位描述（JD）进行二次生成。
- 重新审查带**实时进度条**（SSE 驱动，`topology` 事件更新百分比），完成后自动回到主面板展示新报告。

### 8. 面板拖拽布局
- 左侧 / 右侧 / 上下区域均可**拖拽调整尺寸**（CSS Grid 变量 + flex 比例）。
- 雷达图通过 ResizeObserver 实时重绘，自适应面板尺寸。

### 9. LLM 全链路可观测（Langfuse）
- 集成 **OpenTelemetry OTLP + Langfuse**，LLM 调用自动形成完整 trace 链路。
- 异步线程池通过 `ContextPropagatingTaskDecorator` 自动传播 trace context，HTTP 请求 → Graph 节点 → LLM 调用一条链路贯穿。

---

## 技术栈

| 层 | 技术 | 说明 |
| --- | --- | --- |
| 前端框架 | Vue 3 + Vite + `<script setup>` | 组合式 API |
| UI 组件库 | Element Plus（中文 locale） | 弹窗 / 表格 / 上传 / 进度条 |
| 流程可视化 | Vue Flow | 流程追踪面板（带箭头连线） |
| 图表 | ECharts | 六维雷达图 |
| 流式通信 | EventSource（SSE） | 节点状态 + 报告流式推送 |
| 状态管理 | 轻量 reactive store（composable） | 无 Pinia 依赖 |
| 后端框架 | Spring Boot 3.5.4 | Java 17+ |
| AI 编排 | Spring AI Alibaba 1.1.2.3（Graph + DashScope） | 状态机工作流 |
| 大模型 | 阿里千问 qwen3.8-max（DashScope 多模态） | LLM 评分与报告生成 |
| ORM | MyBatis-Plus 3.5.x | LambdaQueryWrapper / 逻辑删除 / 雪花 ID |
| 数据库 | MySQL 8.x（utf8mb4） | 简历 / 任务 / 报告三张表 |
| 缓存/状态 | Redis | Graph 状态快照 + SSE 事件 Pub/Sub 广播 |
| 向量库 | Milvus（可选） | Spring AI VectorStore + 历史相似人才检索 |
| 鉴权 | Sa-Token | Token 登录态 |
| 可观测性 | OpenTelemetry + Langfuse | OTLP 导出 LLM 调用链路 |
| 构建 | Maven / npm | 前后端分离 |

---

## 系统架构

```
┌─────────────────────────── 前端 (Vue3, :5173) ───────────────────────────┐
│  LoginView ──► AppShell（面板可拖拽布局）                                    │
│  ├─ UploadPanel  上传简历 → 文件信息卡片                                     │
│  ├─ FlowPanel    Vue Flow 流程追踪面板（节点状态高亮 + 箭头连线）            │
│  ├─ RadarChart   ECharts 六维雷达图（ResizeObserver 自适应）                │
│  ├─ ReportPanel  流式报告 + 导出 HTML/Markdown                             │
│  ├─ ReportHistory 历史报告列表（分页/搜索/删除/重新审查/生成耗时）            │
│  └─ ReviewModal  HR 人工复核弹窗                                            │
└──────────────┬──────────────────────────────────────────────────────────┘
               │ REST /api/** + SSE /api/review/stream/{taskId}
┌──────────────▼──────────────────────── 后端 (Spring Boot, :8080) ─────────┐
│  AuthController（Sa-Token）  ResumeController（上传）  ReviewController      │
│        │                                                                   │
│  ReviewTaskService ── startReview() 创建任务 + 异步执行 Graph               │
│        │                     │                                             │
│        │              ResumeReviewGraph（StateGraph 装配，主图）            │
│        │              7 个 Action 节点（parse/skill/project/culture/       │
│        │              synthesis/human_review/generate_report）             │
│        │                     │                                             │
│        │              HR 复核确认后 → ResumeReviewPostGraph（后置子图）     │
│        │                     │                                             │
│        │              BaseTrackingAction 切面 → 节点状态上报                │
│        │                     ▼                                             │
│  GraphEventPublisher ──► Redis Pub/Sub ──► 本机 SseEmitterManager          │
│        │                     │                                             │
│        │              GraphStateCache（Redis 状态快照，支持中断恢复）        │
│        ▼                                                                   │
│  ReviewReportService ── 报告保存 / 向量化入库 / 相似人才检索                 │
│        │                                                                   │
│  LlmService（ChatClient）── 自动插桩 ──► Langfuse（OTLP / OpenTelemetry）  │
└────────────────────────────────────────────────────────────────────────────┘
```

### 数据流（发起一次审查）

1. 前端上传简历 → `POST /api/resume/upload` → 返回 `resumeId`。
2. 填写岗位描述 → `POST /api/review/start` → 后端创建 `ReviewTask`，立即返回 `taskId`（异步执行 Graph，不阻塞 HTTP）。
3. 前端用 `taskId` 建立 `EventSource` 连接 `/api/review/stream/{taskId}`。
4. Graph 各节点通过切面上报状态 → Redis Pub/Sub 广播 → SSE 推送到前端。
5. 前端收到 `topology` / `node` / `task` / `stream` 事件，驱动流程面板、雷达图、报告流式渲染。
6. 任务完成 → 报告落库（`t_review_report`）→ 前端拉取详情并展示。
7. 整个调用链（HTTP → Graph → LLM）由 OpenTelemetry 自动追踪，导出到 Langfuse。

---

## Graph 工作流设计

### 主图（ResumeReviewGraph）

```
START（__START__）
  └── parse_resume（简历解析：OCR/文档解析 → 结构化 JSON）
        ├──→ skill_scoring    ──┐   并行评分（fan-out）
        ├──→ project_scoring  ──┤──→ synthesis（fan-in 汇合屏障）
        └──→ culture_scoring  ──┘
                    └── synthesis（综合诊断：六维雷达图 + 诊断摘要）
                          └── human_review（人机协同：边缘候选人工复核）
                                └── generate_report（LLM 流式生成报告）
                                      └── END（__END__）
```

核心机制：

- **并行执行**：`parse_resume` 多出边 fan-out 同时触发 3 个 LLM 评分任务；3 个评分节点 fan-in 汇聚到 `synthesis`（屏障，全部完成后进入），大幅降低耗时。
- **状态注入**：每个节点执行前后（自定义切面 `BaseTrackingAction`）向状态注入进度，并同步到 Redis 状态快照。
- **状态键策略（GraphConfig）**：全局状态各键的合并策略可配置——`warnings` 用 `AppendStrategy`（多节点累积追加，去重合并），`overall_score` 用 `ReplaceStrategy`（单值覆盖）；未注册键默认走覆盖策略。
- **SSE 推送**：节点事件（`{"node":"parse_resume","status":"running","progress":30}`）经 Redis Pub/Sub 广播 → SSE 客户端实时收到，支持多实例横向扩展。
- **容错重试**：LLM 限流/网络抖动自动重试（默认 3 次，间隔 1.5s，指数退避），SSE 推送 `retrying` 状态。
- **人机协同**：AI 六维均分低于阈值（`resume.borderline-score`，默认 6）时进入 `human_review` 挂起，SSE 通知前端弹出 HR 审核框；HR 调整评分确认后恢复报告生成。

### 后置子图（ResumeReviewPostGraph）

解决「HR 复核确认后仍有后续流程」的可扩展性问题：

```
START
  └── generate_report（复用主图节点：approved=true 时直接生成报告）
        └── (未来: background_check / interview / offer ...)
              └── END
```

- `approve()` 职责单一：校验状态 → 应用 HR 调整 → 标记已复核 → **驱动后置子图异步执行**，不关心具体下游动作。
- 未来新增流程（背调 / 面试安排 / Offer 建议）只需在子图中追加节点/分支，**无需改动 approve 逻辑**。

### 挂起/恢复机制（Human-in-the-Loop）

采用 **「节点内决策 + 服务端恢复」** 模式（非框架 interrupt 挂起）：

1. `HumanReviewAction`：均分 < borderline-score → 置 `NEEDS_REVIEW`，保存待复核快照到 Redis，SSE 推送 `pending` 事件，前端弹审核框。
2. `GenerateReportAction`：感知 `needs_review && !approved` → 挂起返回 `report_ready=false`。
3. HR 在 `/api/review/approve` 确认 → `ReviewTaskService.approve` 应用调整评分、置 `approved=true`，驱动 **`ResumeReviewPostGraph`** 子图从状态快照继续 → 重新执行 `generate_report` → 完成。

---

## 项目结构

```
lion-resume-review-agent/           # 工程根目录 = Spring Boot 后端根目录
├── pom.xml                         # Maven 构建
├── src/main/
│   ├── java/com/lion/resume/
│   │   ├── ResumeReviewApplication.java   # 启动类（@EnableAsync）
│   │   ├── config/                        # AsyncConfig(线程池+trace传播) / CorsConfig / RedisPubSubConfig / SaTokenConfig
│   │   ├── common/                        # R<T> 统一返回 / BizException / GraphNodes 常量(拓扑) / NodeStatus / TaskStatus
│   │   ├── component/TaskProgressService  # 节点进度上报（DB + Redis 快照 + SSE）
│   │   ├── dto/                           # GraphStatusEvent(SSE 载荷) / StartReviewRequest / ApproveRequest
│   │   ├── cache/GraphStateCache.java     # Redis 状态快照（24h TTL，支持恢复）
│   │   ├── sse/                           # SseEmitterManager / GraphEventPublisher / GraphEventSubscriber
│   │   ├── graph/                         # Graph 装配 + 状态键策略
│   │   │   ├── ResumeReviewGraph.java     #   主图装配（7 节点拓扑）
│   │   │   ├── ResumeReviewPostGraph.java #   后置子图装配（复核后流程）
│   │   │   ├── GraphConfig.java           #   状态键合并策略（Append/Replace）
│   │   │   ├── GraphProgress.java         #   节点 → 进度区间映射
│   │   │   ├── ResumeReviewProps.java     #   流程配置属性类
│   │   │   └── action/                    # BaseTrackingAction / AbstractScoringAction
│   │   │       #    ParseResumeAction / SkillScoringAction / ProjectScoringAction
│   │   │       #    CultureScoringAction / SynthesisAction / HumanReviewAction / GenerateReportAction
│   │   ├── entity/ mapper/                # 三实体 + MyBatis-Plus Mapper
│   │   ├── service/                       # ReviewTaskService / ReviewReportService / ResumeService / LlmService
│   │   ├── controller/                    # AuthController / ResumeController / ReviewController / ChatController
│   │   └── util/ResumeParser.java         # PDFBox 文本解析
│   └── resources/
│       ├── application.yml                # 配置（敏感值全部走 ${ENV_VAR:默认} 占位符）
│       └── schema.sql                     # MySQL 建表脚本
├── .env.example                  # 环境变量模板（可提交，仅占位符）
├── .env                          # 本地环境变量（gitignored，真实密钥，勿提交）
├── data/                         # 上传简历存储目录（./data/uploads）
├── frontend/                     # Vue3 前端
│   ├── package.json / vite.config.js
│   └── src/
│       ├── main.js                  # Element Plus + Vue Flow + 全局样式
│       ├── App.vue                  # 登录态切换 + SSE 监听 + 面板拖拽布局
│       ├── api.js                   # axios 封装（R 数据解包 / Token 注入）
│       ├── store.js                 # 全局响应式状态
│       ├── composables/useReportExport.js  # 报告导出（HTML/Markdown）
│       └── components/
│           ├── LoginView.vue        # 独立登录页
│           ├── UploadPanel.vue      # 上传面板
│           ├── FlowPanel.vue        # 流程追踪面板（Vue Flow + 箭头连线）
│           ├── StatusNode.vue       # 节点状态展示组件
│           ├── RadarChart.vue       # 六维雷达图（ECharts）
│           ├── ReportPanel.vue      # 报告展示 + 导出
│           ├── ReportHistory.vue    # 历史报告（列表/详情/删除/重新审查）
│           └── ReviewModal.vue      # HR 人工复核弹窗
└── docker-compose.yml               # MySQL + Redis + Milvus(etcd/minio) 一键启动
```

---

## 快速启动

### 前置条件

- JDK 17+、Maven 3.8+、Node.js 18+
- Docker（可选，用于 MySQL / Redis / Milvus）
- 阿里云 DashScope API Key（可选，未配置时 LLM 节点返回模拟数据）

### 1. 准备环境变量

敏感配置（数据库口令 / API Key / 演示账号密码等）一律通过环境变量注入，`application.yml` 中无任何明文密钥：

```bash
cp .env.example .env   # 复制模板，按需填写 MySQL / Redis / DashScope / Langfuse 等真实值
```

> 详见下文 [环境变量配置](#环境变量配置)。

### 2. 启动基础设施

```bash
docker compose up -d mysql redis          # 最小依赖
# 如需 Milvus 向量检索（可选）：
docker compose up -d
```

> compose 首次启动会自动执行 `schema.sql` 建表。手动建库：
> ```bash
> > mysql -u root -p -e "CREATE DATABASE resume_review DEFAULT CHARACTER SET utf8mb4;"
> > mysql -u root -p resume_review < src/main/resources/schema.sql
> ```

### 3. 启动后端

```bash
# 在工程根目录执行（.env 就绪后即可直接启动）
mvn spring-boot:run
```

> 未配置 `DASHSCOPE_API_KEY` 时自动进入演示模式，LLM 节点返回模拟数据，全流程可跑通。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`，演示账号 `admin / admin123`（可用 `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` 环境变量覆盖）。

### 5. 体验流程

1. 登录 → 上传简历（PDF/TXT/MD）→ 填写岗位描述 → 点击「发起 AI 简历审查」。
2. 中间「流程追踪面板」实时高亮当前节点（蓝色=执行中、绿色=完成、红色=异常），连线箭头指示流程方向。
3. 右侧雷达图实时展示六维评分；报告以打字机效果流式渲染。
4. 若 AI 评分低于及格线，自动弹出「HR 人工复核」弹窗，调整评分后继续。
5. 完成后在「历史报告」中查看 / 搜索 / 删除 / 重新审查 / 导出报告。

### 6. 效果图

![image-20260821223521448](README.assets/image-20260821223521448.png)

![image-20260821223538235](README.assets/image-20260821223538235.png)

![image-20260821223908796](README.assets/image-20260821223908796.png)



---

## 环境变量配置

所有敏感配置（数据库口令、API Key、演示账号密码等）均通过**环境变量**注入。`application.yml` 中只保留 `${ENV_VAR:默认值}` 占位符，不出现任何明文密钥。

### 加载方式

- **本地开发**：工程根目录放置 `.env` 文件（已 gitignore），`application.yml` 通过 `spring.config.import: optional:file:.env[.properties]` 自动加载：

  ```bash
  cp .env.example .env   # 首次克隆后执行，按需修改
  ```

- **IDE / 裸机**：也可不建 `.env`，直接在 IDEA Run Configuration 或系统环境变量中设置同名变量，Spring 会自动映射（如 `DASHSCOPE_API_KEY` → `spring.ai.dashscope.api-key`）。
- **容器 / 生产**：docker-compose 中 `env_file: .env` 或在服务 `environment:` 注入同名变量；K8s Secret / Vault / KMS 运行时注入同样生效。

### 变量清单

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_URL` | MySQL JDBC 连接串 | `jdbc:mysql://localhost:3306/resume_review?...` |
| `MYSQL_USERNAME` | MySQL 账号 | 空（必填） |
| `MYSQL_PASSWORD` | MySQL 密码 | 空（必填） |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 / 端口 | `localhost` / `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key（不配置则 LLM 进入演示模式） | 空 |
| `MILVUS_HOST` | Milvus 地址（留空即关闭向量检索） | `118.25.109.72` |
| `LANGFUSE_OTLP_ENDPOINT` | Langfuse OTLP 端点（必须含 `/api/public/otel`） | `https://us.cloud.langfuse.com/api/public/otel/v1/traces` |
| `LANGFUSE_AUTH_BASIC` | Langfuse OTLP Basic Auth：`Basic base64(pk:sk)` | 空 |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | 演示账号（不设置时后端代码兜底 `admin/admin123`） | `admin` / `admin123` |

---

## API 一览

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 Token | 开放 |
| GET | `/api/auth/info` | 查询登录态 | 开放 |
| POST | `/api/auth/logout` | 退出登录 | 开放 |
| POST | `/api/resume/upload` | 上传简历（multipart） | 开放 |
| GET | `/api/resume/{id}` | 简历详情 | 需登录 |
| POST | `/api/review/start` | 发起审查，返回 `taskId` | 需登录 |
| GET | `/api/review/stream/{taskId}` | **SSE 订阅节点实时状态** | 开放 |
| GET | `/api/review/task/{taskId}` | 任务详情（任务+报告+拓扑） | 需登录 |
| POST | `/api/review/approve` | HR 复核确认（驱动后置子图恢复） | 需登录 |
| GET | `/api/review/topology` | Graph 拓扑 | 需登录 |
| GET | `/api/review/reports` | 历史报告分页列表（`page`/`size`/`keyword`） | 需登录 |
| DELETE | `/api/review/reports/{reportId}` | 删除报告（级联删任务） | 需登录 |
| GET | `/api/review/ping` | 健康检查 | 需登录 |
| POST | `/chat` | 测试用聊天接口 | 开放 |

---

## SSE 事件协议

`GET /api/review/stream/{taskId}` 建立 SSE 长连接，服务端按节点推送以下事件（`event name` + JSON data）：

| 事件名 | 说明 | 载荷示例 |
| --- | --- | --- |
| `topology` | Graph 拓扑（连接建立后立即推送一次） | `{ nodes: [...], edges: [...] }` |
| `node` | 节点状态变化（含 `__START__` 开始节点） | `{ "node":"skill_scoring", "status":"running", "progress":45, "message":"..." }` |
| `task` | 任务整体状态 | `{ "status":"completed", "progress":100, "message":"..." }` |
| `stream` | 报告流式 chunk（打字机） | `{ "data":"该候选人的技术栈..." }` |
| `info` | 附加信息日志 | `{ "message":"..." }` |

节点状态枚举：`pending` / `waiting` / `running` / `stream` / `completed` / `error` / `retrying` / `resumed`。

> 说明：`__START__` 节点在任务创建时即置为 `completed`（node 事件），`__END__` 节点在任务完成/失败时同步更新（node 事件），保证流程面板首尾节点状态完整。

---

## 核心实现机制

### 前端
- **登录态切换**：`App.vue` 顶层 `v-if="!store.loggedIn"` 渲染 `LoginView`，否则渲染主面板；退出时清空全部业务状态并关闭 SSE。
- **SSE 监听**：统一在 `App.vue` 管理，`addEventListener` 分发 `topology` / `node` / `task` / `stream` / `info` 事件，写入 `store` 驱动各组件。
- **面板拖拽**：CSS Grid 列模板 `var(--left-w) 8px 1fr 8px var(--right-w)`，mousedown 记录起点 + mousemove 更新变量；上下比例用 flex 百分比。
- **流程连线箭头**：`FlowPanel.vue` 中每条 edge 配置 `markerEnd: { type: 'arrow' }`，颜色随连线激活状态联动（蓝/灰）。
- **报告导出**：`useReportExport.js` 前端直接生成独立 HTML（含样式、评分表、六维表格）与 Markdown 文件，零后端依赖。
- **重新审查进度条**：`ReportHistory.vue` 内独立 SSE 连接，`topology` 事件更新百分比，`report` 事件 100% 后自动关闭弹窗并切回主面板。

### 后端
- **异步执行**：`@EnableAsync` + 自定义 `graphExecutor` 线程池执行 Graph，`startReview` 立即返回 `taskId`，避免 HTTP 长连接超时。
- **状态广播**：节点事件 → `GraphEventPublisher` → Redis Pub/Sub → `SseEmitterManager` 推送到对应 `taskId` 的 SSE 通道；Redis 不可用时降级为本机直推。
- **状态快照**：`GraphStateCache` 将 Graph 执行状态（含待复核快照）写入 Redis（24h TTL），支持中断恢复与多实例共享。
- **状态键策略**：`GraphConfig` 定义各状态键合并策略（`AppendStrategy` / `ReplaceStrategy`），并行节点写入同一键时自动累积合并。
- **人机协同恢复**：Graph 挂起于 `human_review`，`approve()` 应用 HR 调整评分、重算综合分，**驱动 `ResumeReviewPostGraph` 后置子图**继续执行。
- **trace 跨线程传播**：`AsyncConfig` 中两个线程池配置 `ContextPropagatingTaskDecorator`，提交任务时捕获 OTel/Micrometer context 并在工作线程恢复，LLM 调用自动形成完整链路，无需手动创建 Span。
- **生成耗时**：`Duration.between(任务 createTime, 报告 updateTime)` 计算，取整秒。
- **统一响应**：`R<T>` 封装 + 全局异常处理，前端 axios 拦截器自动解包 `data`。

---

## Langfuse 可观测性

访问地址：https://us.cloud.langfuse.com/

### 集成原理

```
HTTP 请求（POST /api/review/start）
   │  Spring Boot 自动 instrumentation 创建根 Span
   ▼
graphExecutor 线程池（ContextPropagatingTaskDecorator 传播 trace context）
   │
   ▼
Graph 节点 → LlmService → chatClient.prompt()...call()
   │  Spring AI 自动插桩（spring-ai-observability）创建子 Span
   ▼
OTLP Exporter → https://us.cloud.langfuse.com/api/public/otel/v1/traces
```

### 配置说明（application.yml）

```yaml
management:
  otlp:
    tracing:
      # Langfuse Cloud (US) OTLP 端点；路径必须含 /api/public/otel，缺了会 404
      endpoint: ${LANGFUSE_OTLP_ENDPOINT:https://us.cloud.langfuse.com/api/public/otel/v1/traces}
      headers:
        # OTLP 端点的认证方式为 Basic Auth：base64(pk-lf-xxx:sk-lf-xxx)，经环境变量注入
        Authorization: "${LANGFUSE_AUTH_BASIC:}"
        # 实时摄取：不加此头，OTel 数据可能延迟最多 10 分钟才入库
        x-langfuse-ingestion-version: "4"
```

`LANGFUSE_AUTH_BASIC` 的值形如 `Basic <base64(pk:sk)>`，在 `.env` 中配置（已 gitignore），不再写死在 `application.yml`。

### 关键点

- **端点是 OTLP 专用**：`/api/public/otel/v1/traces`（EU: `cloud.langfuse.com` / US: `us.cloud.langfuse.com` / JP: `jp.cloud.langfuse.com`）。
- **认证是 Basic Auth**：`base64(publicKey:secretKey)`，不是 `x-langfuse-public-key`（那是 HTTP API 用的）。
- **无需手动 Span**：依赖线程池 `ContextPropagatingTaskDecorator` 自动传播，业务代码零侵入。
- **零侵入成本**：只需 OTLP 配置 + `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` 依赖，Spring AI 自动插桩即可工作。

![image-20260821223757850](README.assets/image-20260821223757850.png)

![image-20260821220752798](README.assets/image-20260821220752798.png)

---

## 常见问题

- **启动报 Redis 连接失败**：先 `docker compose up -d redis`；Redis 不可用时 SSE 会降级为本机直推，不阻塞主流程。
- **进度条一直 0% / 不更新**：确认前端使用 `addEventListener('topology'/'node'/'task', ...)` 监听自定义事件名（不是 `onmessage`），且 SSE URL 的 `taskId` 为字符串（对象会导致 `[object Object]`）。
- **流程面板首尾节点不显示完成**：`__START__` / `__END__` 节点需要 `node` 类型事件更新（分别由 startReview / markCompleted / markFailed 发出），仅 `task` 事件不会更新节点面板。
- **生成耗时显示 0**：耗时为「任务创建 → 报告完成」时间差，请确认 `t_review_task.create_time` 与 `t_review_report.update_time` 均已正确写入。
- **Milvus 未启动**：注释掉 `spring.ai.vectorstore.milvus.client.host` 即关闭向量检索，不影响审查主流程。
- **Langfuse 报 404**：OTLP 端点必须是 `/api/public/otel/v1/traces`（含 `/public/`），且认证用 Basic Auth。
- **Langfuse 一直 Waiting for first trace**：确认 LLM 调用已发生（真实 Key 或演示模式都会调用 ChatClient）；若配置了手动 Span 又想移除，确保线程池已配 `ContextPropagatingTaskDecorator`。
- **MySQL 版本**：连接串含 `characterEncoding=utf8&serverTimezone=Asia/Shanghai`，需 MySQL 8.x。MyBatis-Plus 默认开启逻辑删除（`t_resume.deleted`），建表脚本已包含对应字段。

