package com.ips.mock.controller;

import com.ips.mock.request.CreditTransferRequest;
import com.ips.mock.response.DefaultResponse;
import com.ips.mock.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/credit-transfer")
    public ResponseEntity<DefaultResponse> creditTransfer(@RequestBody  CreditTransferRequest request){
        return transactionService.processTransaction(request);
    }
}
