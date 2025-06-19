# YaCy Helm Chart

<p align="center">
  <img src="https://yacy.net/images/yacy-logo.png" width="100" alt="YaCy Logo"/>
</p>

This Helm chart deploys [YaCy](https://yacy.net/) - a distributed peer-to-peer search engine - on Kubernetes.

## Introduction

YaCy is a free, distributed search engine that can operate in two primary modes:
- **P2P Network Mode**: Connect to the global YaCy network to share search results
- **Standalone/Intranet Mode**: Run as an independent instance for private document indexing

This Helm chart simplifies deployment and management of YaCy in a Kubernetes environment, with support for various configurations and deployment scenarios.

## Prerequisites

- Kubernetes 1.12+
- Helm 3.0+
- PV provisioner support in the underlying infrastructure (for persistence)

## Installation

### Prerequisites Check

Before installing, ensure your Kubernetes cluster meets the requirements:

```bash
# Check Kubernetes version (requires 1.12+)
kubectl version --short

# Verify Helm is installed (requires 3.0+)
helm version

# Check for default StorageClass (for persistence)
kubectl get storageclass
```

### Install from Local Chart

```bash
# Navigate to the Helm chart directory
cd charts/yacy

# Install with default values
helm install my-yacy .

# OR install with custom values file
helm install my-yacy . -f my-values.yaml

# OR override specific values
helm install my-yacy . \
  --set service.type=NodePort \
  --set yacy.adminPassword=mysecurepassword \
  --set persistence.size=20Gi
```

### Install from Repository (Future)

Once this chart is published to a Helm repository, you'll be able to install it with:

```bash
# Add the repository
helm repo add yacy https://yacy.github.io/helm-charts/
helm repo update

# Install the chart
helm install my-yacy yacy/yacy
```

### Verifying Installation

```bash
# Check if the pod is running
kubectl get pods -l "app.kubernetes.io/instance=my-yacy"

# See the deployed service
kubectl get svc -l "app.kubernetes.io/instance=my-yacy"

# Read installation notes
helm status my-yacy
```

## Uninstalling the Chart

To uninstall/delete the `my-yacy` deployment:

```bash
helm uninstall my-yacy
```

## Parameters

### Common parameters

| Name                | Description                                        | Value  |
|---------------------|----------------------------------------------------|--------|
| `replicaCount`      | Number of YaCy replicas                            | `1`    |
| `nameOverride`      | String to partially override yacy.fullname template | `""`   |
| `fullnameOverride`  | String to fully override yacy.fullname template    | `""`   |

### YaCy Image parameters

| Name                    | Description                                    | Value                  |
|-------------------------|------------------------------------------------|------------------------|
| `image.useLocal`        | Use locally built image instead of repository  | `true`                 |
| `image.localRepository` | Local image name when useLocal is true         | `yacy_search_server`   |
| `image.localTag`        | Local image tag when useLocal is true          | `local`                |
| `image.repository`      | YaCy image repository when useLocal is false   | `yacy/yacy_search_server` |
| `image.tag`             | YaCy image tag when useLocal is false          | `1.930`                |
| `image.pullPolicy`      | YaCy image pull policy when useLocal is false  | `IfNotPresent`         |
| `imagePullSecrets`      | Specify docker-registry secret names           | `[]`                   |

### Architecture-specific images

| Name                | Description                                        | Value                  |
|---------------------|----------------------------------------------------|------------------------|
| `arch.amd64`        | Tag for AMD64 architecture                         | `latest`               |
| `arch.arm64`        | Tag for ARM64 architecture                         | `aarch64-latest`       |
| `arch.arm`          | Tag for ARM architecture                           | `armv7-latest`         |

### YaCy configuration parameters

| Name                      | Description                                  | Value     |
|---------------------------|----------------------------------------------|-----------|
| `env`                     | YaCy environment variables                   | `{}`      |
| `yacy.adminPassword`      | YaCy admin password                          | `"yacy"`  |
| `yacy.settings.maxMemory` | Maximum memory allocation for YaCy           | `"600m"`  |
| `yacy.settings.joinP2PNetwork` | Whether to join the YaCy P2P network    | `true`    |

### Persistence Parameters

| Name                       | Description                                     | Value         |
|----------------------------|-------------------------------------------------|---------------|
| `persistence.enabled`      | Enable persistence using PVC                    | `true`        |
| `persistence.existingClaim`| Use an existing PVC to persist data             | `""`          |
| `persistence.storageClass` | Storage class of backing PVC                    | `""`          |
| `persistence.accessMode`   | PVC Access Mode                                 | `ReadWriteOnce` |
| `persistence.size`         | Size of data volume                             | `10Gi`        |
| `persistence.annotations`  | Additional annotations for the PVC              | `{}`          |

### Exposure Parameters

| Name                       | Description                                     | Value         |
|----------------------------|-------------------------------------------------|---------------|
| `service.type`             | Kubernetes Service type                         | `ClusterIP`   |
| `service.httpPort`         | HTTP Service port                               | `8090`        |
| `service.httpsPort`        | HTTPS Service port                              | `8443`        |
| `ingress.enabled`          | Enable ingress controller resource              | `false`       |
| `ingress.className`        | IngressClass that will be used                  | `""`          |
| `ingress.hosts[0].host`    | Default host for the ingress resource           | `yacy.local`  |
| `ingress.hosts[0].paths`   | Paths for the default host                      | `[{"path":"/","pathType":"Prefix"}]` |
| `ingress.tls`              | TLS configuration                               | `[]`          |

### Other Parameters

| Name                       | Description                                     | Value         |
|----------------------------|-------------------------------------------------|---------------|
| `resources`                | CPU/Memory resource requests/limits             | `{}`          |
| `nodeSelector`             | Node labels for pod assignment                  | `{}`          |
| `tolerations`              | Tolerations for pod assignment                  | `[]`          |
| `affinity`                 | Affinity for pod assignment                     | `{}`          |

## Building and Publishing Images

YaCy can be deployed using either a locally built Docker image or an official image from Docker Hub.

### Option 1: Using Official Images from Docker Hub

The simplest approach is to use the official YaCy images:

```yaml
# values.yaml
image:
  useLocal: false
  repository: yacy/yacy_search_server
  tag: latest  # or specific version like "1.930"
  pullPolicy: IfNotPresent
```

### Option 2: Building Custom Images

For custom builds or development, you can build your own images:

#### Setting up a Local Docker Registry

1. **Start a local Docker registry**:

   ```bash
   docker run -d -p 5000:5000 --restart=always --name registry registry:2
   ```

2. **Build the YaCy Docker image**:

   ```bash
   # The Dockerfiles are in the ./docker/ directory of the YaCy project
   cd docker
   
   # Build for your platform (x86_64/amd64)
   docker build -t localhost:5000/yacy/yacy_search_server:latest -f Dockerfile ../
   
   # Push to local registry
   docker push localhost:5000/yacy/yacy_search_server:latest
   ```

3. **For multi-architecture support** (optional):

   ```bash
   # ARM64 architecture
   docker build -t localhost:5000/yacy/yacy_search_server:aarch64-latest -f Dockerfile.aarch64 ../
   docker push localhost:5000/yacy/yacy_search_server:aarch64-latest

   # ARMv7 architecture
   docker build -t localhost:5000/yacy/yacy_search_server:armv7-latest -f Dockerfile.armv7 ../
   docker push localhost:5000/yacy/yacy_search_server:armv7-latest
   ```

4. **Create a custom values file** (e.g., `local-registry-values.yaml`):

   ```yaml
   image:
     useLocal: false
     repository: localhost:5000/yacy/yacy_search_server
     tag: latest
     pullPolicy: Always
   ```

5. **Install with your custom values**:

   ```bash
   helm install my-yacy ./charts/yacy -f local-registry-values.yaml
   ```

6. **For external Kubernetes clusters**, add registry credentials:

   ```bash
   # Create a Docker registry secret
   kubectl create secret docker-registry regcred \
     --docker-server=localhost:5000 \
     --docker-username=<your-username> \
     --docker-password=<your-password>

   # Add to your values file
   imagePullSecrets:
     - name: regcred
   ```

## Deployment Examples

### Quick Start: Using a locally built image

```bash
# 1. Build the local image (from YaCy source directory)
cd docker
docker build -t yacy_search_server:local -f Dockerfile ../

# 2. Install the chart
helm install my-yacy ./charts/yacy
```

### Common Configuration Examples

#### Deployment Scenarios

##### 1. Public YaCy Node (P2P Network)

```yaml
# values.yaml
yacy:
  settings:
    joinP2PNetwork: true
    maxMemory: "1500m"
  adminPassword: "secure-password-here"

persistence:
  enabled: true
  size: 20Gi
```

##### 2. Private Intranet Search Engine

```yaml
# values.yaml
yacy:
  settings:
    joinP2PNetwork: false  # Standalone mode
    maxMemory: "2000m"
  adminPassword: "secure-password-here"
  
  # Optional: Add intranet crawler configuration
  configFile: |
    network.unit.agent=CompanySearchEngine
    network.unit.description=Internal Document Search
    crawler.http.maxDepth=5

persistence:
  enabled: true
  size: 50Gi
```

#### Exposure Options

##### 1. Basic ClusterIP (default)

```yaml
service:
  type: ClusterIP
```

##### 2. NodePort for simple external access

```yaml
service:
  type: NodePort
```

##### 3. Ingress with TLS

```yaml
ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  hosts:
    - host: search.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: yacy-tls
      hosts:
        - search.example.com
```

#### Resource Allocation

```yaml
# Recommended for production use
resources:
  limits:
    cpu: 2000m
    memory: 2Gi
  requests:
    cpu: 1000m
    memory: 1Gi

# Set YaCy memory to ~75% of container limit
yacy:
  settings:
    maxMemory: "1500m"
```

#### Architecture-Specific Deployments

##### ARM64/aarch64 Deployment

```yaml
image:
  useLocal: false
  repository: yacy/yacy_search_server
  tag: aarch64-latest
```

##### ARMv7 Deployment

```yaml
image:
  useLocal: false
  repository: yacy/yacy_search_server
  tag: armv7-latest
```

## Management

### Backup and Restore

YaCy's data is stored in `/opt/yacy_search_server/DATA` and persisted to a PVC when `persistence.enabled=true`.

#### Backup YaCy Data

```bash
# 1. Find the pod name
POD_NAME=$(kubectl get pods -l "app.kubernetes.io/instance=my-yacy" -o jsonpath="{.items[0].metadata.name}")

# 2. Create a backup (two methods)
# Option A: Direct backup to local machine
kubectl exec $POD_NAME -- tar -cf - /opt/yacy_search_server/DATA | gzip > yacy-backup-$(date +%Y%m%d).tar.gz

# Option B: Backup within pod first (if pod has sufficient storage)
kubectl exec $POD_NAME -- bash -c "cd /opt && tar -czf /tmp/yacy-backup.tar.gz yacy_search_server/DATA"
kubectl cp $POD_NAME:/tmp/yacy-backup.tar.gz ./yacy-backup-$(date +%Y%m%d).tar.gz
```

#### Restore YaCy Data

```bash
# First, stop YaCy gracefully (important for index integrity)
POD_NAME=$(kubectl get pods -l "app.kubernetes.io/instance=my-yacy" -o jsonpath="{.items[0].metadata.name}")
kubectl exec $POD_NAME -- /opt/yacy_search_server/stopYACY.sh

# Wait for YaCy to fully shut down
sleep 15

# Restore from backup 
cat yacy-backup.tar.gz | kubectl exec -i $POD_NAME -- bash -c "cd /opt && rm -rf yacy_search_server/DATA/* && tar -xzf -"

# Restart the pod
kubectl delete pod $POD_NAME
```

### Troubleshooting

#### Verify Deployment Status

```bash
# Check if pods are running
kubectl get pods -l "app.kubernetes.io/instance=my-yacy"

# Verify services
kubectl get svc -l "app.kubernetes.io/instance=my-yacy"

# Check persistent volume claims
kubectl get pvc -l "app.kubernetes.io/instance=my-yacy"
```

#### Check Logs

```bash
# Follow logs from the YaCy pod
POD_NAME=$(kubectl get pods -l "app.kubernetes.io/instance=my-yacy" -o jsonpath="{.items[0].metadata.name}")
kubectl logs -f $POD_NAME

# View YaCy application logs directly
kubectl exec $POD_NAME -- cat /opt/yacy_search_server/DATA/LOG/yacy00.log
```

#### Common Issues

1. **YaCy pod crashes immediately**: Check memory settings - container's memory limit should be higher than `yacy.settings.maxMemory`

2. **Can't access YaCy UI**: Verify the service is correctly exposed; try port-forwarding for quick access:
   ```bash
   kubectl port-forward svc/my-yacy 8090:8090
   ```

3. **Slow crawling/indexing**: Increase resource limits and YaCy's memory allocation

4. **Persistence issues**: Check that the PVC is correctly bound and has sufficient space:
   ```bash
   kubectl get pvc
   kubectl describe pvc my-yacy-data
   ```

## Contributing

Contributions to improve this chart are welcome! To contribute:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Please refer to the YaCy project's [contribution guidelines](https://github.com/yacy/yacy_search_server/blob/master/CONTRIBUTING.md) for more information.
