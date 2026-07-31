package com.example.cdcsync.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.math.BigDecimal;

@Document(indexName = "orders")
public class OrderIndex {
    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long customerId;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Double)
    private BigDecimal totalAmount;

    public OrderIndex() {}

    public OrderIndex(String id, Long customerId, String status, BigDecimal totalAmount) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public static OrderIndexBuilder builder() {
        return new OrderIndexBuilder();
    }

    public static class OrderIndexBuilder {
        private String id;
        private Long customerId;
        private String status;
        private BigDecimal totalAmount;

        public OrderIndexBuilder id(String id) {
            this.id = id;
            return this;
        }
        public OrderIndexBuilder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }
        public OrderIndexBuilder status(String status) {
            this.status = status;
            return this;
        }
        public OrderIndexBuilder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }
        public OrderIndex build() {
            return new OrderIndex(id, customerId, status, totalAmount);
        }
    }
}
