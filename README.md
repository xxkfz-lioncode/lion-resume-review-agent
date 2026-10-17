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
  <a href="#graph-实战教程">Graph 实战</a> ·
  <a href="#快速启动">快速启动</a> ·
  <a href="#环境变量配置">环境变量</a> ·
  <a href="#常见问题">常见问题</a>
</p>

</div>

---

基于 **Spring AI Alibaba Graph** 构建的 AI 简历审查系统。用 7 节点状态机编排「简历解析 → 技能 / 项目 / 文化并行评分 → 综合诊断 → 人工复核 → 生成报告」全链路，人工复核节点通过官方 `InterruptableAction` 真正挂起并落盘 Checkpoint，HR 确认后从断点续跑；全流程状态通过 Redis Pub/Sub + SSE 实时推送到前端；每个节点的耗时 / 状态经 `GraphLifecycleListener` 扩展点自动落库，可在「日志查询」页面按甘特时间轴复盘整条流程的耗时。

---

## 核心功能

### 1. AI 简历审查工作流（Graph 编排）

- 7 节点状态机：`parse_resume → [skill_scoring | project_scoring | culture_scoring] → synthesis →（条件边）→ human_review? → generate_report`。
  其中 `human_review` 是**可选分支**：评分达标的简历直接跳到 `generate_report`。
- **并行执行**：3 个评分节点 fan-out 同时跑，synthesis 节点 fan-in 等待全部完成后进入。
- 前端 Vue Flow 流程追踪面板实时高亮节点状态，ECharts 六维雷达图动态刷新，报告通过 SSE 打字机流式渲染。
- 后端异步执行 Graph，`startReview` 立即返回 `taskId`，HTTP 不被长流程阻塞。

### 2. Human-in-the-Loop 人工复核 + 条件边自动分流

- `synthesis` 后是**条件边**：综合评分达标且无维度短板时跳过 `human_review` 直接出报告，只有边缘候选才进人工复核（阈值见 `resume.auto-approve-score` / `review-dimension-floor`）。
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

### 5. 节点执行日志与耗时分析

- 每个节点的耗时、状态、错误信息由 `GraphLifecycleListener` 扩展点**自动落库**到 `t_graph_node_log`，业务节点零侵入。
- 前端独立「日志查询」页面：甘特式时间轴横向展示每个节点的**开始时刻 + 耗时**，一眼看出三个评分节点的并行关系与瓶颈节点。
- 支持按任务 / 状态 / 节点筛选，可一键隐藏框架内部路由节点（如 `xxx__(parse_resume)`）。

---

## 项目亮点

- **Spring AI Alibaba Graph 状态机工作流**：使用 `StateGraph`、`CompiledGraph`、`RunnableConfig`、`Checkpoint` 实现真正的状态机编排，不是手写 if/else 流程控制。
- **官方 Human-in-the-Loop 中断恢复**：`InterruptableAction#interruptAfter` 返回 `InterruptionMetadata` → Checkpoint 落盘 → `updateState + resume` 续跑，已执行节点**不重跑**。
- **Checkpoint 回溯能力**：`reapprove` 通过 `checkpointSaver.list(threadId)` 过滤 `human_review` 检查点，支持已完成任务的重新复核。
- **并行 fan-out / fan-in**：`parse_resume` 三条出边触发并行评分，三个节点汇聚到 `synthesis` 形成屏障，显著降低耗时。
- **声明式状态合并策略**：`KeyStrategyFactory` 配置 `AppendStrategy`（warnings 累积）/ `ReplaceStrategy`（评分覆盖）/ `CustomStrategy` 自定义规则。
- **条件边自动分流**：`synthesis` 后的 `ReviewRouter` 按「综合分 + 最低维度」双阈值判定，达标简历跳过人工复核直接出报告，HR 只需关注边缘候选；判定与阈值解耦，改规则不动代码。
- **零侵入节点可观测**：基于框架 `GraphLifecycleListener` 自建监听器链（桥接器 + `NodeExecutionListener`），计时 / 落库 / 推送按 `@Order` 编排，新增能力只写一个实现类，业务节点完全无感知。
- **异步隔离的日志落库**：`NodeLogPersistListener` 走独立线程池（与 Graph 线程池隔离）且自带 try-catch，写库失败只告警、绝不阻断审查主流程。
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
| `NodeAction` | 同步节点：`Map<String,Object> apply(OverAllState)` | 框架基础形态，本项目未直接使用 |
| `AsyncNodeActionWithConfig` | 异步节点 + 运行配置：`CompletableFuture<Map<String,Object>> apply(OverAllState, RunnableConfig)` | `BaseTrackingAction` 实现的接口，本项目所有业务节点都用它 |
| `AsyncEdgeAction` | 异步条件边路由：`edge_async(state -> "label")` | `reviewRouter::route`，返回 `review` / `auto`，由映射表翻译成目标节点 |
| `KeyStrategyFactory` | 声明每个状态键的合并策略 | `GraphConfig` |
| `CompileConfig` + `SaverConfig` | 编译期配置（注入 Checkpoint 存储） | 注入 `BaseCheckpointSaver` |
| `CompiledGraph` | 编译产物：`invoke` / `stream` / `getState` / `updateState` | Spring 单例 Bean |
| `RunnableConfig` | 运行时配置：`threadId` / `checkPointId` / `nextNode` / `resume()` | `threadId = taskId` |
| `BaseCheckpointSaver` | Checkpoint 持久化抽象 | `RedisSaver` / `MysqlSaver` |
| `InterruptableAction` | 节点级中断：`interrupt()` / `interruptAfter()` | `HumanReviewAction` |
| `GraphLifecycleListener` | 节点生命周期回调：`onStart` / `before` / `after` / `onError` / `onComplete` | `GraphNodeLifecycleBridge` 桥接到自研监听器链 |

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

本项目把「计时、重试、事件上报、异常兜底」抽到抽象基类 `BaseTrackingAction`（模板方法），业务节点只实现 `doExecute(state, config)`，因此评分节点可以极简：

```java
@Component
public class SkillScoringAction extends AbstractScoringAction {
    @Override public String nodeId()        { return GraphNodes.SKILL; }
    @Override protected String dimensionKey()   { return "skill_match"; }
    @Override protected String dimensionLabel() { return "技能匹配度"; }
    @Override protected String reviewFocus()    { return "技能与岗位的匹配程度：技术栈、工具链、行业知识是否与岗位要求对齐"; }
}
```

**为什么基类选 `AsyncNodeActionWithConfig` 而不是 `AsyncNodeAction`**

每个节点都要拿 `taskId` 去更新任务进度、推送 SSE。如果一律从 `OverAllState` 读，就必须保证 `task_id` 在 `KeyStrategyFactory` 注册过，否则 Checkpoint 往返后会丢失（见踩坑清单）。
而发起执行时显式指定了 `threadId = taskId`，框架执行时会把 `RunnableConfig` 透传给节点，所以**身份类字段直接从配置里取最可靠**：

```java
// BaseTrackingAction
@Override
public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
    String taskId = resolveTaskId(state, config);   // 优先 config.threadId()
    String safeTaskId = (taskId == null || taskId.isBlank()) ? "unknown" : taskId;
    ...
    Map<String, Object> result = doExecute(state, config);   // 业务节点同样能拿到 config
}

protected String resolveTaskId(OverAllState state, RunnableConfig config) {
    if (config != null && config.threadId() != null) {
        String tid = config.threadId().orElse(null);
        // threadId 与 taskId 同源，由框架沿调用链传递，不依赖 State 往返
        if (tid != null && !tid.isBlank()) return tid;
    }
    if (state != null && state.data() != null) {         // 兼容手工调用 / 单元测试
        Object v = state.data().get(GraphStateKeys.TASK_ID);
        if (v != null) return String.valueOf(v);
    }
    // 不用 String.valueOf(...)：null 会变成字符串 "null"，导致按 id 查询全部落空
    return null;
}
```

> **分工**：身份/运行类字段（`taskId` / `threadId` / 中断标记）走 `RunnableConfig`；业务数据（简历原文、评分、岗位描述）仍走 `OverAllState`。

### 5. Edge：普通边、并行边、条件边

```java
// ① 普通边：A 执行完必定走 B
.addEdge(GraphNodes.HUMAN_REVIEW, GraphNodes.GENERATE_REPORT)

// ② 并行 fan-out：同一节点多条出边 → 下游并行执行（也可 addEdge(source, List.of(...))）
.addEdge(GraphNodes.PARSE, GraphNodes.SKILL)
.addEdge(GraphNodes.PARSE, GraphNodes.PROJECT)
.addEdge(GraphNodes.PARSE, GraphNodes.CULTURE)

// ③ 并行 fan-in：同一节点被多条入边指向 → 形成屏障，全部上游完成后才执行
.addEdge(GraphNodes.SKILL,   GraphNodes.SYNTHESIS)
.addEdge(GraphNodes.PROJECT, GraphNodes.SYNTHESIS)
.addEdge(GraphNodes.CULTURE, GraphNodes.SYNTHESIS)

// ④ 条件边：synthesis 后按评分结果动态路由（本项目真实用法）
.addConditionalEdges(GraphNodes.SYNTHESIS,
        AsyncEdgeAction.edge_async(reviewRouter::route), Map.of(
                ReviewRouter.TO_REVIEW, GraphNodes.HUMAN_REVIEW,
                ReviewRouter.TO_REPORT, GraphNodes.GENERATE_REPORT))
```

`AsyncEdgeAction.edge_async(state -> "label")` 把 `OverAllState -> String` 函数包装成异步条件边，返回的字符串 key 由第三个参数的 `Map` 翻译成真实节点名。

三个保留节点常量：`StateGraph.START`（`__START__`）、`StateGraph.END`（`__END__`）、`StateGraph.ERROR`。

#### 条件边实战：低风险简历跳过人工复核

**问题**：人工复核原本在主链上（`synthesis → human_review → generate_report`），每份简历都必须 HR 点一下才出报告。但高分候选人根本不需要复核，纯属浪费人力。

**解法**：把 `synthesis → human_review` 的固定边换成**条件边**，达标简历直接跳到 `generate_report`。

判定逻辑放在**节点**里（`SynthesisAction` 按阈值算出布尔量写入 State），边只做「翻译」：

```java
// SynthesisAction：算分数、定阈值、写判定结果
double weakest = Math.min(skill, Math.min(project, Math.min(culture,
        Math.min(education, Math.min(communication, growth)))));
boolean needsHumanReview =
        overall < props.getAutoApproveScore() || weakest < props.getReviewDimensionFloor();

result.put(GraphStateKeys.NEEDS_HUMAN_REVIEW, needsHumanReview);
result.put(GraphStateKeys.REVIEW_REASON, needsHumanReview
        ? String.format("AI 综合评分 %.1f（最低维度 %.1f），需 HR 复核确认", overall, weakest)
        : String.format("AI 综合评分 %.1f，最低维度 %.1f，达标免人工复核", overall, weakest));
```

```java
// ReviewRouter：同步条件边，不读分数、不设阈值，只翻译判定结果
@Component
public class ReviewRouter implements EdgeAction {
    public static final String TO_REVIEW = "review";
    public static final String TO_REPORT = "auto";

    @Override
    public String apply(OverAllState state) {
        boolean needsReview = Boolean.TRUE.equals(state.data().get(GraphStateKeys.NEEDS_HUMAN_REVIEW));
        return needsReview ? TO_REVIEW : TO_REPORT;
    }
}
```

阈值走配置，改规则不用改代码：

```yaml
resume:
  # 综合评分 ≥ 该值且无维度短板 → 跳过人工复核（设为 5.01 表示始终复核）
  auto-approve-score: 4.0
  # 任一维度低于该值即视为短板，即便综合分达标也必须复核
  review-dimension-floor: 3.0
```

**分工**：「要不要复核」由条件边在**拓扑层**决定，「复核了没有」由 `interruptAfter` 在**节点层**保证。两者正交，所以 `HumanReviewAction` 内部仍然可以无条件挂起 —— 能被走到就说明确实需要 HR 确认。

**配套改动**：

| 位置 | 改动 |
| --- | --- |
| `GraphNodes.edges()` | 补上 `SYNTHESIS → GENERATE_REPORT`，前端拓扑才画得出这条分支 |
| `FlowPanel.vue` | `human_review` 右移给分支让位；任务完成但未执行的节点显示「已跳过」 |
| `ReviewTaskService#reapprove` | 免复核任务没有 `human_review` 检查点，报错文案与「已过期」区分开 |

> **踩坑**：`apply()` 返回的字符串**必须**命中映射表的 key，否则运行时报
> `cannot find edge mapping for id: 'xxx'`；映射目标节点不存在则在**编译期**就失败（`GraphStateException`）。
> 另外判定用的状态键（这里是 `needs_human_review`）必须在 `KeyStrategyFactory` 注册，否则中断恢复往返后会丢失。

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
   └─ 条件边 ReviewRouter：needs_human_review ? ⑤ : ⑦（免复核直接出报告）
⑤ human_review        [需复核分支] 执行完 → interruptAfter 中断   → Checkpoint#6 ★挂起
   ├─ 任务状态置 NEEDS_REVIEW，SSE 推 pending，前端弹出 HR 复核框
⑥ HR 调整评分 → POST /api/review/approve
   ├─ CAS 抢占状态（status: NEEDS_REVIEW → RUNNING），防重复提交
   ├─ updateState 写入 approved=true + 调整后评分
   └─ resume → 从 human_review 之后继续
⑦ generate_report     LLM 流式生成报告（SSE 打字机）            → Checkpoint#7
⑧ END                 任务置 COMPLETED，报告落库
```

### 11. 节点横切逻辑：GraphLifecycleListener 扩展

**为什么需要它**：节点的「开始 / 结束 / 异常」时机只有框架自己知道。如果在节点里手写 `try-finally` 记耗时、写日志、推状态，7 个节点会各写一遍，还和业务逻辑缠在一起。`GraphLifecycleListener` 就是官方留给你的横切扩展点。

**一句话概括**：框架回调（`before` / `after` / `onError`）→ 桥接器翻译成自研上下文 → 按 `@Order` 分发给一堆监听器。

| 组成 | 是什么 | 本项目实现 |
| --- | --- | --- |
| `GraphLifecycleListener` | 框架回调接口：`onStart` / `before` / `after` / `onError` / `onComplete` | `GraphNodeLifecycleBridge`（全局唯一对接点） |
| `NodeExecutionContext` | 自研上下文：承载 taskId / userId / threadId / nodeId / 耗时 / 状态 / 错误 | `NodeExecutionContext` |
| `NodeExecutionListener` | 自研监听器接口：`before` / `after` / `onError`，继承 `Ordered` | 计时 / 落库 / 推送三个实现 |
| `@Order` | 监听器执行顺序 | 计时(0) → 落库(100) → 推送(200) |

> 只让**一个类**去实现框架接口，框架 API 变动时只改这个类，业务监听器一个都不用动 —— 这就是这层设计的价值。

**① 桥接器：框架回调 → 自研上下文**

每个节点 `before` 时创建上下文并记起始时间戳，`after` / `onError` 时算耗时、移除上下文，再分发给监听器：

```java
@Override
public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long time) {
    String taskId = String.valueOf(state.getOrDefault(GraphStateKeys.TASK_ID, "unknown"));
    String userId = String.valueOf(state.getOrDefault(GraphStateKeys.USER_ID, "system"));
    NodeExecutionContext ctx = new NodeExecutionContext(
            taskId, userId, config.threadId().orElse("unknown"), nodeId, state,
            config.isInterrupted(nodeId));
    ctx.markStart(System.nanoTime());
    running.put(key(config, nodeId), ctx);      // threadId + nodeId 隔离
    dispatch(l -> l.before(ctx));
}

@Override
public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long time) {
    NodeExecutionContext ctx = running.remove(key(config, nodeId));
    if (ctx == null) return;
    ctx.finish(NodeStatus.COMPLETED, System.nanoTime());
    dispatch(l -> l.after(ctx));
}
```

耗时用 `System.nanoTime()` 自管计时（不受系统时钟回拨影响），在 `NodeExecutionContext.finish()` 里换算成毫秒。

> **关键时序**：`ctx.finish()`（算耗时）在 `dispatch()` **之前**执行，所以所有监听器拿到的 `ctx.getDurationMs()` 都已经是终值，**与 `@Order` 无关**。监听器里不要重复计时，直接读即可。

**② 挂载到编译期**

```java
return workflow.compile(CompileConfig.builder()
        .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
        .withLifecycleListener(lifecycleBridge)   // ← 生命周期回调入口
        .build());
```

> 和 `BaseCheckpointSaver` 一样，必须在**编译期**注入，不是运行时参数。

**③ 监听器接口：横切能力的唯一扩展点**

```java
public interface NodeExecutionListener extends Ordered {
    default void before(NodeExecutionContext ctx) {}
    default void after(NodeExecutionContext ctx) {}
    default void onError(NodeExecutionContext ctx) {}
}
```

三条约定（写新监听器时必须遵守）：

1. **用 `@Order` 编排顺序**：Spring 注入 `List<NodeExecutionListener>` 时已按 `@Order` 升序排好，桥接器依次调用；计时监听器排最前(0)是为了让耗时日志最先打印，**不是为了"算出"耗时**。
2. **内部自己 try-catch**：单个监听器异常不得影响主流程（桥接器 `dispatch` 也做了二次兜底）。
3. **`ctx` 可能为 `null`**：异常发生在 `before` 之前时上下文还没建，必须判空。

**④ 三个内置监听器**

| `@Order` | 实现类 | 职责 | 开关 |
| --- | --- | --- | --- |
| 0 | `NodeTimingListener` | 打印节点级耗时日志（耗时由桥接器 `finish()` 计算，此处只读） | 常开 |
| 100 | `NodeLogPersistListener` | 耗时 / 状态 / 错误写入 `t_graph_node_log` | `resume.node-listeners.node-log` |
| 200 | `ProgressPushListener` | 节点开始前推 `running`、异常推 `error`（SSE） | `resume.node-listeners.progress-push` |

开关用 `@ConditionalOnProperty`，在 `application.yml` 里关掉即不注册 Bean，无需改代码：

```yaml
resume:
  node-listeners:
    progress-push: true
    node-log: true
```

**⑤ 落库监听器（耗时数据从哪来）**

```java
@Override
public void after(NodeExecutionContext ctx)   { persist(ctx); }
@Override
public void onError(NodeExecutionContext ctx) { persist(ctx); }

private void persist(NodeExecutionContext ctx) {
    if (ctx == null) return;
    nodeLogExecutor.execute(() -> {          // 独立线程池，与 Graph 线程池隔离
        try {
            NodeLog logEntity = new NodeLog();
            logEntity.setId(UUID.randomUUID().toString().replace("-", ""));
            logEntity.setTaskId(ctx.getTaskId());
            logEntity.setUserId(ctx.getUserId());
            logEntity.setNodeId(ctx.getNodeId());
            logEntity.setStatus(ctx.getStatus() == null ? null : ctx.getStatus().getValue());
            logEntity.setDurationMs(ctx.getDurationMs());
            logEntity.setErrorMsg(ctx.getErrorMessage());
            // 结束时间即当前时刻，开始时间由耗时反推
            logEntity.setStartTime(LocalDateTime.now().minusNanos(
                    ctx.getDurationMs() == null ? 0 : ctx.getDurationMs() * 1_000_000L));
            logEntity.setEndTime(LocalDateTime.now());
            nodeLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("节点执行日志落库失败 taskId={} node={}", ctx.getTaskId(), ctx.getNodeId(), e);
        }
    });
}
```

`userId` 取自 `OverAllState` 的 `USER_ID` 键，它由 `startReview` 在**请求线程**里从 Sa-Token `StpUtil.getLoginId()` 取出后写进 State —— Graph 跑在异步线程池里，没有 HTTP 请求，那里读不到登录态。

**⑥ 新增一个监听器有多简单**

只写一个实现类并注册为 Bean，业务节点零改动（示意）：

```java
@Component
@Order(300)                       // 排在落库之后
public class SlowNodeAlarmListener implements NodeExecutionListener {
    @Override
    public void after(NodeExecutionContext ctx) {
        if (ctx.getDurationMs() != null && ctx.getDurationMs() > 5000) {
            // 慢节点告警：可接钉钉 / Prometheus，异常自行吞掉不影响主流程
        }
    }
}
```

**⑦ 并行节点的线程安全**

三个评分节点并发执行，同一时刻会有多个上下文同时存在。桥接器用 `ConcurrentHashMap` 以 `threadId + nodeId` 为 key 隔离，并且**每次 `after` / `onError` 必须 `remove`**，否则中断场景下上下文会堆积。

**⑧ 数据落到哪里、怎么看**

`t_graph_node_log` 每行 = 一次节点执行：`task_id` / `user_id` / `thread_id` / `node_id` / `attempt` / `status` / `start_time` / `end_time` / `duration_ms` / `error_msg`。
前端「日志查询」页面按任务读取，用 ECharts 堆叠柱状图渲染成**甘特式时间轴**：横条起点 = 相对流程开始的时刻，长度 = 耗时，颜色区分成功 / 失败 / 挂起。

> 表里会出现 `xxx__(parse_resume)` 这类**框架内部路由节点**（条件分支、并行聚合、中断检查点生成），不是业务节点；统计节点数时可用「隐藏框架内部节点」开关过滤。

### 12. 踩坑清单

1. **业务字段必须在 `KeyStrategyFactory` 注册**，否则 `updateState / resume` 会丢失该键。
2. 恢复时 `stream(inputs, config)` 的 `inputs` 要传**完整状态**，为未注册字段兜底；关键字段以 DB 为权威来源覆盖写入。
3. `resume()` 不能漏：没有它 Graph 不会继续往下执行。
4. `Flux` 是惰性的：`stream()` 之后必须 `blockLast()` / 订阅，否则不会执行。
5. 不要每次请求都 `compile()`，`CompiledGraph` 是 Spring 单例 Bean。
6. 不要 `releaseThread()`，交给 TTL / 存储过期回收更可靠。
7. 中断时 `stream()` 也会触发 `doOnComplete`，完成回调里要用 DB 状态区分「真完成」和「挂起」。
8. 并行节点写同一键必须有 `AppendStrategy` 之类的合并策略，否则互相覆盖。
9. 生命周期回调里**不要做重活**：`after` 是在节点执行线程上同步调用的，写库 / 推送要丢到独立线程池，否则拖慢每个节点。
10. 日志里出现 `xxx__(节点名)` 是框架内部路由节点，不是业务节点，统计节点数时需过滤。
11. 条件边 `apply()` 的返回值**必须**命中 `addConditionalEdges` 的映射表，否则运行时报 `cannot find edge mapping for id`；映射目标节点不存在则编译期就失败。判定所依赖的状态键（如 `needs_human_review`）也要在 `KeyStrategyFactory` 注册，否则中断恢复往返后丢失，路由会走错分支。

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
- **报 `Unknown column 'user_id' in 'field list'`**：老库未做迁移。需对 `t_review_task` 补列并建 `t_graph_node_log` 表（脚本见 `src/main/resources/schema.sql`）。
- **日志查询页是空的**：确认 `resume.node-listeners.node-log=true`（默认开启）且 `t_graph_node_log` 已建表；日志是节点**执行完**才落库的，运行中查不到。
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
