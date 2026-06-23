pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  parameters {
    booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: 'Push images and deploy to Kubernetes when building main.')
    choice(name: 'DEPLOY_ENV', choices: ['dev'], description: 'Target deployment environment.')
  }

  environment {
    REGISTRY = 'ghcr.io/example'
    BACKEND_IMAGE = "${REGISTRY}/lease-track-backend"
    FRONTEND_IMAGE = "${REGISTRY}/lease-track-frontend"
    FRONTEND_API_BASE_URL = 'http://localhost:30081'
    KUBE_NAMESPACE = 'lease-track'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
          env.IMAGE_TAG = "${env.GIT_SHORT_SHA}-${env.BUILD_NUMBER}"
        }
      }
    }

    stage('Backend Test') {
      steps {
        sh 'mvn test'
      }
    }

    stage('Frontend Install') {
      steps {
        dir('frontend') {
          sh 'bun install --frozen-lockfile'
        }
      }
    }

    stage('Frontend Test') {
      steps {
        dir('frontend') {
          sh 'bun run test'
        }
      }
    }

    stage('Build Artifacts') {
      parallel {
        stage('Backend Package') {
          steps {
            sh 'mvn -DskipTests package'
          }
        }
        stage('Frontend Build') {
          steps {
            dir('frontend') {
              sh 'bun run build'
            }
          }
        }
      }
    }

    stage('Build Docker Images') {
      steps {
        sh 'docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} -t ${BACKEND_IMAGE}:build-${BUILD_NUMBER} .'
        sh 'docker build --build-arg VITE_API_BASE_URL=${FRONTEND_API_BASE_URL} -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -t ${FRONTEND_IMAGE}:build-${BUILD_NUMBER} ./frontend'
      }
    }

    stage('Validate Kubernetes Manifests') {
      when {
        allOf {
          branch 'main'
          expression { return params.RUN_DEPLOY }
        }
      }
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-creds', variable: 'KUBECONFIG_FILE')]) {
          sh '''
            export KUBECONFIG="$KUBECONFIG_FILE"
            kubectl apply --dry-run=client -f k8s/
          '''
        }
      }
    }

    stage('Push Docker Images') {
      when {
        allOf {
          branch 'main'
          expression { return params.RUN_DEPLOY }
        }
      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker-registry-creds', usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'printf "%s" "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USERNAME" --password-stdin'
          sh 'docker push ${BACKEND_IMAGE}:${IMAGE_TAG}'
          sh 'docker push ${BACKEND_IMAGE}:build-${BUILD_NUMBER}'
          sh 'docker push ${FRONTEND_IMAGE}:${IMAGE_TAG}'
          sh 'docker push ${FRONTEND_IMAGE}:build-${BUILD_NUMBER}'
        }
      }
    }

    stage('Deploy to Kubernetes') {
      when {
        allOf {
          branch 'main'
          expression { return params.RUN_DEPLOY }
        }
      }
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-creds', variable: 'KUBECONFIG_FILE')]) {
          sh '''
            export KUBECONFIG="$KUBECONFIG_FILE"
            kubectl apply -f k8s/namespace.yaml
            kubectl -n "$KUBE_NAMESPACE" get secret lease-track-secrets
            kubectl apply -f k8s/configmap.yaml
            kubectl apply -f k8s/postgres.yaml
            kubectl apply -f k8s/rabbitmq.yaml
            kubectl apply -f k8s/backend.yaml
            kubectl apply -f k8s/frontend.yaml
            kubectl apply -f k8s/local-access.yaml
            kubectl -n "$KUBE_NAMESPACE" set image deployment/lease-track-backend backend="${BACKEND_IMAGE}:${IMAGE_TAG}"
            kubectl -n "$KUBE_NAMESPACE" set image deployment/lease-track-frontend frontend="${FRONTEND_IMAGE}:${IMAGE_TAG}"
          '''
        }
      }
    }

    stage('Verify Rollout') {
      when {
        allOf {
          branch 'main'
          expression { return params.RUN_DEPLOY }
        }
      }
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-creds', variable: 'KUBECONFIG_FILE')]) {
          sh '''
            export KUBECONFIG="$KUBECONFIG_FILE"
            kubectl -n "$KUBE_NAMESPACE" rollout status deployment/lease-track-backend --timeout=180s
            kubectl -n "$KUBE_NAMESPACE" rollout status deployment/lease-track-frontend --timeout=120s
          '''
        }
      }
    }

    stage('Lightweight Smoke Checks') {
      when {
        allOf {
          branch 'main'
          expression { return params.RUN_DEPLOY }
        }
      }
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-creds', variable: 'KUBECONFIG_FILE')]) {
          sh '''
            export KUBECONFIG="$KUBECONFIG_FILE"
            kubectl -n "$KUBE_NAMESPACE" run lease-track-smoke-"$BUILD_NUMBER" \
              --rm --restart=Never --image=curlimages/curl:8.11.1 -- \
              sh -c 'curl -fsS http://lease-track-backend:8080/actuator/health/readiness && curl -fsS http://lease-track-backend:8080/v3/api-docs >/dev/null && curl -fsS http://lease-track-frontend/healthz'
          '''
        }
      }
    }
  }

  post {
    always {
      sh 'docker logout "$REGISTRY" || true'
    }
  }
}
