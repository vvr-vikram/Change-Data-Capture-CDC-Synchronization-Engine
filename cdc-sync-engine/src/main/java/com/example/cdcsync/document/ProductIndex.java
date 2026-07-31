package com.example.cdcsync.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.math.BigDecimal;

@Document(indexName = "products")
public class ProductIndex {
    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String sku;

    public ProductIndex() {}

    public ProductIndex(String id, String name, String description, BigDecimal price, String sku) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.sku = sku;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public static ProductIndexBuilder builder() {
        return new ProductIndexBuilder();
    }

    public static class ProductIndexBuilder {
        private String id;
        private String name;
        private String description;
        private BigDecimal price;
        private String sku;

        public ProductIndexBuilder id(String id) {
            this.id = id;
            return this;
        }
        public ProductIndexBuilder name(String name) {
            this.name = name;
            return this;
        }
        public ProductIndexBuilder description(String description) {
            this.description = description;
            return this;
        }
        public ProductIndexBuilder price(BigDecimal price) {
            this.price = price;
            return this;
        }
        public ProductIndexBuilder sku(String sku) {
            this.sku = sku;
            return this;
        }
        public ProductIndex build() {
            return new ProductIndex(id, name, description, price, sku);
        }
    }
}
