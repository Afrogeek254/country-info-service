<#
 Build the image and deploy everything to Kubernetes (Windows PowerShell).
 Examples
   .\scripts\deploy.ps1
   .\scripts\deploy.ps1 -Cluster minikube
   .\scripts\deploy.ps1 -Registry docker.io/myuser -ImageTag 1.0.1
#>
param(
  [string]$Namespace = "country-info",
  [string]$ImageName = "country-info-service",
  [string]$ImageTag  = "1.0.0",
  [string]$Registry  = "",
  [ValidateSet("", "minikube", "kind")][string]$Cluster = ""
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

function Run($cmd) { Write-Host ">> $cmd"; Invoke-Expression $cmd; if ($LASTEXITCODE -ne 0) { throw "Command failed: $cmd" } }

$fullImage = if ($Registry) { "$Registry/${ImageName}:$ImageTag" } else { "${ImageName}:$ImageTag" }

Run "docker build -t $fullImage ."
if ($Registry)                  { Run "docker push $fullImage" }
elseif ($Cluster -eq "minikube") { Run "minikube image load $fullImage" }
elseif ($Cluster -eq "kind")     { Run "kind load docker-image $fullImage" }

Run "kubectl apply -f k8s/namespace.yaml"
Run "kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml"
Run "kubectl apply -f k8s/mysql.yaml"
Run "kubectl -n $Namespace rollout status statefulset/mysql --timeout=300s"
Run "kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/hpa.yaml -f k8s/pdb.yaml"
try { Run "kubectl apply -f k8s/ingress.yaml" } catch { Write-Warning "Ingress not applied (no ingress controller?). Use port-forward." }
Run "kubectl -n $Namespace set image deployment/country-info-service app=$fullImage"
Run "kubectl -n $Namespace rollout status deployment/country-info-service --timeout=300s"
Run "kubectl -n $Namespace get pods,svc,hpa"

Write-Host "`nTest with: kubectl -n $Namespace port-forward svc/country-info-service 8080:80"
