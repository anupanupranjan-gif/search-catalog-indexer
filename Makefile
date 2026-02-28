.PHONY: help build run test docker-build docker-push k8s-run k8s-logs k8s-clean

APP_NAME   := catalog-indexer
IMAGE_NAME := ghcr.io/YOUR_GITHUB_USERNAME/$(APP_NAME)
VERSION    := $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
ES_URL     := http://localhost:9200
ES_PASS    := changeme

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ── Local dev ─────────────────────────────────────────────────────────────────
build: ## Compile and package
	mvn clean package -DskipTests

test: ## Run tests
	mvn test

run: build ## Run indexer locally against local ES (indexes 50K products)
	java -jar target/catalog-indexer-*.jar \
		--elasticsearch.host=localhost \
		--elasticsearch.password=$(ES_PASS) \
		--indexer.dataset.max-records=50000

run-small: build ## Quick test run - index only 500 products
	java -jar target/catalog-indexer-*.jar \
		--elasticsearch.host=localhost \
		--elasticsearch.password=$(ES_PASS) \
		--indexer.dataset.max-records=500 \
		--indexer.dataset.skip-download=false

run-skip-download: build ## Run without re-downloading dataset (uses cached file)
	java -jar target/catalog-indexer-*.jar \
		--elasticsearch.host=localhost \
		--elasticsearch.password=$(ES_PASS) \
		--indexer.dataset.max-records=50000 \
		--indexer.dataset.skip-download=true

# ── Docker ────────────────────────────────────────────────────────────────────
docker-build: ## Build Docker image
	docker build -t $(IMAGE_NAME):$(VERSION) -t $(IMAGE_NAME):latest .

docker-push: docker-build ## Push image to GitHub Container Registry
	docker push $(IMAGE_NAME):$(VERSION)
	docker push $(IMAGE_NAME):latest

# ── Kubernetes ────────────────────────────────────────────────────────────────
k8s-run: ## Run indexer as Kubernetes Job
	kubectl apply -f k8s/indexer-job.yml
	@echo "Job submitted. Watch with: make k8s-logs"

k8s-logs: ## Stream logs from the indexer job
	kubectl logs -n search-app -l app=catalog-indexer -f

k8s-status: ## Check job status
	kubectl get job catalog-indexer -n search-app

k8s-clean: ## Delete the completed job
	kubectl delete job catalog-indexer -n search-app --ignore-not-found

# ── ES verification ───────────────────────────────────────────────────────────
verify-count: ## Check how many docs are in the products index
	@curl -s -u elastic:$(ES_PASS) "$(ES_URL)/products/_count" | python3 -m json.tool

verify-sample: ## Fetch a sample document to verify structure
	@curl -s -u elastic:$(ES_PASS) \
		-H "Content-Type: application/json" \
		"$(ES_URL)/products/_search" \
		-d '{"size":1,"_source":["product_id","title","category","brand","price","rating"]}' \
		| python3 -m json.tool

verify-vector: ## Check that product_vector field is populated
	@curl -s -u elastic:$(ES_PASS) \
		-H "Content-Type: application/json" \
		"$(ES_URL)/products/_search" \
		-d '{"size":1,"_source":["product_id","title","product_vector"],"query":{"exists":{"field":"product_vector"}}}' \
		| python3 -m json.tool

