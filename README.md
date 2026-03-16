# search-catalog-indexer

Spring Batch application that downloads the Amazon Products 2023 dataset from Hugging Face, generates semantic embeddings using `all-MiniLM-L6-v2`, and bulk indexes products into Elasticsearch.

Built as Phase 2 of the SearchX platform.

---

## How It Works

```
HuggingFace Parquet (~453MB, 117K products)
  → HuggingFaceParquetReader (reads row by row via custom NIO InputFile)
    → ProductTransformer (cleans fields, builds ProductDocument)
      → EmbeddingProcessor (generates 384-dim vector via DJL + PyTorch)
        → ElasticsearchBulkWriter (bulk indexes 500 docs per request)
```

**Result**: 34,311 products indexed in ~35 minutes on ARM aarch64. Each document contains a normalized 384-dimension embedding vector for cosine similarity search.

---

## Prerequisites

- Java 25
- Maven 3.9+
- Elasticsearch running (see [search-infra](https://github.com/anupanupranjan-gif/search-infra))

---

## Quick Start

```bash
# Index 500 products (quick test)
make run-small

# Index 50K products (full run, ~35 min)
make run
```

The first run downloads the dataset (~453MB Parquet file from Hugging Face) and the `all-MiniLM-L6-v2` model (~80MB from DJL model zoo). Both are cached locally after the first run.

---

## Configuration

All settings are in `src/main/resources/application.yml` and overridable via env vars:

| Env Var | Default | Description |
|---|---|---|
| `ES_HOST` | `localhost` | Elasticsearch host |
| `ES_PORT` | `9200` | Elasticsearch port |
| `ES_USERNAME` | `elastic` | Elasticsearch username |
| `ES_PASSWORD` | `changeme` | Elasticsearch password |
| `ES_SCHEME` | `http` | `http` or `https` |
| `ES_INDEX` | `products` | Target index name |
| `MAX_RECORDS` | `50000` | Max products to index |
| `CHUNK_SIZE` | `500` | Docs per bulk request |
| `SKIP_DOWNLOAD` | `false` | Use cached Parquet file |
| `DATASET_PATH` | `/tmp/amazon-products.parquet` | Local dataset path |

---

## Running Against ECK (In-Cluster Elasticsearch)

When Elasticsearch is running under ECK in Kind, it uses a self-signed TLS certificate. The indexer's `ElasticsearchConfig` uses a trust-all `SSLContext` with `NoopHostnameVerifier` to connect:

```bash
DATASET_PATH=/tmp/amazon-products.parquet \
ES_HOST=localhost \
ES_PORT=9200 \
ES_USERNAME=elastic \
ES_PASSWORD=<eck-password> \
ES_SCHEME=https \
ES_INDEX=products \
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dindexer.dataset.skip-download=true"
```

Get the ECK password:
```bash
kubectl get secret searchx-es-elastic-user -n elasticsearch \
  -o jsonpath='{.data.elastic}' | base64 -d
```

Port-forward Elasticsearch before running locally:
```bash
kubectl port-forward svc/searchx-es-http -n elasticsearch 9200:9200 &
```

---

## Running on Kubernetes

```bash
# Build and push image
make docker-build docker-push

# Run as a one-shot Job
make k8s-run

# Watch logs
make k8s-logs

# Verify results
make verify-count
make verify-vector
```

---

## Verify Indexing Worked

```bash
# Doc count (should be ~34,311)
make verify-count

# Sample document
make verify-sample

# Confirm vectors are populated
make verify-vector
```

---

## Embedding Model Details

| Property | Value |
|---|---|
| Model | `sentence-transformers/all-MiniLM-L6-v2` |
| Source | DJL model zoo (`djl://ai.djl.huggingface.pytorch/...`) |
| Output | `pooler_output` — shape `[batch, 384]` (already pooled) |
| Normalization | L2 (unit vectors for cosine similarity) |
| Max input tokens | 256 (truncated) |
| Input format | `input_ids` + `attention_mask` (no `token_type_ids`) |

The model returns a 2D `[batch, 384]` tensor directly from `pooler_output` — no mean pooling needed. Vectors are L2-normalized to unit length, matching the normalization applied at query time in search-api.

---

## Performance

| Metric | Value |
|---|---|
| Documents indexed | 34,311 |
| Total time | ~35 minutes |
| Throughput | ~20 docs/sec (CPU-bound by embedding) |
| Chunk size | 500 docs per bulk request |
| Vector dimensions | 384 |
| Vector magnitude | 1.0 (L2 normalized) |
| Hardware | ARM aarch64 |
| Dataset size | 453MB Parquet file |

---

## Dataset

**milistu/AMAZON-Products-2023** from Hugging Face

- 117K Amazon products (34,311 successfully indexed after filtering)
- Fields: title, description, category, brand, price, rating, images
- License: MIT
- URL: https://huggingface.co/datasets/milistu/AMAZON-Products-2023

---

## Stack

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.3 |
| Spring Batch | 6.0.2 |
| Elasticsearch Java Client | 8.12.2 |
| DJL | 0.28.0 |
| PyTorch (via DJL) | 2.2.2 |
| Parquet | 1.13.1 |
| Hadoop | 3.4.1 |

---

## Part of SearchX

This repo is one component of the SearchX platform:

- [search-api](https://github.com/anupanupranjan-gif/search-api) — Spring Boot hybrid search service (BM25 + vector)
- [search-ui](https://github.com/anupanupranjan-gif/search-ui) — React eCommerce frontend
- [prometheus-mcp](https://github.com/anupanupranjan-gif/prometheus-mcp) — Prometheus MCP server
- [observability-console](https://github.com/anupanupranjan-gif/observability-console) — AI-powered ops console
- [search-infra](https://github.com/anupanupranjan-gif/search-infra) — Kubernetes manifests, Helm charts, ArgoCD, Terraform
