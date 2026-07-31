package com.example.cdcsync.repository.elasticsearch;

import com.example.cdcsync.document.InventoryIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryIndexRepository extends ElasticsearchRepository<InventoryIndex, String> {
}
