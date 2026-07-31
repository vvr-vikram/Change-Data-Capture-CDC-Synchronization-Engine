package com.example.cdcsync.repository.elasticsearch;

import com.example.cdcsync.document.CustomerIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerIndexRepository extends ElasticsearchRepository<CustomerIndex, String> {
}
