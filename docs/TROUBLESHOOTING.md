# Troubleshooting on Kubernetes

Set a shortcut first: `kubectl config set-context --current --namespace=country-info`
(otherwise add `-n country-info` to every command).

## 0. Five-command triage

```bash
kubectl get pods -o wide                       # status, restarts, node
kubectl describe pod <pod>                     # Events at the bottom explain most problems
kubectl logs <pod> --tail=200                  # app logs ("--previous" for the crashed container)
kubectl get events --sort-by=.lastTimestamp    # cluster-level events
kubectl rollout status deployment/country-info-service
```

## 1. Symptom -> cause -> fix

| Symptom | Likely cause | How to confirm | Fix |
|---|---|---|---|
| `ImagePullBackOff` / `ErrImagePull` | Image not available to the cluster | `describe pod` -> Events | minikube: `minikube image load <img>`; kind: `kind load docker-image <img>`; remote: push + check `imagePullSecrets`, tag |
| `ErrImageNeverPull` | Local image + wrong pull policy | `describe pod` | Keep `imagePullPolicy: IfNotPresent` and load the image into the cluster |
| `Init:0/1` stuck | `wait-for-mysql` cannot reach MySQL | `logs <pod> -c wait-for-mysql`; `get pods -l app=mysql` | Fix MySQL first (see section 2) |
| `CrashLoopBackOff` | App fails at boot (DB credentials, URL, port) | `logs <pod> --previous` | Look for `Communications link failure` / `Access denied`; check ConfigMap `DB_URL` and Secret |
| `OOMKilled` (Last State) | Memory limit too low for heap | `describe pod` -> Last State: OOMKilled | Raise `limits.memory`; `MaxRAMPercentage=75` already leaves headroom |
| Pod `Running` but `0/1` Ready | Readiness probe failing (DB unreachable) | `curl` `/actuator/health/readiness` via port-forward | Fix DB connectivity; pod joins the Service automatically |
| Pod restarts repeatedly, no crash in logs | Liveness failing (slow start / CPU throttling) | `describe pod` -> "Liveness probe failed" | Raise `startupProbe.failureThreshold` or CPU limit |
| `Pending` | No node has the requested CPU/memory, or PVC unbound | `describe pod` -> FailedScheduling; `get pvc` | Free resources / lower requests; check StorageClass for PVC |
| HPA shows `<unknown>/70%` | metrics-server missing | `kubectl top pods` fails | Install metrics-server (`minikube addons enable metrics-server`) |
| Ingress returns 404/502 | No controller, wrong host, no ready endpoints | `get ingress`; `get endpoints country-info-service` | Enable controller, add host to hosts file, fix readiness |
| 503 from POST | SOAP provider down/slow or circuit open | Logs: `SOAP operation ... failed`; health shows circuitBreaker `OPEN` | See section 3 |
| 404 from POST | Provider does not know that country | Log: `Not found: Country '...'` | Correct the name (expected behaviour) |
| 409 from POST | Concurrent duplicate insert | Log: `Data integrity violation` | Retry the request |
| 500 | Unexpected bug | Search logs by `correlationId` | See section 4 |

## 2. Database problems

```bash
kubectl get pods -l app=mysql ; kubectl get pvc
kubectl logs mysql-0 --tail=100
kubectl exec -it mysql-0 -- mysqladmin ping -uroot -p          # "mysqld is alive"
kubectl exec -it mysql-0 -- mysql -ucountryapp -p countrydb -e "show tables;"
# DNS / connectivity from an app pod
kubectl exec -it deploy/country-info-service -- sh -c "getent hosts mysql"
```
- `Access denied for user`: the Secret changed after MySQL first initialised its volume. MySQL only reads
  `MYSQL_USER/PASSWORD` on the **first** boot of an empty volume. Fix the password inside MySQL, or delete the PVC
  (data loss) and redeploy.
- `Unknown database`: `MYSQL_DATABASE` was not applied on first boot (same cause as above).
- Connection pool exhausted (`Connection is not available, request timed out after 5000ms`): look at
  `hikaricp_connections_active` / `_pending` in `/actuator/metrics`; increase `DB_POOL_SIZE` or scale pods (mind MySQL `max_connections`).

## 3. SOAP provider problems

```bash
kubectl port-forward svc/country-info-service 8080:80
curl localhost:8080/actuator/health | grep -i -A5 circuit           # state CLOSED / OPEN / HALF_OPEN
curl localhost:8080/actuator/metrics/soap.client.requests           # count, latency by operation/outcome
curl "localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state"
# Can the pod reach the provider at all?
kubectl exec -it deploy/country-info-service -- sh -c \
  "curl -s -o /dev/null -w '%{http_code}\n' http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL"
```
(If `curl` is missing in the slim image use `kubectl debug -it <pod> --image=curlimages/curl --target=app -- sh`.)

- `UnknownHostException` / timeouts: cluster egress or DNS is blocked. Check `kubectl run tmp --rm -it --image=busybox -- nslookup webservices.oorsprong.org`, proxy settings, NetworkPolicies, corporate firewall.
- Circuit `OPEN`: the provider failed >= 50% of the last 10 calls. It retries automatically after 30 s (HALF_OPEN probes).
  Cached countries keep working while it is open.
- Different endpoint (e.g. a mock): change `SOAP_ENDPOINT_URL` in the ConfigMap and `rollout restart`.
- Slow responses: raise `SOAP_READ_TIMEOUT`; watch `soap.client.requests` max latency.

## 4. Tracing one failing request through the logs

1. Every response has an `X-Correlation-Id` header and every error body has `correlationId`.
2. Find it across all replicas:
   ```bash
   kubectl logs -l app=country-info-service --tail=-1 --prefix | grep <correlationId>
   ```
3. In the `prod` profile logs are JSON (ECS): filter with `jq`:
   ```bash
   kubectl logs <pod> | jq -c 'select(.["log.level"]=="ERROR")'
   kubectl logs <pod> | jq -c 'select(.correlationId=="<id>")'   # field name may appear as .mdc.correlationId depending on version
   ```
4. Typical sequence for a healthy POST: `HTTP POST ...` -> `SOAP call operation=CountryISOCode outcome=success` ->
   `SOAP call operation=FullCountryInfo outcome=success` -> `Country KE created (id=..)`.

## 5. Performance / scaling

```bash
kubectl top pods ; kubectl get hpa -w
kubectl describe hpa country-info-service            # events show why it did / did not scale
```
Useful metrics: `http_server_requests_seconds` (latency/err rate), `jvm_memory_used_bytes`, `hikaricp_connections_*`,
`resilience4j_circuitbreaker_*`, `cache_gets_total{result="hit|miss"}`.

## 6. Bad release? Roll back

```bash
kubectl rollout undo deployment/country-info-service
kubectl rollout status deployment/country-info-service
```
`maxUnavailable: 0` means a broken new version never receives traffic (readiness fails) while old pods keep serving.

## 7. Last-resort debugging

```bash
kubectl exec -it <pod> -- sh                              # shell in the container
kubectl port-forward <pod> 8080:8080                      # talk to one specific pod
kubectl debug -it <pod> --image=busybox --target=app      # ephemeral debug container
kubectl delete pod <pod>                                  # Deployment recreates it
```
