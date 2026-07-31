package com.example.cdcsync.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "customers")
public class CustomerIndex {
    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String phone;

    public CustomerIndex() {}

    public CustomerIndex(String id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public static CustomerIndexBuilder builder() {
        return new CustomerIndexBuilder();
    }

    public static class CustomerIndexBuilder {
        private String id;
        private String name;
        private String email;
        private String phone;

        public CustomerIndexBuilder id(String id) {
            this.id = id;
            return this;
        }
        public CustomerIndexBuilder name(String name) {
            this.name = name;
            return this;
        }
        public CustomerIndexBuilder email(String email) {
            this.email = email;
            return this;
        }
        public CustomerIndexBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }
        public CustomerIndex build() {
            return new CustomerIndex(id, name, email, phone);
        }
    }
}
