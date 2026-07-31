package com.example.cdcsync.controller;

import com.example.cdcsync.model.*;
import com.example.cdcsync.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync/data")
public class DataIngestionController {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;

    public DataIngestionController(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            InventoryRepository inventoryRepository,
            PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/customers")
    public ResponseEntity<Map<String, Object>> createCustomer(@RequestBody Customer customer) {
        Customer saved = customerRepository.save(customer);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Customer written to PostgreSQL. CDC pipeline triggered.");
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody Product product) {
        Product saved = productRepository.save(product);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Product written to PostgreSQL. CDC pipeline triggered.");
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Order order) {
        // Validate customer exists
        if (!customerRepository.existsById(order.getCustomerId())) {
            throw new IllegalArgumentException("Customer not found with ID: " + order.getCustomerId());
        }
        Order saved = orderRepository.save(order);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Order written to PostgreSQL. CDC pipeline triggered.");
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/inventory")
    public ResponseEntity<Map<String, Object>> createInventory(@RequestBody Inventory inventory) {
        // Validate product exists
        if (!productRepository.existsById(inventory.getProductId())) {
            throw new IllegalArgumentException("Product not found with ID: " + inventory.getProductId());
        }
        Inventory saved = inventoryRepository.save(inventory);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Inventory written to PostgreSQL. CDC pipeline triggered.");
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody Payment payment) {
        // Validate order exists
        if (!orderRepository.existsById(payment.getOrderId())) {
            throw new IllegalArgumentException("Order not found with ID: " + payment.getOrderId());
        }
        Payment saved = paymentRepository.save(payment);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Payment written to PostgreSQL. CDC pipeline triggered.");
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }
}
