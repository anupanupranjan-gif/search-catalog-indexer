package com.search.indexer.processor;

import ai.djl.inference.Predictor;
import com.search.indexer.model.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProcessor implements ItemProcessor<ProductDocument, ProductDocument> {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProcessor.class);
    private final Predictor<String[], float[][]> embeddingPredictor;
    private boolean debugged = false;

    public EmbeddingProcessor(Predictor<String[], float[][]> embeddingPredictor) {
        this.embeddingPredictor = embeddingPredictor;
    }

    @Override
    public ProductDocument process(ProductDocument item) throws Exception {
        if (item == null) return null;
        String text = buildEmbeddingText(item);
        try {
            float[][] vectors = embeddingPredictor.predict(new String[]{text});

            // Debug first result
            if (!debugged) {
                debugged = true;
                float[] v = vectors[0];
                log.info("DEBUG embedding: length={}, first5=[{},{},{},{},{}], magnitude={}",
                    v.length, v[0], v[1], v[2], v[3], v[4],
                    magnitude(v));
            }

            float[] vector = vectors[0];
            if (magnitude(vector) < 1e-6) {
                log.warn("Zero vector for {}, skipping embedding", item.getProductId());
                item.setProductVector(new float[384]);
            } else {
                item.setProductVector(vector);
            }
        } catch (Exception e) {
            log.warn("Embedding failed for {}: {}", item.getProductId(), e.getMessage());
            item.setProductVector(new float[384]);
        }
        return item;
    }

    private float magnitude(float[] v) {
        float sum = 0;
        for (float f : v) sum += f * f;
        return (float) Math.sqrt(sum);
    }

    private String buildEmbeddingText(ProductDocument product) {
        StringBuilder sb = new StringBuilder();
        if (product.getTitle() != null) sb.append(product.getTitle());
        if (product.getDescription() != null && !product.getDescription().isBlank())
            sb.append(" ").append(product.getDescription());
        String text = sb.toString().trim();
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}
