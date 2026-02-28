package com.search.indexer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public class ProductDocument {

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("category")
    private String category;

    @JsonProperty("category_path")
    private String categoryPath;

    @JsonProperty("brand")
    private String brand;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("rating")
    private Double rating;

    @JsonProperty("review_count")
    private Integer reviewCount;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("in_stock")
    private Boolean inStock;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("product_vector")
    private float[] productVector;

    @JsonProperty("suggest")
    private SuggestPayload suggest;

    private ProductDocument() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getProductId() { return productId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getCategoryPath() { return categoryPath; }
    public String getBrand() { return brand; }
    public Double getPrice() { return price; }
    public Double getRating() { return rating; }
    public Integer getReviewCount() { return reviewCount; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getInStock() { return inStock; }
    public List<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public float[] getProductVector() { return productVector; }
    public SuggestPayload getSuggest() { return suggest; }

    // ── Setters (only what's needed post-construction) ────────────────────────

    public void setProductVector(float[] productVector) { this.productVector = productVector; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ProductDocument doc = new ProductDocument();

        public Builder productId(String v)       { doc.productId = v;     return this; }
        public Builder title(String v)           { doc.title = v;         return this; }
        public Builder description(String v)     { doc.description = v;   return this; }
        public Builder category(String v)        { doc.category = v;      return this; }
        public Builder categoryPath(String v)    { doc.categoryPath = v;  return this; }
        public Builder brand(String v)           { doc.brand = v;         return this; }
        public Builder price(Double v)           { doc.price = v;         return this; }
        public Builder rating(Double v)          { doc.rating = v;        return this; }
        public Builder reviewCount(Integer v)    { doc.reviewCount = v;   return this; }
        public Builder imageUrl(String v)        { doc.imageUrl = v;      return this; }
        public Builder inStock(Boolean v)        { doc.inStock = v;       return this; }
        public Builder tags(List<String> v)      { doc.tags = v;          return this; }
        public Builder createdAt(Instant v)      { doc.createdAt = v;     return this; }
        public Builder updatedAt(Instant v)      { doc.updatedAt = v;     return this; }
        public Builder productVector(float[] v)  { doc.productVector = v; return this; }
        public Builder suggest(SuggestPayload v) { doc.suggest = v;       return this; }

        public ProductDocument build() { return doc; }
    }

    // ── SuggestPayload ────────────────────────────────────────────────────────

    public static class SuggestPayload {
        private List<String> input;
        private Integer weight;

        private SuggestPayload() {}

        public List<String> getInput() { return input; }
        public Integer getWeight() { return weight; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final SuggestPayload p = new SuggestPayload();

            public Builder input(List<String> v)  { p.input = v;  return this; }
            public Builder weight(Integer v)      { p.weight = v; return this; }

            public SuggestPayload build() { return p; }
        }
    }
}

