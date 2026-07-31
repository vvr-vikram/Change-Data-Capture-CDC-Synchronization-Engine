package com.example.cdcsync.repository.elasticsearch;

import com.example.cdcsync.document.OrderIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderIndexRepository extends ElasticsearchRepository<OrderIndex, String> {
}
