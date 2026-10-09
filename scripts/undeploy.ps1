param([string]$Namespace = "country-info")
kubectl delete namespace $Namespace --ignore-not-found
