package com.example.transactions.controller;

import com.example.transactions.dto.*;
import com.example.transactions.service.TransactionProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TransactionController {
	private final TransactionProcessingService service;

	public TransactionController(TransactionProcessingService s) {
		service = s;
	}

	@PostMapping("/transactions")
	public ResponseEntity<TransactionResponse> submit(@Valid @RequestBody TransactionRequest request) {
		return ResponseEntity.accepted().body(service.submit(request));
	}

	@PostMapping("/transactions/process")
	public Map<String, Object> process() {
		return service.processBatch();
	}

	@GetMapping("/transactions")
	public List<TransactionResponse> list() {
		return service.list();
	}

	@GetMapping("/transactions/{id}")
	public ResponseEntity<TransactionResponse> get(@PathVariable String id) {
		return service.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/summary")
	public Map<String, Object> summary() {
		return service.summary();
	}

	@GetMapping("/accounts/{id}")
	public Map<String, Object> account(@PathVariable String id) {
		return service.account(id);
	}
}
