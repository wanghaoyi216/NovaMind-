# 本地构建注记：原第 1 行为 parser directive「syntax=docker/dockerfile:1」，
# 会从 registry 拉取 frontend 镜像，当前镜像源限流导致拉取失败。
# 本文件仅用标准特性，Docker 29 内置 frontend 完全支持，故本地移除该指令。
# (disabled locally) syntax=docker/dockerfile:1
# =====================================================================
# NovaMind 门户（novamind-portal 新版前端）镜像 —— 多阶段构建
#   阶段 1  node:22-alpine    npm ci + vue-tsc 类型检查 + vite build
#   阶段 2  nginx:1.27-alpine 仅含静态 dist 与自定义 nginx.conf（模板渲染）
#
# 使用：在仓库根目录执行
#   docker compose --profile portal up -d --build portal
# 或单独构建：docker build -t tjxt-portal:local ./novamind-portal
# =====================================================================

########################## 阶段 1：构建产物 ##########################
FROM node:22-alpine AS build

WORKDIR /app

# 先只拷贝依赖清单并安装全部依赖（构建需要 devDependencies：vite / vue-tsc / sass 等，
# 故不要设置 NODE_ENV=production）。依赖清单未变时该层可复用缓存。
COPY package.json package-lock.json ./
RUN npm ci

# 再拷贝构建所需源码与配置。
# 刻意逐项 COPY（而非 COPY . .）：构建上下文中的 node_modules/、dist/、
# tests/、课程笔记 md 等既不参与构建，也避免拖慢上下文传输/误入镜像。
COPY index.html vite.config.ts vitest.config.ts tsconfig.json tsconfig.node.json env.d.ts postcss.config.js tailwind.config.js ./
COPY src ./src
COPY public ./public

# 对应 package.json 的 build 脚本：vue-tsc --noEmit && vite build
RUN npm run build

########################## 阶段 2：运行时 ############################
FROM nginx:1.27-alpine

# 网关地址默认值（Spring Cloud Gateway :10010）。
# 官方镜像启动时会用 /etc/nginx/templates/*.template 经 envsubst 渲染为
# /etc/nginx/conf.d/*.conf，本镜像的 nginx.conf 即按此机制消费该变量；
# 仅替换「已定义的环境变量」，nginx 运行时变量（$uri 等）不受影响。
# compose 场景由 portal 服务的 GATEWAY_URL 覆盖（见 docker-compose.yml）。
ENV GATEWAY_URL=http://gateway:10010

# 静态产物（含 PWA：sw.js / manifest.webmanifest / pwa-icons）
COPY --from=build /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/templates/default.conf.template

EXPOSE 80

# 自检 /healthz（镜像内置；compose 的 portal 服务另有同款 healthcheck）
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=10 \
  CMD wget -q --spider http://127.0.0.1/healthz || exit 1
