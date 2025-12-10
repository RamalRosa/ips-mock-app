package com.ips.mock.controller;

import com.ips.mock.request.CreditTransferRequestOld;
import com.ips.mock.response.DefaultResponse;
import com.ips.mock.service.TransactionServiceOld;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionControllerOld {
    private final TransactionServiceOld transactionServiceOld;

    public TransactionControllerOld(TransactionServiceOld transactionServiceOld) {
        this.transactionServiceOld = transactionServiceOld;
    }

    @PostMapping("/credit-transfer")
    public ResponseEntity<DefaultResponse> creditTransfer(@RequestBody CreditTransferRequestOld request){
        return transactionServiceOld.processTransaction(request);
    }
}
