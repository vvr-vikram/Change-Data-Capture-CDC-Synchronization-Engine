package com.example.cdcsync.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "inventory")
public class InventoryIndex {
    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long productId;

    @Field(type = FieldType.Integer)
    private Integer quantity;

    @Field(type = FieldType.Keyword)
    private String location;

    public InventoryIndex() {}

    public InventoryIndex(String id, Long productId, Integer quantity, String location) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.location = location;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public static InventoryIndexBuilder builder() {
        return new InventoryIndexBuilder();
    }

    public static class InventoryIndexBuilder {
        private String id;
        private Long productId;
        private Integer quantity;
        private String location;

        public InventoryIndexBuilder id(String id) {
            this.id = id;
            return this;
        }
        public InventoryIndexBuilder productId(Long productId) {
            this.productId = productId;
            return this;
        }
        public InventoryIndexBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }
        public InventoryIndexBuilder location(String location) {
            this.location = location;
            return this;
        }
        public InventoryIndex build() {
            return new InventoryIndex(id, productId, quantity, location);
        }
    }
}
