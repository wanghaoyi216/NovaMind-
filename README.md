<div align="center">

<!-- Banner placeholder -->
<br/>

# 🌌 NovaMind

### *Cloud-Native AI-Powered Online Education Platform*

<br/>

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Alibaba-6DB33F.svg)](https://github.com/alibaba/spring-cloud-alibaba)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-FF6F00.svg)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D.svg)](https://vuejs.org)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6.svg)](https://www.typescriptlang.org)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00.svg)](https://openjdk.org)

<br/>

**NovaMind** is a production-grade online education platform built on a
**Spring Cloud Alibaba** microservice architecture, with **Spring AI**–powered
**RAG-based content generation**, intelligent course/exam workflows, and a
modern **Vue 3 + TypeScript** frontend — fully orchestrated via **Docker Compose**.

<br/>

[🚀 Quick Start](#-quick-start) · [🏗️ Architecture](#%EF%B8%8F-architecture) · [📦 Modules](#-modules) · [🧰 Tech Stack](#-tech-stack) · [🛠️ Development](#%EF%B8%8F-development) · [📄 License](#-license)

</div>

---

## ✨ Features

| | |
|---|---|
| 🎯 **Course & Exam Management** | End-to-end course catalog, lesson scheduling, exam authoring, grading, and rich-media delivery. |
| 🤖 **AI Content Generation** | Spring AI + Alibaba Cloud AI integration, with **Retrieval-Augmented Generation (RAG)** for context-aware answers, quiz generation, and study-assistant chat. |
| 🧩 **Microservice Architecture** | 18+ Spring Cloud Alibaba services behind a unified gateway — independent deployability, fault isolation, and horizontal scaling. |
| ⚡ **Distributed Infrastructure** | Nacos service discovery & config, RabbitMQ async messaging, Redis distributed cache, ElasticSearch full-text search, XXL-JOB distributed scheduling, Jaeger distributed tracing. |
| 🖥️ **Modern Frontend** | Vue 3 + TypeScript + Vite + TailwindCSS, with component tests (Vitest) and a clean admin/portal separation. |
| 🐳 **One-Command Deploy** | Full stack — MySQL, Redis, MongoDB, RabbitMQ, ES, MinIO, Nacos, XXL-JOB, Jaeger, Nginx, and every microservice — brought up with a single `docker compose up`. |
| 🔐 **Auth & Security** | Unified authentication, JWT, role-based access control across gateway-level routing. |
| 📊 **Observability** | Distributed tracing via Jaeger, structured logging, and centralized configuration via Nacos. |

---

## 🏗️ Architecture

```
                            ┌──────────────────────────────┐
                            │      Browser / Mobile App    │
                            └──────────────┬───────────────┘
                                           │
                                           ▼
                              ┌────────────────────────┐
                              │   Nginx  /  Gateway    │  ← novamind-gateway
                              └──────────┬─────────────┘
                                         │
        ┌────────────┬────────────┬──────┴───────┬────────────┬─────────────┐
        ▼            ▼            ▼              ▼             ▼             ▼
   ┌────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
   │  Auth  │  │  Course  │  │ Learning │  │   Exam   │  │   AIGC   │  │  Trade   │
   └────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
        │            │            │              │             │             │
        ▼            ▼            ▼              ▼             ▼             ▼
   ┌────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
   │ User   │  │  Media   │  │ Search   │  │  Remark  │  │  Data    │  │   Pay    │
   └────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────────┐
                         │  Shared Infrastructure (Docker)  │
                         ├──────────────────────────────────┤
                         │ Nacos │ MySQL │ Redis │ RabbitMQ │
                         │ Mongo │ ES    │ MinIO │ XXL-JOB │
                         │ Jaeger│ Nginx │       │         │
                         └──────────────────────────────────┘
```

**Request flow** — A user request flows through **Nginx** → **novamind-gateway** (routing + auth) → the appropriate business microservice → shared infrastructure (Nacos for config/discovery, MySQL/Mongo for persistence, Redis for cache, RabbitMQ for async, ES for search, MinIO for media).

---

## 📦 Modules

| # | Module | Responsibility |
|---|---|---|
| 01 | `novamind-gateway`   | Unified API gateway, routing, auth filter, rate limiting. |
| 02 | `novamind-auth`      | Authentication, JWT issuance & validation, RBAC. |
| 03 | `novamind-user`      | User profiles, account management, learner data. |
| 04 | `novamind-course`    | Course catalog, lesson authoring, course media binding. |
| 05 | `novamind-learning`  | Learner progress tracking, lesson sequencing, learning state. |
| 06 | `novamind-exam`      | Exam papers, question bank, auto-grading, results. |
| 07 | `novamind-trade`     | Order creation, cart, checkout flow. |
| 08 | `novamind-pay`       | Payment integration, callbacks, reconciliation. |
| 09 | `novamind-message`   | In-app notifications & async messaging consumers. |
| 10 | `novamind-search`    | Full-text search across courses/exams using Elasticsearch. |
| 11 | `novamind-media`     | Media asset metadata, transcoding coordination, MinIO integration. |
| 12 | `novamind-remark`    | Reviews, ratings, comment threads. |
| 13 | `novamind-promotion` | Coupons, flash sales, marketing campaigns. |
| 14 | `novamind-data`      | Analytics, reporting, BI-feeds. |
| 15 | `novamind-portal`    | BFF/aggregation layer for end-user portal. |
| 16 | `novamind-api`       | Public-facing shared DTOs & API contracts. |
| 17 | `novamind-common`    | Shared utilities, base entities, common response wrappers. |
| 18 | `novamind-aigc`      | **AI content generation** — Spring AI + RAG, vector store, prompt templates. |

---

## 🧰 Tech Stack

### Backend
- **Java 17**, **Spring Boot 3.x**, **Spring Cloud Alibaba**
- **Spring AI 1.0** + **Spring AI Alibaba** (DashScope / Tongyi)
- **MyBatis-Plus**, **Hibernate Validator**
- **XXL-JOB** distributed scheduling
- **Seata** distributed transactions (where required)
- **OpenFeign** inter-service calls, **Sentinel** flow control

### AI / RAG
- **Spring AI** chat models, embeddings, vector stores
- **RAG** pipelines with document loaders, splitters, and retrievers
- Prompt engineering utilities, function-calling, structured output

### Frontend
- **Vue 3.4** (Composition API), **TypeScript 5**, **Vite 5**
- **TailwindCSS**, **Pinia**, **Vue Router**
- **Axios**, **Vitest** for unit tests

### Infrastructure
- **Nacos** — service discovery & config center
- **MySQL 8** — primary persistence
- **MongoDB** — document storage
- **Redis 7** — distributed cache & session
- **RabbitMQ** — async messaging
- **ElasticSearch 8** — full-text search
- **MinIO** — S3-compatible object storage for media
- **XXL-JOB** — distributed job scheduling
- **Jaeger** — distributed tracing
- **Nginx** — reverse proxy & static asset hosting

### DevOps
- **Docker** + **Docker Compose** multi-service orchestration
- **GitHub Actions** CI (see `.github/workflows/`)

---

## 🚀 Quick Start

> **Prerequisites:** Docker ≥ 24.x, Docker Compose ≥ v2.20+, Node.js 20+ (for portal dev), JDK 17+ (for backend dev).

### 1️⃣ Clone

```bash
git clone https://github.com/wanghaoyi216/NovaMind-.git
cd NovaMind-
```

### 2️⃣ Bring up the full stack (infrastructure + services)

```bash
# Start MySQL / Redis / Nacos / RabbitMQ / ES / Mongo / MinIO / XXL-JOB / Jaeger / Nginx
docker compose up -d mysql redis nacos rabbitmq mongodb es minio xxl-job jaeger nginx

# Wait for Nacos to register services, then start business services
docker compose up -d novamind-gateway novamind-auth novamind-user novamind-course \
                      novamind-learning novamind-exam novamind-aigc novamind-trade \
                      novamind-pay novamind-message novamind-search novamind-media \
                      novamind-remark novamind-promotion novamind-data novamind-portal
```

> 💡 **Tip:** All service images share the `novamind-network` compose network. Service discovery happens automatically via Nacos.

### 3️⃣ Verify

```bash
# Check running containers
docker compose ps

# Gateway health
curl http://localhost:8080/actuator/health

# Nacos dashboard
open http://localhost:8848/nacos   # default: nacos / nacos
```

### 4️⃣ Frontend dev (optional — full UI on top of the gateway)

```bash
cd novamind-portal
npm install
npm run dev          # → http://localhost:5173
npm run build        # production bundle into dist/
npm run test         # Vitest unit tests
```

---

## 🛠️ Development

### Build the backend (all modules)

```bash
mvn clean install -DskipTests
```

### Run a single module locally

```bash
cd novamind-course
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
```

### IDE setup
- **IntelliJ IDEA** (Ultimate recommended) — install Lombok, MyBatisX, Docker plugins.
- Open the root `pom.xml` as a Maven project; modules auto-link.

### Code conventions
- 4-space indent, UTF-8, LF line endings
- Conventional Commits for commit messages (`feat: …`, `fix: …`, `refactor: …`)
- Each microservice owns its own DB schema; cross-service reads go through Feign, **never** direct DB joins.

---

## 🗺️ Roadmap

- [x] Core microservice skeleton (auth / user / course / learning / exam / trade / pay)
- [x] Spring AI + RAG content generation module
- [x] Vue 3 + TS portal frontend
- [x] Docker Compose full-stack orchestration
- [ ] Multi-tenant isolation
- [ ] Mobile (uni-app) client
- [ ] Observability stack upgrade (Prometheus + Grafana + Loki)
- [ ] CI/CD pipeline (ArgoCD / K8s manifests)
- [ ] Internationalization (i18n) for portal

---

## 🤝 Contributing

Contributions are welcome — PRs, issue reports, and feature proposals.

1. Fork the repository
2. Create your branch (`git checkout -b feat/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feat/amazing-feature`)
5. Open a Pull Request

Please make sure tests pass and commits follow Conventional Commits.

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for the full text.
The [NOTICE](NOTICE) file contains the project copyright statement.

```
Copyright 2026 NovaMind Contributors
Licensed under the Apache License, Version 2.0
```

---

## 🙏 Acknowledgments

Built with the help of an outstanding open-source ecosystem — Spring, Vue,
Alibaba Cloud, and countless maintainers whose libraries made this project
possible. Thank you.

---

<div align="center">

**⭐ Star this repo if you find it useful — it helps a lot.**

<sub>Made with care · NovaMind Project</sub>

</div>