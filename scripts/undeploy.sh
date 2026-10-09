#!/usr/bin/env bash
# Removes everything (including the MySQL volume!) by deleting the namespace.
set -euo pipefail
kubectl delete namespace "${NAMESPACE:-country-info}" --ignore-not-found
