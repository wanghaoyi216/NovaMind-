# Local Infrastructure

1. Start only the infrastructure you need.

   Core services for most modules:

   ```powershell
   docker compose up -d mysql redis nacos minio
   ```

   Add feature middleware only when the corresponding modules need it:

   ```powershell
   docker compose up -d rabbitmq mongodb es xxl-job
   ```

2. Import Nacos shared configs:

   ```powershell
   .\docker\nacos\import-config.ps1
   ```

3. If `docker/mysql/data` already existed before the schema script was added, apply the generated DDL manually:

   ```powershell
   .\docker\mysql\apply-init.ps1
   ```

4. Run the backend services from the IDE or from local jars on the host. The backend no longer uses a Docker image compose file.

The generated schema in `docker/mysql/init/01-novamind-schema.sql` is a permissive bootstrap schema generated from MyBatis Plus entity classes. It is suitable for local startup, but it is not a replacement for the original production DDL and seed data.

## 新版前端容器化部署（novamind-portal）

`docker/tj-portal`、`docker/tj-admin` 下是旧版前端的遗留构建产物（品牌为「知行 AI 学堂」，已过时），目前仍由 compose 的 `nginx` 服务（80 端口）提供，仅为兼容默认行为而保留；新版前端源码位于 `novamind-portal/`（Vue 3.5 + Vite 6 + TypeScript），可通过 `portal` 服务容器化运行，并将最终替代旧版 dist。

`portal` 服务位于 `profiles: ["portal"]` 下，默认不启动，因此 `docker compose up` 的行为与从前完全一致。启用新版前端：

```bash
# 首次运行会构建镜像（阶段 1：node:22-alpine 执行 npm ci + vue-tsc + vite build；
# 阶段 2：nginx:1.27-alpine 仅含静态 dist 与自定义 nginx 配置）
docker compose --profile portal up -d --build portal
# 访问 http://localhost:8080
```

要点：

- **网关地址（GATEWAY_URL）**：后端 Java 服务在宿主机运行（上文第 4 条），portal 容器默认经 `host.docker.internal:10010` 访问网关；Linux 上由 compose 的 `extra_hosts: host.docker.internal:host-gateway` 提供解析。若网关本身也容器化，在 `.env` 中设置 `PORTAL_GATEWAY_URL=http://<网关容器名>:10010` 覆盖（镜像内置默认值为 `http://gateway:10010`，仅供网关同名容器场景使用）。
- **代理前缀**：镜像内 nginx 完整复刻 `novamind-portal/vite.config.ts` 的 dev 代理规则——`/ais` 原样透传（AI 对话 SSE 流式，已关闭 `proxy_buffering` 并延长读超时）、`/api` 去掉前缀后转发（`/api/foo` → 网关 `/foo`）。旧版 nginx.conf 中的 14 个业务服务前缀仅服务于旧版 dist，新版前端不需要。
- **静态资源**：`/assets/`（内容哈希文件名）按一年不可变缓存下发，已开启 gzip；`sw.js`、`manifest.webmanifest`（PWA）强制 `no-cache`；SPA history 路由回退到 `index.html`；RAG 文档上传走 `/api`，`client_max_body_size` 已放宽到 50m。
- **健康检查**：容器提供 `/healthz` 端点，镜像 HEALTHCHECK 与 compose healthcheck 均基于它。
- **端口关系**：新版前端在 **8080**，旧版 nginx 在 **80**，二者可并存对比，切换稳定后可下线旧版。

## 环境变量说明

所有口令类配置统一放在项目根目录 `.env`（docker compose 自动读取；模板见 `.env.example`，`.env` 已被 `.gitignore` 忽略，严禁提交仓库）：

| 变量 | 用途 | 影响的服务 |
|------|------|-----------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 口令；同时经 compose 以 `SPRING_DATASOURCE_PASSWORD` 注入 xxl-job，覆盖其挂载配置中的硬编码值 | `mysql`、`xxl-job` |
| `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS` | RabbitMQ 管理账号（控制台 :15672） | `rabbitmq` |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | MinIO root 账号（控制台 :9001） | `minio` |
| `MONGO_INITDB_ROOT_USERNAME` / `MONGO_INITDB_ROOT_PASSWORD` | MongoDB root 账号，**仅在首次初始化数据卷时生效**（改口令需删卷重建） | `mongodb` |
| `NEO4J_USER` / `NEO4J_PASSWORD` | Neo4j 账号，拼装为 `NEO4J_AUTH`，**同样仅首次初始化生效** | `neo4j` |

未纳入 `.env` 的口令及同步注意事项：

- **Redis**：口令未走 compose 环境变量，`requirepass` 写在 `docker/redis/conf/redis.conf` 中；修改后需重启 redis 容器，并同步 Nacos 的 `shared-redis.yaml`。
- **MySQL 口令变更的联动**：改 `MYSQL_ROOT_PASSWORD` 后，xxl-job 会自动跟随；但已初始化的 `docker/mysql/data` 卷内账号不变（需进容器改口令），且 Nacos 中各数据源配置（`{服务名}.yaml`、`shared-*.yaml`）里的口令需手工同步。
- **Elasticsearch / Nacos / Jaeger**：compose 中未启用口令（`xpack.security.enabled=false`、`NACOS_AUTH_ENABLE=false`），仅限本地开发使用，勿暴露到公网。
- **ES 版本**：镜像已对齐后端客户端版本 8.13.4（原为 7.12.1），旧数据目录需备份清空后重新启动（详见根 README「已知事项」，未经端到端验证）。
