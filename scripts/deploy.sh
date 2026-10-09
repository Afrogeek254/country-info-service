#!/usr/bin/env bash
# Build the image and deploy everything to Kubernetes.
#
# Examples
#   ./scripts/deploy.sh                                  # image stays in local docker (Docker Desktop k8s)
#   CLUSTER=minikube ./scripts/deploy.sh                 # loads the image into minikube
#   CLUSTER=kind ./scripts/deploy.sh                     # loads the image into kind
#   REGISTRY=docker.io/myuser IMAGE_TAG=1.0.1 ./scripts/deploy.sh   # pushes to a registry
set -euo pipefail

NAMESPACE="${NAMESPACE:-country-info}"
IMAGE_NAME="${IMAGE_NAME:-country-info-service}"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"
REGISTRY="${REGISTRY:-}"
CLUSTER="${CLUSTER:-}"

cd "$(dirname "$0")/.."

if [[ -n "$REGISTRY" ]]; then FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"; else FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"; fi

echo ">> Building image ${FULL_IMAGE}"
docker build -t "${FULL_IMAGE}" .

if [[ -n "$REGISTRY" ]]; then
  echo ">> Pushing to registry"
  docker push "${FULL_IMAGE}"
elif [[ "$CLUSTER" == "minikube" ]]; then
  echo ">> Loading image into minikube"
  minikube image load "${FULL_IMAGE}"
elif [[ "$CLUSTER" == "kind" ]]; then
  echo ">> Loading image into kind"
  kind load docker-image "${FULL_IMAGE}"
fi

echo ">> Applying namespace, config, secret, database"
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml
kubectl apply -f k8s/mysql.yaml
kubectl -n "$NAMESPACE" rollout status statefulset/mysql --timeout=300s

echo ">> Applying application"
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/hpa.yaml -f k8s/pdb.yaml
kubectl apply -f k8s/ingress.yaml || echo "!! Ingress not applied (no ingress controller?). Use port-forward instead."
kubectl -n "$NAMESPACE" set image deployment/country-info-service app="${FULL_IMAGE}"
kubectl -n "$NAMESPACE" rollout status deployment/country-info-service --timeout=300s

echo ">> Done."
kubectl -n "$NAMESPACE" get pods,svc,hpa
echo
echo "Test with:  kubectl -n $NAMESPACE port-forward svc/country-info-service 8080:80"
echo "            curl -X POST localhost:8080/api/v1/countries -H 'Content-Type: application/json' -d '{\"name\":\"kenya\"}'"
