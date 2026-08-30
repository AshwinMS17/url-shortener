# Deploying url-shortener

Container image + Kubernetes manifests for running the service on EKS with
DynamoDB as the store. Placeholders are written as `<LIKE_THIS>`.

## 1. Build and push the image

```bash
# from the repo root
docker build -t url-shortener:0.1.0 .

# quick local smoke test
docker run --rm -p 8080:8080 \
  -e LOCAL_DEV_API_KEY=dev-secret-change-me \
  url-shortener:0.1.0
curl -s localhost:8080/actuator/health          # {"status":"UP"}
```

Push to ECR:

```bash
ACCOUNT_ID=<ACCOUNT_ID>; REGION=us-east-1
aws ecr create-repository --repository-name url-shortener --region "$REGION" || true
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

docker tag url-shortener:0.1.0 "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/url-shortener:0.1.0"
docker push "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/url-shortener:0.1.0"
```

Then set that image ref in `deployment.yaml` (or via the `images:` block in
`kustomization.yaml`).

## 2. Provision the DynamoDB table

The app does **not** create the table in real environments
(`DYNAMODB_CREATE_TABLE=false`). Create it once - Terraform/CloudFormation in a
real setup, or by hand:

```bash
aws dynamodb create-table \
  --table-name url-shortener \
  --attribute-definitions AttributeName=code,AttributeType=S \
  --key-schema AttributeName=code,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

Partition key is `code` (string); no sort key, no GSIs yet.

## 3. IAM role for the pods (IRSA)

The pods reach DynamoDB by assuming an IAM role bound to their ServiceAccount -
no static keys. The app's `DefaultCredentialsProvider` picks up the projected
web-identity token automatically.

```bash
cat > dynamo-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem"],
    "Resource": "arn:aws:dynamodb:us-east-1:<ACCOUNT_ID>:table/url-shortener"
  }]
}
JSON
aws iam create-policy --policy-name url-shortener-dynamodb \
  --policy-document file://dynamo-policy.json

# Creates the IAM role, wires the trust policy to the cluster OIDC provider,
# and annotates the k8s ServiceAccount in one step:
eksctl create iamserviceaccount \
  --cluster <CLUSTER_NAME> --namespace url-shortener --name url-shortener \
  --attach-policy-arn arn:aws:iam::<ACCOUNT_ID>:policy/url-shortener-dynamodb \
  --approve
```

If you let `eksctl` manage the ServiceAccount, drop `serviceaccount.yaml` from
`kustomization.yaml`; otherwise put the role ARN into its annotation.

## 4. Secrets

`secret.example.yaml` is a template. Create the real Secret out-of-band:

```bash
kubectl create secret generic url-shortener-secrets -n url-shortener \
  --from-literal=APP_APIKEYS_ACME='<real-secret>' \
  --from-literal=APP_APIKEYS_PARTNER1='<real-secret>'
```

Each `APP_APIKEYS_<OWNER>` binds to `app.api-keys.<owner>`.

## 5. Apply

```bash
kubectl apply -k k8s/
kubectl -n url-shortener rollout status deploy/url-shortener
```

`kubectl apply -k` skips `secret.example.yaml` (not in `kustomization.yaml`).

## 6. Verify

```bash
kubectl -n url-shortener get pods,svc,ingress,hpa
kubectl -n url-shortener port-forward svc/url-shortener 8080:80

curl -s localhost:8080/actuator/health
curl -s -XPOST localhost:8080/shorten \
  -H 'Content-Type: application/json' -H 'X-API-Key: <real-secret>' \
  -d '{"longUrl":"https://example.com/x"}'
```

## What's here

| File | Purpose |
|------|---------|
| `namespace.yaml` | `url-shortener` namespace |
| `serviceaccount.yaml` | SA carrying the IRSA role annotation |
| `configmap.yaml` | non-secret env (profile, region, table, base URL) |
| `secret.example.yaml` | template for the API-key Secret (not applied by kustomize) |
| `deployment.yaml` | 2 replicas, non-root, read-only rootfs, start/ready/live probes |
| `service.yaml` | ClusterIP :80 → :8080 |
| `ingress.yaml` | nginx by default; ALB annotations noted inline |
| `hpa.yaml` | CPU-based autoscale 2→6 |
| `kustomization.yaml` | applies all of the above |
