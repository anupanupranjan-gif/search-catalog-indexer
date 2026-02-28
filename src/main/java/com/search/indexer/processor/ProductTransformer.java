package com.search.indexer.processor;

import com.search.indexer.model.AmazonProduct;
import com.search.indexer.model.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductTransformer implements ItemProcessor<AmazonProduct, ProductDocument> {

    private static final Logger log = LoggerFactory.getLogger(ProductTransformer.class);
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_TITLE_LENGTH = 500;

    @Override
    public ProductDocument process(AmazonProduct item) {
        try {
            String title       = cleanText(item.getTitle(), MAX_TITLE_LENGTH);
            String description = buildDescription(item);
            String category    = resolveCategory(item);
            String brand       = cleanBrand(item.getStore());
            String imageUrl    = extractFirstImageUrl(item.getImages());
            List<String> suggestInputs = buildSuggestInputs(title, brand);

            return ProductDocument.builder()
                    .productId(item.getParentAsin())
                    .title(title)
                    .description(description)
                    .category(category)
                    .categoryPath(buildCategoryPath(item))
                    .brand(brand)
                    .price(item.getPrice())
                    .rating(item.getAverageRating())
                    .reviewCount(item.getRatingNumber())
                    .imageUrl(imageUrl)
                    .inStock(true)
                    .tags(List.of(category))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .suggest(ProductDocument.SuggestPayload.builder()
                            .input(suggestInputs)
                            .weight(calculateSuggestWeight(item))
                            .build())
                    .build();

        } catch (Exception e) {
            log.warn("Failed to transform product {}: {}", item.getParentAsin(), e.getMessage());
            return null;
        }
    }

    private String buildDescription(AmazonProduct item) {
        StringBuilder sb = new StringBuilder();
        if (item.getDescription() != null) {
            String desc = item.getDescription().toString()
                    .replaceAll("^\\[|\\]$", "").replace("'", "");
            if (!desc.isBlank()) sb.append(desc);
        }
        if (item.getFeatures() != null) {
            String features = item.getFeatures().toString()
                    .replaceAll("^\\[|\\]$", "").replace("'", "");
            if (!features.isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(features);
            }
        }
        return cleanText(sb.toString(), MAX_DESCRIPTION_LENGTH);
    }

    private String resolveCategory(AmazonProduct item) {
        if (item.getMainCategory() != null && !item.getMainCategory().isBlank())
            return item.getMainCategory().trim();
        if (item.getCategories() != null) {
            String cats = item.getCategories().toString()
                    .replaceAll("^\\[|\\]$", "").trim();
            if (!cats.isBlank())
                return cats.split(",")[0].replace("'", "").trim();
        }
        return "Uncategorized";
    }

    private String buildCategoryPath(AmazonProduct item) {
        if (item.getCategories() == null) return resolveCategory(item);
        return item.getCategories().toString()
                .replaceAll("^\\[|\\]$", "")
                .replace("'", "")
                .replace(", ", " > ")
                .trim();
    }

    private String cleanBrand(String store) {
        if (store == null || store.isBlank()) return "Unknown";
        return store.trim().replaceAll("[^a-zA-Z0-9\\s&.-]", "").trim();
    }

    private String extractFirstImageUrl(Object images) {
        if (images == null) return null;
        String s = images.toString();
        int start = s.indexOf("https://");
        if (start == -1) return null;
        int end = s.indexOf("'", start);
        if (end == -1) end = s.indexOf("\"", start);
        if (end == -1) return null;
        return s.substring(start, end);
    }

    private List<String> buildSuggestInputs(String title, String brand) {
        List<String> inputs = new ArrayList<>();
        if (title != null && !title.isBlank()) inputs.add(title);
        if (brand != null && !brand.equals("Unknown")) inputs.add(brand);
        return inputs;
    }

    private int calculateSuggestWeight(AmazonProduct item) {
        if (item.getRatingNumber() == null) return 1;
        return Math.min(100, Math.max(1, item.getRatingNumber() / 100));
    }

    private String cleanText(String text, int maxLength) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > maxLength) cleaned = cleaned.substring(0, maxLength);
        return cleaned.isBlank() ? null : cleaned;
    }
}

