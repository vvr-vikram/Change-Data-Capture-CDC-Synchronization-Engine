package com.example.cdcsync.repository.elasticsearch;

import com.example.cdcsync.document.ProductIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductIndexRepository extends ElasticsearchRepository<ProductIndex, String> {
}
