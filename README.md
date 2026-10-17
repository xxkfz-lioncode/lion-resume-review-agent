<div align="center">

<h1>🦁 Lion Resume Review · AI 招聘审查智能体</h1>

<p><strong>基于 Spring AI Alibaba Graph 的简历审查智能体 —— 7 节点状态机 · 并行评分 · 人机协同断点续跑</strong></p>

<p>
  <img src="https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.5.4">
  <img src="https://img.shields.io/badge/Spring%20AI%20Alibaba%20Graph-1.1.2.3-6DB33F?logo=spring&logoColor=white" alt="Spring AI Alibaba">
  <img src="https://img.shields.io/badge/DashScope-%E5%8D%83%E9%97%AE-615CED?logo=alibabacloud&logoColor=white" alt="DashScope 千问">
  <img src="https://img.shields.io/badge/MySQL-8.x-4479A1?logo=mysql&logoColor=white" alt="MySQL 8.x">
  <img src="https://img.shields.io/badge/Redis-7.x-DC382D?logo=redis&logoColor=white" alt="Redis 7.x">
  <img src="https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3.5">
</p>

<p>
  <a href="#核心功能">核心功能</a> ·
  <a href="#项目亮点">项目亮点</a> ·
  <a href="#graph-实战从零上手">Graph 实战</a> ·
  <a href="#快速启动">快速启动</a> ·
  <a href="#环境变量配置">环境变量</a> ·
  <a href="#常见问题">常见问题</a>
</p>

</div>

---

基于 **Spring AI Alibaba Graph** 构建的 AI 简历审查系统。用 7 节点状态机编排「简历解析 → 技能 / 项目 / 文化并行评分 → 综合诊断 → 人工复核 → 生成报告」全链路，人工复核节点通过官方 `InterruptableAction` 真正挂起并落盘 Checkpoint，HR 确认后从断点续跑；全流程状态通过 Redis Pub/Sub + SSE 实时推送到前端。

---

## 核心功能

### 1. AI 简历审查工作流（Graph 编排）

- 7 节点状态机：`parse_resume → [skill_scoring | project_scoring | culture_scoring] → synthesis → human_review → generate_report`。
- **并行执行**：3 个评分节点 fan-out 同时跑，synthesis 节点 fan-in 等待全部完成后进入。
- 前端 Vue Flow 流程追踪面板实时高亮节点状态，ECharts 六维雷达图动态刷新，报告通过 SSE 打字机流式渲染。
- 后端异步执行 Graph，`startReview` 立即返回 `taskId`，HTTP 不被长流程阻塞。

### 2. Human-in-the-Loop 人工复核

- `human_review` 节点实现 `InterruptableAction`，执行后返回 `InterruptionMetadata`，Graph 真正挂起并写入 Checkpoint。
- HR 调整评分 → 后端通过 `CompiledGraph.updateState()` 写回新状态 → `RunnableConfig.resume()` 从 `human_review` 断点继续生成报告。
- 已完成任务支持**重新复核**：从历史 Checkpoint 中定位 `nodeId == human_review` 的那一条，用其 `checkPointId` 续跑。

### 3. 可插拔持久化与横向扩展

- Checkpoint 存储可切换：`resume.checkpoint-store = redis（默认，带 TTL）| mysql（落库，自动建表）`。
- SSE 事件经 Redis Pub/Sub 广播，多实例下任意节点都能把事件推给对应浏览器。
- 向量库可选：`.env` 中 `VECTORSTORE_TYPE=none` 即可关闭 Milvus，不影响审查主流程。

### 4. LLM 可观测与配置安全

- `LlmService` 统一封装 LLM 调用，失败自动重试 3 次 + 指数退避，并向前端推送 `retrying` 状态。
- `ContextPropagatingTaskDecorator` 自动传播 OTel trace context，HTTP → Graph 节点 → LLM 调用形成完整链路，导出到 Langfuse。
- 所有密钥通过环境变量注入（`.env` 加载），`application.yml` 中无明文密钥。

---

## 项目亮点

- **Spring AI Alibaba Graph 状态机工作流**：使用 `StateGraph`、`CompiledGraph`、`RunnableConfig`、`Checkpoint` 实现真正的状态机编排，不是手写 if/else 流程控制。
- **官方 Human-in-the-Loop 中断恢复**：`InterruptableAction#interruptAfter` 返回 `InterruptionMetadata` → Checkpoint 落盘 → `updateState + resume` 续跑，已执行节点**不重跑**。
- **Checkpoint 回溯能力**：`reapprove` 通过 `checkpointSaver.list(threadId)` 过滤 `human_review` 检查点，支持已完成任务的重新复核。
- **并行 fan-out / fan-in**：`parse_resume` 三条出边触发并行评分，三个节点汇聚到 `synthesis` 形成屏障，显著降低耗时。
- **声明式状态合并策略**：`KeyStrategyFactory` 配置 `AppendStrategy`（warnings 累积）/ `ReplaceStrategy`（评分覆盖）/ `CustomStrategy` 自定义规则。
- **断点恢复状态兜底**：恢复时以 DB 为权威来源补齐 `taskId` / `resumeId` / `job_description` / 简历原文，防止 Checkpoint 序列化丢失未注册字段。
- **幂等 CAS 状态机**：恢复前用 MyBatis-Plus 条件更新抢占状态，避免 HR 重复点击导致两次恢复、双倍 LLM 开销。
- **多实例事件广播**：`GraphEventPublisher → Redis Pub/Sub → SseEmitterManager` 架构，后端可无状态横向扩展。
- **LLM 全链路可观测**：异步线程池通过 `ContextPropagatingTaskDecorator` 传播 OTel context，业务代码零手动 Span。
- **配置安全**：`.env` 文件由 `spring.config.import` 加载且已 gitignore，生产可用 K8s Secret / Vault 注入同名变量。

---

## Graph 实战教程

> 这一节既是 Spring AI Alibaba Graph 的入门讲解，也是本项目的使用说明：每个知识点都配了项目里的真实代码。

### 1. 心智模型：Graph 到底是什么

一句话概括：**Graph = 一张有向图 + 一份共享状态 + 一个可持久化的执行引擎**。

| 组成 | 是什么 | 类比 |
| --- | --- | --- |
| **State** | 一个全局共享的 `Map`（`OverAllState`），所有节点都能读 | 流水线上的"物料" |
| **Node** | 一段业务逻辑，读入 State、返回**增量 Map** | 流水线上的"工位" |
| **Edge** | 决定下一步走哪个节点 | 工位之间的"传送带" |
| **Checkpoint** | 每执行完一个节点，State 被序列化存一次 | 流水线的"存档点" |
| **CompiledGraph** | 图编译后的执行器，真正跑起来的是它 | 已排产的"运行实例" |

关键心智转变：**节点之间不互相调用**。节点只负责「读 State → 算 → 返回增量」，谁来跑、跑几次、什么时候停，全部由图和框架决定。这正是它比「一个 Service 里顺序调 7 个方法」更适合 Agent 场景的原因——流程可声明、可回溯、可中断。

### 2. 核心 API 速查

| API | 作用 | 项目中的用法 |
| --- | --- | --- |
| `StateGraph` | 定义节点与边的有向图 | `ResumeReviewGraph` 装配 7 个节点 |
| `OverAllState` | 全局共享状态 | 承载简历原文 / 评分 / 雷达图 / 复核意见 |
| `NodeAction` | 同步节点：`Map apply(OverAllState)` | 项目节点经 `BaseTrackingAction` 间接实现 |
| `AsyncNodeActionWithConfig` | 异步节点，可拿到 `RunnableConfig` | 需要读线程配置时用 |
| `EdgeAction` | 条件边路由：`String apply(OverAllState)` | 返回目标节点名 |
| `KeyStrategyFactory` | 声明每个状态键的合并策略 | `GraphConfig` |
| `CompileConfig` + `SaverConfig` | 编译期配置（注入 Checkpoint 存储） | 注入 `BaseCheckpointSaver` |
| `CompiledGraph` | 编译产物：`invoke` / `stream` / `getState` / `updateState` | Spring 单例 Bean |
| `RunnableConfig` | 运行时配置：`threadId` / `checkPointId` / `nextNode` / `resume()` | `threadId = taskId` |
| `BaseCheckpointSaver` | Checkpoint 持久化抽象 | `RedisSaver` / `MysqlSaver` |
| `InterruptableAction` | 节点级中断：`interrupt()` / `interruptAfter()` | `HumanReviewAction` |

### 3. State：全局状态与合并策略

`OverAllState` 本质是一个 `Map<String, Object>`：**节点不直接改它，而是返回一个"增量 Map"，由框架按 `KeyStrategy` 合并进去**。

为什么要声明合并策略？因为并行节点会**同时**往同一个键写值，框架需要知道是"追加"还是"覆盖"。

```java
public static KeyStrategyFactory keyStrategyFactory = () -> {
    Map<String, KeyStrategy> map = new HashMap<>();
    map.put(GraphStateKeys.WARNINGS, new AppendStrategy());   // 追加：并行节点累积告警
    map.put(GraphStateKeys.CUSTOM,   new CustomStrategy());   // 自定义合并规则
    replace(map, TASK_ID, RESUME_ID, JOB_DESCRIPTION, RESUME_TEXT,
               RADAR_DATA, OVERALL_SCORE, DIAGNOSIS_SUMMARY,
               NEEDS_HUMAN_REVIEW, APPROVED, HR_COMMENT, HR_ADJUSTED_SCORES, ...);
    return map;
};
```

三种策略：

- `AppendStrategy`：多节点往同一键累积，读取时是 `List<T>`（如 `warnings`）；
- `ReplaceStrategy`：后写覆盖先写，用于结果唯一的字段（评分、复核意见）；
- `CustomStrategy`（实现 `KeyStrategy` 接口）：自定义合并逻辑，如取最大值、按业务规则融合。

> ⚠️ **最容易被坑的一点**：业务字段必须在 `KeyStrategyFactory` 里注册。Checkpoint 在 `updateState / resume` 往返中**只保留注册过的键**，未注册字段会丢失，导致恢复后的节点取不到 `taskId`/`resumeId` 而失败。

节点侧只返回增量即可（`HumanReviewAction` 真实代码）：

```java
Map<String, Object> result = new HashMap<>();
result.put(GraphStateKeys.NEEDS_HUMAN_REVIEW, true);
result.put(GraphStateKeys.REVIEW_REASON,
        String.format("AI 综合评分 %.1f，等待 HR 复核确认", overall));
return result;   // 框架按策略合并进 OverAllState
```

### 4. Node：节点的三种写法

```java
// ① 同步节点：最常用的形态
NodeAction node = state -> {
    Map<String, Object> r = new HashMap<>();
    r.put("score", calc(state.data()));
    return r;
};

// ② 异步 / 需要 RunnableConfig（能拿到 threadId、checkPointId 等）
AsyncNodeActionWithConfig asyncNode = (state, config) ->
        CompletableFuture.supplyAsync(() -> Map.of("score", calc(state.data())));

// ③ 子图节点：把另一张图当成一个节点（用于流程拆分复用）
workflow.addNode("sub_flow", subStateGraph);
```

本项目把「计时、重试、事件上报、异常兜底」抽到抽象基类 `BaseTrackingAction`（模板方法），业务节点只实现 `doExecute()`，因此评分节点可以极简：

```java
@Component
public class SkillScoringAction extends AbstractScoringAction {
    @Override public String nodeId()        { return GraphNodes.SKILL; }
    @Override protected String dimensionKey()   { return "skill_match"; }
    @Override protected String dimensionLabel() { return "技能匹配度"; }
    @Override protected String reviewFocus()    { return "技能与岗位的匹配程度：技术栈、工具链、行业知识是否与岗位要求对齐"; }
}
```

### 5. Edge：普通边、并行边、条件边

```java
// ① 普通边：A 执行完必定走 B
.addEdge(GraphNodes.SYNTHESIS, GraphNodes.HUMAN_REVIEW)

// ② 并行 fan-out：同一节点多条出边 → 下游并行执行（也可 addEdge(source, List.of(...))）
.addEdge(GraphNodes.PARSE, GraphNodes.SKILL)
.addEdge(GraphNodes.PARSE, GraphNodes.PROJECT)
.addEdge(GraphNodes.PARSE, GraphNodes.CULTURE)

// ③ 并行 fan-in：同一节点被多条入边指向 → 形成屏障，全部上游完成后才执行
.addEdge(GraphNodes.SKILL,   GraphNodes.SYNTHESIS)
.addEdge(GraphNodes.PROJECT, GraphNodes.SYNTHESIS)
.addEdge(GraphNodes.CULTURE, GraphNodes.SYNTHESIS)

// ④ 条件边：按状态动态路由（示意，本项目用中断机制代替）
.addConditionalEdges(GraphNodes.SYNTHESIS,
        state -> (Double) state.data().get("overall_score") >= 3.0
                ? "pass" : "human_review",
        Map.of("pass", GraphNodes.GENERATE_REPORT,
               "human_review", GraphNodes.HUMAN_REVIEW))
```

`EdgeAction.apply(state)` 返回一个**字符串 key**，框架用第三个参数的 `Map` 把它翻译成真实节点名（也可以直接返回节点名）。

三个保留节点常量：`StateGraph.START`（`__START__`）、`StateGraph.END`（`__END__`）、`StateGraph.ERROR`。

### 6. 编译：StateGraph → CompiledGraph

定义好的 `StateGraph` 只是"图纸"，必须 `compile()` 才能运行。编译做三件事：校验拓扑合法性（是否存在不可达节点 / 缺失入口）、绑定状态合并策略、绑定 Checkpoint 存储。

```java
return workflow.compile(CompileConfig.builder()
        .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
        .build());
```

> **必须在编译期注入 `BaseCheckpointSaver`**，否则中断后没有 Checkpoint，HIL 无法恢复。
> **只编译一次**：`CompiledGraph` 是线程安全的执行器，在 Spring 中注册为单例 Bean 注入即可，不要每次请求重新编译。

### 7. 运行：invoke / stream 与 RunnableConfig

```java
// 同步：一次性跑完，返回最终状态
Optional<OverAllState> finalState = compiledGraph.invoke(inputs, config);

// 流式：返回 Flux<NodeOutput>，每个节点产出都会发出一次事件（本项目用这个推 SSE）
compiledGraph.stream(inputs, config)
        .doOnError(e -> markFailed(taskId, e.getMessage()))
        .doOnComplete(() -> markCompleted(taskId))
        .blockLast();   // Flux 是惰性的，必须订阅/阻塞才会真正执行
```

`RunnableConfig` 是每次运行的"运行时上下文"：

| 方法 | 说明 |
| --- | --- |
| `threadId(id)` | 线程（会话）ID，Checkpoint 按它隔离 —— 本项目用 `taskId` |
| `checkPointId(id)` | 从指定检查点开始跑 |
| `nextNode(id)` | 指定起始节点 |
| `resume()` | 清除中断标记，允许继续执行 |
| `streamMode(...)` | 流式输出模式 |

**框架的执行循环**（理解这个就理解了 Graph 的运行原理）：

```
1. 用 inputs 初始化 OverAllState（按 KeyStrategy 合并）
2. 取当前待执行节点（初始为 START 指向的节点）
3. 若节点实现了 InterruptableAction：
   - interrupt() 返回非空 → 立即中断，保存 Checkpoint，退出
4. 执行节点 apply() → 得到增量 Map
5. 按 KeyStrategy 合并进 OverAllState
6. 保存 Checkpoint（含 nodeId / nextNodeId / state）
7. 若 interruptAfter() 返回非空 → 中断，退出
8. 按边计算下一批节点（多出边=并行，条件边=路由）
9. 回到第 2 步，直到 END 或中断
```

项目中的真实运行代码：

```java
private void runGraph(String taskId, Map<String, Object> initialState) {
    // threadId = taskId：Checkpoint 按任务隔离，供中断后恢复定位
    RunnableConfig config = RunnableConfig.builder().threadId(taskId).build();
    resumeReviewCompiledGraph.stream(initialState, config)
            .doOnError(e -> markFailed(taskId, e.getMessage()))
            .doOnComplete(() -> {
                // 中断时 stream 同样会 complete，故以 DB 状态区分
                ReviewTask task = taskMapper.selectById(taskId);
                if (task != null && "NEEDS_REVIEW".equals(task.getStatus())) {
                    return;      // 已挂起，等待 HR 恢复
                }
                markCompleted(taskId);
            })
            .blockLast();
}
```

### 8. Checkpoint：可回溯的执行存档

每执行完一个节点，框架通过 `BaseCheckpointSaver` 存一份快照（含 `id` / `nodeId` / `nextNodeId` / `state`）。有了它才能做三件事：

```java
StateSnapshot snapshot = compiledGraph.getState(config);              // 当前状态
Collection<StateSnapshot> history = compiledGraph.getStateHistory(config);  // 全部历史快照
Collection<Checkpoint> cps = checkpointSaver.list(config);            // 直接读存储层
```

本项目提供两种实现，一行配置切换：

| `resume.checkpoint-store` | 实现 | 特点 |
| --- | --- | --- |
| `redis`（默认） | `RedisSaver`（依赖 Redisson） | 读写快、TTL 自动过期（默认 72h） |
| `mysql` | `MysqlSaver` | 落 `GRAPH_THREAD` / `GRAPH_CHECKPOINT`，首次启动自动建表，无 TTL |

### 9. Human-in-the-Loop：中断与恢复

这是 Graph 最有价值的特性：**让流程在指定节点真正停下来，等人类决策后再从断点继续**。

**① 声明中断点**（节点实现 `InterruptableAction`）：

```java
public class HumanReviewAction extends BaseTrackingAction implements InterruptableAction {

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        return Optional.empty();     // 执行「前」不中断：要先算分并把数据推给前端
    }

    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeId, OverAllState state,
                                                        Map<String, Object> result, RunnableConfig config) {
        // 执行「后」无条件中断 → Graph 在此挂起并把 State 写入 Checkpoint
        return Optional.of(InterruptionMetadata.builder(nodeId, state).build());
    }
}
```

> `interrupt()`：执行前中断（还没算就停，适合"前置审批"）；
> `interruptAfter()`：执行后中断（算完了把结果给人看，再决定要不要继续）——本项目用这个。
> 也可以不用代码：编译期配置 `interruptBefore("node_id")` 静态指定，但那样**所有任务都会无条件中断**，不如代码里按状态判断灵活。

**② 恢复执行**（HR 确认后）：

```java
// 1) threadId 必须与发起时一致；resume() 清除中断标记，缺了不会继续往下走
RunnableConfig config = RunnableConfig.builder().threadId(taskId).resume().build();

// 2) 以 Checkpoint 为基底 + HR 调整 + DB 权威字段，构造恢复状态
Map<String, Object> resumeState = buildResumeState(taskId, config, adjustedScores, reviewComment);

// 3) 写回状态，返回携带新 checkpointId 的配置
RunnableConfig resumeConfig = compiledGraph.updateState(config, resumeState, GraphNodes.HUMAN_REVIEW);

// 4) 从中断处继续（inputs 传完整状态，为未注册字段兜底）
graphExecutor.execute(() -> resumeGraph(taskId, resumeConfig, resumeState));
```

`updateState(config, values, nodeId)` 的第三个参数是"以哪个节点的身份写入"，写入后生成新的 Checkpoint，配合 `resume()` 即可从该节点的**下一个节点**继续，**已执行过的节点不会重跑**。

**③ 回溯历史 Checkpoint（重新复核）**：

任务已完成时最新 Checkpoint 已经是 `generate_report`，不能直接 resume。做法是从历史里挑出 `human_review` 那一条：

```java
Collection<Checkpoint> checkpoints = checkpointSaver.list(RunnableConfig.builder().threadId(taskId).build());
Checkpoint target = checkpoints.stream()
        .filter(cp -> GraphNodes.HUMAN_REVIEW.equals(cp.getNodeId()))
        .findFirst()
        .orElseThrow(() -> new BizException("人工复核检查点已过期，无法重新复核"));

RunnableConfig config = RunnableConfig.builder()
        .threadId(taskId)
        .checkPointId(target.getId())   // 指定从哪个检查点续跑
        .resume()
        .build();
```

### 10. 一次完整执行的时序（本项目）

```
① POST /api/review/start
   → 建 ReviewTask（RUNNING）→ threadId=taskId → 异步 stream(initialState)
② parse_resume        解析简历（PDFBox 抽文本 + LLM 结构化）   → Checkpoint#1
③ skill/project/culture 三个评分节点并行跑 LLM                 → Checkpoint#2/#3/#4
④ synthesis           屏障汇合，算出六维雷达图 + 诊断摘要        → Checkpoint#5
⑤ human_review        执行完 → interruptAfter 中断              → Checkpoint#6 ★挂起
   ├─ 任务状态置 NEEDS_REVIEW，SSE 推 pending，前端弹出 HR 复核框
⑥ HR 调整评分 → POST /api/review/approve
   ├─ CAS 抢占状态（status: NEEDS_REVIEW → RUNNING），防重复提交
   ├─ updateState 写入 approved=true + 调整后评分
   └─ resume → 从 human_review 之后继续
⑦ generate_report     LLM 流式生成报告（SSE 打字机）            → Checkpoint#7
⑧ END                 任务置 COMPLETED，报告落库
```

### 11. 踩坑清单

1. **业务字段必须在 `KeyStrategyFactory` 注册**，否则 `updateState / resume` 会丢失该键。
2. 恢复时 `stream(inputs, config)` 的 `inputs` 要传**完整状态**，为未注册字段兜底；关键字段以 DB 为权威来源覆盖写入。
3. `resume()` 不能漏：没有它 Graph 不会继续往下执行。
4. `Flux` 是惰性的：`stream()` 之后必须 `blockLast()` / 订阅，否则不会执行。
5. 不要每次请求都 `compile()`，`CompiledGraph` 是 Spring 单例 Bean。
6. 不要 `releaseThread()`，交给 TTL / 存储过期回收更可靠。
7. 中断时 `stream()` 也会触发 `doOnComplete`，完成回调里要用 DB 状态区分「真完成」和「挂起」。
8. 并行节点写同一键必须有 `AppendStrategy` 之类的合并策略，否则互相覆盖。

---

## 快速启动

### 前置条件

- JDK 17+、Maven 3.8+、Node.js 18+
- Docker（启动 MySQL / Redis）
- 阿里云 DashScope API Key（**必填**）

### 1. 启动中间件

```bash
# MySQL 映射到宿主机 3307，Redis 映射到 6379
docker compose up -d mysql redis
```

> 首次启动自动执行 `schema.sql` 建业务表；Graph 的 `GRAPH_THREAD` / `GRAPH_CHECKPOINT` 由 `MysqlSaver` 在应用启动时自动建。
> 不需要 Milvus 向量检索时只起 `mysql redis` 即可。

### 2. 配置环境变量

```bash
cp .env.example .env
```

最小必填：

```properties
MYSQL_URL=jdbc:mysql://localhost:3307/resume_review?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
MYSQL_USERNAME=root
MYSQL_PASSWORD=your-password
DASHSCOPE_API_KEY=sk-your-dashscope-api-key
```

### 3. 启动后端

```bash
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`，演示账号 `admin / admin123`。

---

## 环境变量配置

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_URL` | MySQL JDBC 连接串（compose 用 `localhost:3307`） | `jdbc:mysql://localhost:3306/resume_review?...` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号密码（必填） | 空 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 地址 / 端口 / 密码 | `localhost` / `6379` / 空 |
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key（**必填**） | 空 |
| `VECTORSTORE_TYPE` | `milvus` / `none`（`none` 关闭向量库） | `milvus` |
| `MILVUS_HOST` | Milvus 地址 | `127.0.0.1` |
| `LANGFUSE_OTLP_ENDPOINT` | Langfuse OTLP 端点（须含 `/api/public/otel`） | `https://us.cloud.langfuse.com/api/public/otel/v1/traces` |
| `LANGFUSE_AUTH_BASIC` | Basic Auth：`Basic base64(pk:sk)` | 空 |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | 演示账号 | `admin` / `admin123` |

非敏感开关在 `application.yml` 的 `resume.*` 下：`checkpoint-store`、`checkpoint-ttl-hours`、`llm-retry-times`、`llm-retry-delay-ms`、`upload-dir`。

---

## 常见问题

- **LLM 节点全部失败**：确认 `DASHSCOPE_API_KEY` 已配置，本项目已移除演示模式，无 Key 会直接报错。
- **MySQL 连接不上**：compose 把 MySQL 映射到宿主机 `3307`，`.env` 里的 `MYSQL_URL` 要同步使用 `3307`。
- **`人工复核检查点已过期`**：Checkpoint 被清理或 TTL 过期，只能重新发起审查。
- **重新复核后没反应**：确认 `checkpoint-store` 与发起时一致；Redis 和 MySQL 的 Checkpoint 不互通。
- **进度条不更新**：前端需监听自定义事件名 `topology` / `node` / `task` / `stream`（不是 `onmessage`）。
- **Milvus 不想启动**：`.env` 中设置 `VECTORSTORE_TYPE=none`。
- **Langfuse 404**：OTLP 端点必须含 `/api/public/otel`，认证用 Basic Auth `base64(pk:sk)`。

---

## 界面预览

<div align="center">

**发起审查 · 流程追踪面板 · 六维雷达图**

![image-20260821223521448](README.assets/image-20260821223521448.png)

**流式报告与评分详情**

![image-20260821223538235](README.assets/image-20260821223538235.png)

**历史报告管理（分页 / 搜索 / 导出 / 重新审查）**

![image-20260821223908796](README.assets/image-20260821223908796.png)

</div>
