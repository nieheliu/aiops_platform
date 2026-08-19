# AIOps 智能运维与工单服务平台

面向云环境的智能运维平台，打通「**告警接入 → 自动建单 → AI 自动诊断（RAG 增强）→ 知识沉淀 → 检索复用**」全流程闭环，降低人工排障成本。支持多模型接入（云端 API + 本地私有化模型），并基于检索增强生成（RAG）让大模型结合项目历史经验回答问题。

## 架构总览

```
监控系统(Prometheus风格)
        │  POST /api/alerts/webhook
        ▼
┌────────────────────┐      RabbitMQ       ┌─────────────────────┐
│  AlertWebhook      │ ───────────────────▶│  告警消费者          │
│  (告警解析/落库)    │   (手动ACK+死信队列)  │  (建工单+触发AI诊断)  │
└────────────────────┘                     └──────────┬──────────┘
                                                      ▼
                                          ┌─────────────────────┐
                                          │  AI 诊断工作流        │
                                          │  ┌─────────────────┐ │
                                          │  │ RAG 检索器       │ │
                                          │  │ BM25+向量kNN     │ │
                                          │  │ +RRF融合         │ │
                                          │  └────────┬────────┘ │
                                          │           ▼          │
                                          │  大模型诊断报告       │
                                          │  (云端/本地Ollama)    │
                                          └──────────┬──────────┘
                                                     ▼
                                        MySQL + Elasticsearch
                                        (知识双写, 向量存储)
```

## 核心技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3、MyBatis-Plus |
| 中间件 | MySQL、RabbitMQ、Redis、Elasticsearch 8.15 |
| AI | Spring AI、多模型接入（DeepSeek / Qwen / MiniMax / 本地 Ollama）、RAG（BM25 + 向量 kNN + RRF 融合） |
| 安全 | JWT（HS256）、BCrypt、RBAC 角色权限 |
| 部署 | Docker 多阶段构建、docker-compose |

## 核心功能

1. **告警接入**：兼容 Prometheus Alertmanager 规范的 Webhook 接口，批量解析告警标签、严重级映射并落库。
2. **异步解耦与可靠投递**：告警落库后通过 RabbitMQ 异步通知建单；消费端手动 ACK + `prefetch=1`，配置死信队列（DLX/DLQ）保证「告警→建单」不丢失；按 alertId 查重防重复建单。
3. **AI 自动诊断（RAG 增强）**：
   - 基于 Spring AI 封装多模型接入（云端 API / 本地 Ollama `qwen2.5:7b` 可配置切换）；
   - 诊断前从知识库检索 topK 相似历史案例注入 Prompt（BM25 词法召回 + 向量语义召回 + RRF 融合），让模型结合项目实际经验回答；
   - 对模型输出做 **JSON 解析 + 正则提取双通道容错**（含嵌套 JSON 二次解析）；
   - 本地锁 + 二次查库防止并发重复调用模型。
4. **知识沉淀与检索**：工单解决 / 诊断报告自动生成知识条目，MySQL + Elasticsearch 双写；ES 多字段加权全文检索、命中高亮、分面统计（来源 / 组件 / 模型 / 生命周期）。
5. **工单生命周期**：待处理→认领/分配→已解决→关闭状态机；非管理员仅当前处理人可操作，全操作留痕。
6. **运维看板**：今日告警 / 待办工单 / 诊断量 / 知识总量 + 状态与严重级分布 + 近 7 日趋势；Redis 缓存（1min TTL）+ 数据变更主动失效。

## 目录结构

```
src/main/java/com/ops/ai/platform/
├── controller/     # Web 层：告警/工单/诊断/知识/看板/认证
├── service/        # 业务层接口 + 实现（含 AI 诊断工作流、RAG 检索、Embedding）
├── mq/             # RabbitMQ 生产者/消费者
├── security/       # JWT 认证拦截器、Token 提供
├── config/         # 中间件/模型/RAG 配置类
├── entity/         # MyBatis-Plus 实体
├── mapper/         # MyBatis 映射（含看板聚合 SQL）
├── es/document/    # Elasticsearch 文档映射（含 dense_vector）
├── dto/            # 请求/响应对象
└── common/         # 常量、异常处理、工具类
```

## 快速开始

### 前置依赖

- **中间件**（MySQL / RabbitMQ / Redis / Elasticsearch 8.x）—— 可自行部署或使用已有实例
- **JDK 17+、Maven 3.8+**
- **（可选，本地模型）** Ollama + `qwen2.5:7b-instruct` + `nomic-embed-text`

### 1. 启动中间件

项目默认连接 `192.168.88.136`（MySQL/RabbitMQ/Redis）与 `192.168.88.137`（Elasticsearch）。请按需调整，或通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | 192.168.88.136 / 3306 / ops_ai_platform | MySQL 连接 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | root1 / 1 | MySQL 账号 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | 192.168.88.136 / 5672 | RabbitMQ 地址 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | admin / 1 | RabbitMQ 账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 192.168.88.136 / 6379 / 1 | Redis 地址 |
| `ES_URIS` | http://192.168.88.137:9200 | Elasticsearch 地址 |
| `JWT_SECRET` | 默认开发密钥 | JWT 签名密钥（**生产务必覆盖**） |
| `OPENCODE_API_KEY` / `BAILIAN_API_KEY` | — | 云端模型 API Key（可选） |

### 2. 初始化数据库

执行根目录 `mysql.sql` 创建表结构。

### 3. 启动后端

```bash
mvn -s settings.xml spring-boot:run
```

### 4. 验证

- 登录接口：`POST /auth/login`（默认账号 `admin`，密码见数据库 sys_user 表）
- 告警 Webhook：`POST /api/alerts/webhook`（Prometheus Alertmanager 风格）
- 触发诊断：`POST /ops-alerts/{id}/diagnose`，body 指定 `modelId`

### Docker 部署

项目提供 `Dockerfile` + `docker-compose.yml`（后端 + `web/` 前端）。构建镜像前通过环境变量注入中间件地址与密钥。

## RAG 设计说明

- **检索器**：BM25 词法召回（多字段加权 `title^3 / experienceSummary^2` 等）+ 向量语义召回（`nomic-embed-text` 768 维，ES `dense_vector` kNN），双路结果用 RRF（Reciprocal Rank Fusion）融合排序。
- **为何混合**：BM25 精确但「词不匹配就检不出」（如告警写 `ServiceDown`，历史记 `磁盘不足`），向量检索按语义召回能补上这类遗漏。实测 `ServiceDown` 告警 BM25 召回 0 条、向量召回 3 条。
- **容错降级**：RAG 检索失败 / ES 不可用 / embedding 生成失败时，自动降级为无 RAG 的普通诊断，不影响主流程。
- **配置**：`aiops.rag`（enabled / mode / top-k / min-score / rrf-k），`aiops.embedding`（模型 / base-url）。

## 已知局限

- Elasticsearch 索引字段类型为动态映射（text），与实体注解的 keyword 有出入，但不影响功能；干净做法是删索引后由 Spring Data 重建。
- 本地模型推理暂无超时 / 熔断（可接入 Resilience4j）。
- 生产者侧未配置 Publisher Confirm，消息可靠性仅覆盖消费端。

## 面试亮点速览

- 完整业务闭环：告警 → 工单 → AI 诊断 → 知识库 → 检索复用
- 消息可靠性设计：手动 ACK、死信队列、幂等建单
- AI 工程化：多模型接入、双通道输出容错、并发防重
- RAG 落地：BM25 + 向量混合检索 + RRF，AB 对比验证效果
- 面试 Q&A 详见 `简历_aiops项目_面试准备.md`
