# search-catalog-indexer

Spring Batch application that downloads the Amazon Products 2023 dataset from Hugging Face,
generates semantic embeddings using all-MiniLM-L6-v2, and bulk indexes products into Elasticsearch.

## How it works

```
HuggingFace Parquet (~117K products)
  → HuggingFaceParquetReader (reads row by row)
    → ProductTransformer (cleans fields, builds suggest payload)
      → EmbeddingProcessor (generates 384-dim vector via DJL)
        → ElasticsearchBulkWriter (bulk index 500 docs per request)
```

## Prerequisites

- Java 25
- Maven 3.9+
- Elasticsearch running (see search-infra)

## Quick start

```bash
# Index 500 products (quick test)
make run-small

# Index 50K products (full run, ~15-20 min)
make run
```

First run downloads the dataset (~150MB Parquet file from Hugging Face) and
the all-MiniLM-L6-v2 model (~80MB from DJL model zoo). Both are cached locally.

## Configuration

All settings are in `src/main/resources/application.yml` and overridable via env vars:

| Env Var | Default | Description |
|---|---|---|
| `ES_HOST` | localhost | Elasticsearch host |
| `ES_PORT` | 9200 | Elasticsearch port |
| `ES_PASSWORD` | changeme | Elasticsearch password |
| `ES_INDEX` | products | Target index name |
| `MAX_RECORDS` | 50000 | Max products to index |
| `CHUNK_SIZE` | 500 | Docs per bulk request |
| `SKIP_DOWNLOAD` | false | Use cached Parquet file |
| `DATASET_PATH` | /tmp/amazon-products.parquet | Local dataset path |

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

## Verify indexing worked

```bash
# Doc count
make verify-count

# Sample document
make verify-sample

# Confirm vectors are populated
make verify-vector
```

## Dataset

**milistu/AMAZON-Products-2023** from Hugging Face
- 117K Amazon products
- Fields: title, description, category, brand, price, rating, images
- License: MIT
- URL: https://huggingface.co/datasets/milistu/AMAZON-Products-2023

