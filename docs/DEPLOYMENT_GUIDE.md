# Kubernetes Deployment Guide

## What gets deployed (namespace `country-info`)

| File | Resource | Notes |
|---|---|---|
| `k8s/namespace.yaml` | Namespace | Isolation |
| `k8s/configmap.yaml` | ConfigMap `country-info-config` | Non-secret settings |
| `k8s/secret.yaml` | Secret `country-info-secret` | **Demo passwords. Replace before real use** |
| `k8s/mysql.yaml` | StatefulSet + headless Service `mysql` + 2Gi PVC | Demo database |
| `k8s/deployment.yaml` | Deployment (2 replicas) | Probes, limits, rolling update, non-root |
| `k8s/service.yaml` | Service (ClusterIP :80) | |
| `k8s/hpa.yaml` | HPA 2-6 pods @ 70% CPU | Needs metrics-server |
| `k8s/pdb.yaml` | PodDisruptionBudget | Keeps >= 1 pod during node drains |
| `k8s/ingress.yaml` | Ingress `country-info.local` `/api` | Needs ingress controller |

## 1. Prerequisites

- Docker, `kubectl`, and a cluster: **Docker Desktop** (Settings > Kubernetes > Enable) *or* **minikube** *or* **kind**.
- Check: `kubectl config current-context` and `kubectl get nodes` (node must be `Ready`).
- minikube extras: `minikube addons enable metrics-server` and `minikube addons enable ingress`.

## 2. Set secrets (do this first)

Edit `k8s/secret.yaml` (demo only), or better create it out-of-band and remove the file from git:
```bash
kubectl create namespace country-info
kubectl -n country-info create secret generic country-info-secret \
  --from-literal=DB_USERNAME=root \
  --from-literal=DB_PASSWORD='' \
  --from-literal=MYSQL_ROOT_PASSWORD='root'
```
(If you do this, skip `secret.yaml` when applying.)

## 3. Deploy: one command

| Cluster | Linux/macOS/Git-Bash | Windows PowerShell |
|---|---|---|
| Docker Desktop | `./scripts/deploy.sh` | `.\scripts\deploy.ps1` |
| minikube | `CLUSTER=minikube ./scripts/deploy.sh` | `.\scripts\deploy.ps1 -Cluster minikube` |
| kind | `CLUSTER=kind ./scripts/deploy.sh` | `.\scripts\deploy.ps1 -Cluster kind` |
| Remote cluster + registry | `REGISTRY=docker.io/<user> IMAGE_TAG=1.0.1 ./scripts/deploy.sh` | `.\scripts\deploy.ps1 -Registry docker.io/<user> -ImageTag 1.0.1` |

The script: builds the image -> makes it available to the cluster (load or push) -> applies namespace, config, secret ->
applies MySQL and **waits until it is ready** -> applies the app, service, HPA, PDB, ingress -> sets the image ->
waits for the rollout.

### Manual equivalent
```bash
docker build -t country-info-service:1.0.0 .
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml
kubectl apply -f k8s/mysql.yaml
kubectl -n country-info rollout status statefulset/mysql
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/hpa.yaml -f k8s/pdb.yaml -f k8s/ingress.yaml
kubectl -n country-info rollout status deployment/country-info-service
```

## 4. Verify

```bash
kubectl -n country-info get pods,svc,hpa,pvc          # all Running / Ready 1/1
kubectl -n country-info port-forward svc/country-info-service 8080:80
curl http://localhost:8080/actuator/health            # {"status":"UP",...}
curl -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d "{\"name\":\"kenya\"}"
```
Through the ingress (minikube): add `<minikube ip>  country-info.local` to the hosts file, then
`curl http://country-info.local/api/v1/countries`.

Confirm the data is in MySQL:
```bash
kubectl -n country-info exec -it mysql-0 -- mysql -ucountryapp -p countrydb -e "select id,iso_code,name from country_info;"
```

## 5. Operate

| Task | Command |
|---|---|
| Logs (all pods) | `kubectl -n country-info logs -l app=country-info-service --tail=100 -f` |
| Scale manually | `kubectl -n country-info scale deployment/country-info-service --replicas=4` (HPA may override) |
| Change config | edit `k8s/configmap.yaml`, `kubectl apply -f ...`, `kubectl -n country-info rollout restart deployment/country-info-service` |
| Release new version | build new tag, `kubectl -n country-info set image deployment/country-info-service app=<image:tag>` |
| Roll back | `kubectl -n country-info rollout undo deployment/country-info-service` |
| History | `kubectl -n country-info rollout history deployment/country-info-service` |
| Remove everything | `./scripts/undeploy.sh` (**deletes the DB volume**) |

## 6. Production hardening checklist

- Use a managed/HA MySQL, remove `mysql.yaml`, point `DB_URL` at it.
- Use Sealed Secrets / External Secrets / Vault; remove `secret.yaml` from git.
- Push images to a private registry with immutable tags (never `latest`); add `imagePullSecrets`.
- Add TLS to the Ingress (cert-manager) and NetworkPolicies (only ingress -> app, only app -> DB + provider).
- Switch to Flyway migrations and `DDL_AUTO=validate`.
- Scrape `/actuator/prometheus` and alert on: 5xx rate, p95 latency, circuit breaker open, pod restarts, DB pool saturation.
