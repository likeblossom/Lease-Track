# Lease Track

Lease Track is a lease and notice management platform for landlords, property managers, and teams that need reliable records around rental compliance work. It helps users track properties, units, leases, legal notices, delivery attempts, supporting documents, deadlines, and audit history in one place.

This project is meant to make lease administration easier to follow and easier to prove. Instead of keeping notices, delivery confirmations, uploaded evidence, and status updates across separate tools, Lease Track organizes that information into a searchable workflow and can produce evidence packages when records need to be reviewed.

## Built With

- Java 21
- Spring Boot 3.5
- Maven
- PostgreSQL
- Flyway
- RabbitMQ
- Spring Security with JWT authentication
- React 19
- TypeScript
- Vite
- Bun
- Docker and Docker Compose
- Kubernetes
- Jenkins
- AWS SDK for S3 storage support
- Apache PDFBox
- springdoc-openapi for Swagger API documentation

## Getting Started

### Prerequisites

- Java 21 or newer
- Maven 3.9+
- Docker and Docker Compose for local PostgreSQL/RabbitMQ
- Bun

You can check the local toolchain with:

```sh
scripts/check-dev-env.sh
```

### Installation

Clone the repository and move into the project directory:

```sh
git clone <repository-url>
cd Lease-Track
```

Install frontend dependencies:

```sh
cd frontend
bun install
cp .env.example .env
cd ..
```

The frontend expects `VITE_API_BASE_URL=http://localhost:8080` for local backend calls.

### Run Locally

Start PostgreSQL and RabbitMQ:

```sh
docker compose up -d postgres rabbitmq
```

Run the backend:

```sh
mvn spring-boot:run
```

Run the frontend:

```sh
cd frontend
bun install
cp .env.example .env
bun run dev
```

The backend runs on `http://localhost:8080` and the frontend runs on `http://localhost:5173` by default.

You can also run the full local stack with Docker:

```sh
docker compose up --build
```

## Deployment and CI/CD

Lease Track includes a production-style deployment reference using Docker, Jenkins, and plain Kubernetes manifests.

- Backend and frontend images are built with multi-stage Dockerfiles.
- Kubernetes manifests under `k8s/` define the backend, frontend, PostgreSQL, RabbitMQ, ConfigMap, Secret template, health probes, resources, and local access services.
- The root `Jenkinsfile` runs backend/frontend tests, builds Docker images, validates Kubernetes manifests, optionally pushes images, deploys on `main`, verifies rollouts, and annotates deployments with build metadata.
- PostgreSQL and RabbitMQ manifests are intended for local/dev Kubernetes. A production environment would normally use managed database and messaging services.

See `docs/cicd-kubernetes.md` for local Kubernetes setup, Jenkins credentials, secret creation, deployment commands, rollback commands, and production tradeoffs.

## Verification

Run backend tests:

```sh
mvn test
```

Run frontend tests and build:

```sh
cd frontend
bun run test
bun run build
```

Run endpoint smoke checks against a running backend:

```sh
scripts/smoke-endpoints.sh
```
