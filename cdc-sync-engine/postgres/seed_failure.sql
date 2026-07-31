INSERT INTO failed_events (event_id, topic, partition_id, offset_val, payload, error_message, resolved) 
VALUES (
    'evt:customers:999999:1690000000000', 
    'cdc.public.customers.DLT', 
    0, 
    15, 
    '{"payload": {"op": "c", "before": null, "after": {"id": 105, "name": "Bala Nair", "email": "balanair@example.com", "phone": "987654"}, "source": {"table": "customers", "lsn": 999999, "ts_ms": 1690000000000}}}', 
    'Simulated connection timeout during Elasticsearch indexing', 
    false
);
