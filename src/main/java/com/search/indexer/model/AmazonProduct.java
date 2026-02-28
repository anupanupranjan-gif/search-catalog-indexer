package com.search.indexer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AmazonProduct {

    @JsonProperty("parent_asin")
    private String parentAsin;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private Object description;

    @JsonProperty("main_category")
    private String mainCategory;

    @JsonProperty("store")
    private String store;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("average_rating")
    private Double averageRating;

    @JsonProperty("rating_number")
    private Integer ratingNumber;

    @JsonProperty("images")
    private Object images;

    @JsonProperty("categories")
    private Object categories;

    @JsonProperty("features")
    private Object features;

    @JsonProperty("details")
    private Object details;

    public AmazonProduct() {}

    public String getParentAsin() { return parentAsin; }
    public void setParentAsin(String parentAsin) { this.parentAsin = parentAsin; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Object getDescription() { return description; }
    public void setDescription(Object description) { this.description = description; }

    public String getMainCategory() { return mainCategory; }
    public void setMainCategory(String mainCategory) { this.mainCategory = mainCategory; }

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getRatingNumber() { return ratingNumber; }
    public void setRatingNumber(Integer ratingNumber) { this.ratingNumber = ratingNumber; }

    public Object getImages() { return images; }
    public void setImages(Object images) { this.images = images; }

    public Object getCategories() { return categories; }
    public void setCategories(Object categories) { this.categories = categories; }

    public Object getFeatures() { return features; }
    public void setFeatures(Object features) { this.features = features; }

    public Object getDetails() { return details; }
    public void setDetails(Object details) { this.details = details; }
}

