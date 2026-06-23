# Lease Track CI/CD and Kubernetes

This document describes the CI/CD and Kubernetes reference setup included with Lease Track. It is intended for local development, demos, and as a starting point for production deployment planning.

## Overview

Lease Track is packaged as two application images:

- `lease-track-backend`: Spring Boot API
- `lease-track-frontend`: React/Vite frontend served by Nginx

The Kubernetes manifests also include local development dependencies:

- PostgreSQL StatefulSet
- RabbitMQ StatefulSet

The manifests use plain Kubernetes YAML instead of Helm or cloud-specific infrastructure. They demonstrate the deployment structure, health checks, ConfigMaps, Secret templates, resource limits, rolling updates, and CI/CD flow.

## Important Notes

- Do not commit real secrets. Use `k8s/secret.template.yaml` as a template only.
- The included PostgreSQL and RabbitMQ manifests are for local or demo environments.
- Production deployments should normally use managed database, messaging, storage, secret management, TLS, monitoring, and backup services.
- The frontend reads `VITE_API_BASE_URL` at build time. Build the frontend image with the correct API URL for the target environment.
- The backend defaults to one replica. Scaling horizontally requires shared document storage and coordination for scheduled jobs.

## Repository Structure

```text
k8s/
  namespace.yaml
  configmap.yaml
  secret.template.yaml
  postgres.yaml
  rabbitmq.yaml
  backend.yaml
  frontend.yaml
  local-access.yaml
```

`configmap.yaml` contains non-sensitive application configuration. `secret.template.yaml` lists the required secret keys and should be copied or recreated with environment-specific values before deployment.

## Prerequisites

- Java 21 and Maven
- Bun
- Docker
- kubectl
- A local Kubernetes cluster such as Minikube or Docker Desktop Kubernetes
- Jenkins with Docker and kubectl available on the build agent

## Build Images

Build the backend image:

```sh
docker build -t lease-track-backend:local .
```

Build the frontend image:

```sh
docker build \
  --build-arg VITE_API_BASE_URL=http://localhost:30081 \
  -t lease-track-frontend:local \
  ./frontend
```

For Minikube, build inside the Minikube Docker daemon before running the same build commands:

```sh
eval "$(minikube docker-env)"
```

## Create Kubernetes Secrets

Create the namespace first:

```sh
kubectl apply -f k8s/namespace.yaml
```

Copy the secret template, replace every placeholder, and apply it:

```sh
cp k8s/secret.template.yaml /tmp/lease-track-secrets.yaml
kubectl apply -f /tmp/lease-track-secrets.yaml
```

For private container images, create a registry pull secret in the same namespace:

```sh
kubectl -n lease-track create secret docker-registry registry-creds \
  --docker-server=<registry-host> \
  --docker-username=<username> \
  --docker-password=<token>
```

## Deploy Locally

Apply the manifests:

```sh
kubectl apply -f k8s/namespace.yaml
kubectl apply -f /tmp/lease-track-secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/rabbitmq.yaml
kubectl apply -f k8s/backend.yaml
kubectl apply -f k8s/frontend.yaml
kubectl apply -f k8s/local-access.yaml
```

If using local image tags, update the Deployments:

```sh
kubectl -n lease-track set image deployment/lease-track-backend backend=lease-track-backend:local
kubectl -n lease-track set image deployment/lease-track-frontend frontend=lease-track-frontend:local
```

## Verify Deployment

Check pod status and rollout progress:

```sh
kubectl -n lease-track get pods
kubectl -n lease-track rollout status deployment/lease-track-backend
kubectl -n lease-track rollout status deployment/lease-track-frontend
```

Check backend readiness:

```sh
kubectl -n lease-track port-forward service/lease-track-backend 8080:8080
curl -fsS http://localhost:8080/actuator/health/readiness
```

For local NodePort access with Minikube:

```sh
minikube service lease-track-frontend-nodeport -n lease-track
minikube service lease-track-backend-nodeport -n lease-track
```

## Jenkins Pipeline

The root `Jenkinsfile` validates, builds, publishes, and deploys the application.

Required Jenkins credentials:

- `docker-registry-creds`: username/password credential for the container registry
- `kubeconfig-creds`: file credential containing kubeconfig for the target cluster

Required Kubernetes secrets:

- `lease-track-secrets`: application, database, RabbitMQ, JWT, and optional storage values
- `registry-creds`: registry pull credentials for private backend and frontend images

Before using the pipeline, update the registry and environment values in `Jenkinsfile` for the target deployment environment.

Pipeline behavior:

1. Checks out the repository.
2. Runs backend tests.
3. Installs frontend dependencies.
4. Runs frontend tests.
5. Builds backend and frontend artifacts.
6. Builds Docker images.
7. Validates Kubernetes manifests.
8. Pushes images when deployment is enabled.
9. Applies Kubernetes manifests.
10. Updates Deployment images.
11. Annotates Deployments with build metadata.
12. Verifies rollout status.
13. Runs lightweight health checks.

Feature branches and pull requests run validation and build stages. Deployments are intended to run from `main` when explicitly enabled by pipeline parameters.

## Rollback

View rollout history:

```sh
kubectl -n lease-track rollout history deployment/lease-track-backend
kubectl -n lease-track rollout history deployment/lease-track-frontend
```

Roll back to the previous revision:

```sh
kubectl -n lease-track rollout undo deployment/lease-track-backend
kubectl -n lease-track rollout undo deployment/lease-track-frontend
```

Roll back to a specific revision:

```sh
kubectl -n lease-track rollout undo deployment/lease-track-backend --to-revision=<revision>
kubectl -n lease-track rollout undo deployment/lease-track-frontend --to-revision=<revision>
```

## Production Considerations

A production deployment should review and add:

- Managed PostgreSQL with backups and recovery
- Managed RabbitMQ or a cloud messaging service
- External secret management
- TLS ingress and DNS
- Centralized logging, metrics, and alerting
- Shared object storage for uploaded documents
- Scheduler locking, leader election, or dedicated worker processes
- Separate environments with controlled promotion between them
