package com.ips.mock.controller;

import com.ips.mock.request.AccountVerificationRequest;
import com.ips.mock.request.CreditTransferRequest;
import com.ips.mock.service.TransactionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iso")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Account verification – simulate camt.004 account response.
     * Request is JSON, response is XML.
     */
    @PostMapping(
            value = "/account-verification",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> verifyAccount(@RequestBody AccountVerificationRequest request) {
        return transactionService.verifyAccount(request);
    }

    /**
     * Credit transfer – simulate pacs.008 in / pacs.002 out.
     * Request is JSON, response is XML.
     */
    @PostMapping(
            value = "/credit-transfer",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> creditTransfer(@RequestBody CreditTransferRequest request) {
        return transactionService.processCreditTransfer(request);
    }

    /**
     * Balance inquiry – request is ISO-style XML (camt.003-like),
     * response is ISO-style XML (camt.004 with balance).
     */
    @PostMapping(
            value = "/balance-inquiry",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> balanceInquiry(@RequestBody AccountVerificationRequest dto) {
        return transactionService.balanceInquiryFromIsoXml(dto);
    }

}