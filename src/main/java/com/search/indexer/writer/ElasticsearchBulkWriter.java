package com.search.indexer.writer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.search.indexer.model.ProductDocument;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ElasticsearchBulkWriter implements ItemWriter<ProductDocument> {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchBulkWriter.class);

    private final ElasticsearchClient esClient;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer bulkTimer;
    private final AtomicLong totalIndexed = new AtomicLong(0);

    @Value("${elasticsearch.index-name:products}")
    private String indexName;

    @Value("${indexer.bulk.max-retries:3}")
    private int maxRetries;

    public ElasticsearchBulkWriter(ElasticsearchClient esClient, MeterRegistry meterRegistry) {
        this.esClient       = esClient;
        this.successCounter = Counter.builder("indexer.documents.success").register(meterRegistry);
        this.failureCounter = Counter.builder("indexer.documents.failure").register(meterRegistry);
        this.bulkTimer      = Timer.builder("indexer.bulk.duration").register(meterRegistry);
    }

    @Override
    public void write(Chunk<? extends ProductDocument> chunk) throws Exception {
        List<? extends ProductDocument> items = chunk.getItems();
        if (items.isEmpty()) return;

        bulkTimer.record(() -> {
            try { executeBulkWithRetry(items); }
            catch (Exception e) { throw new RuntimeException("Bulk indexing failed", e); }
        });
    }

    private void executeBulkWithRetry(List<? extends ProductDocument> items) throws Exception {
        int attempt = 0;
        Exception last = null;
        while (attempt < maxRetries) {
            try {
                handleBulkResponse(executeBulk(items), items);
                return;
            } catch (Exception e) {
                attempt++;
                last = e;
                log.warn("Bulk attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) Thread.sleep(1000L * attempt);
            }
        }
        throw new Exception("Bulk failed after " + maxRetries + " attempts", last);
    }

    private BulkResponse executeBulk(List<? extends ProductDocument> items) throws Exception {
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (ProductDocument product : items) {
            bulk.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(product.getProductId())
                    .document(product)));
        }
        return esClient.bulk(bulk.build());
    }

    private void handleBulkResponse(BulkResponse response, List<? extends ProductDocument> items) {
        if (response.errors()) {
            long failed = response.items().stream()
                    .filter(i -> i.error() != null)
                    .peek(i -> {
                        failureCounter.increment();
                        log.warn("Failed to index {}: {}", i.id(), i.error().reason());
                    })
                    .count();
            long success = items.size() - failed;
            successCounter.increment(success);
            log.warn("Bulk done. Success: {}, Failed: {}, Total: {}",
                    success, failed, totalIndexed.addAndGet(success));
        } else {
            successCounter.increment(items.size());
            log.info("Bulk indexed {}. Total: {}", items.size(), totalIndexed.addAndGet(items.size()));
        }
    }
}

