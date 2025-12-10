package com.ips.mock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.mock.dto.Account;
import com.ips.mock.request.CreditTransferRequestOld;
import com.ips.mock.response.DefaultResponse;
import com.ips.mock.service.storage.AccountStorageService;
import com.ips.mock.service.storage.BankStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TransactionServiceOld {
    private final AccountStorageService accountStorageService;
    private final BankStorageService bankStorageService;
    private final ObjectMapper objectMapper= new ObjectMapper();

    public TransactionServiceOld(AccountStorageService accountStorageService, BankStorageService bankStorageService) {
        this.accountStorageService = accountStorageService;
        this.bankStorageService = bankStorageService;
    }

    public ResponseEntity<DefaultResponse> processTransaction(CreditTransferRequestOld request){
        try {
            log.info("[STEP 001] IPS RECEIVES PACS.008 CREDIT TRANSFER REQUEST");

            log.info("[STEP 002] PERFORMS VALIDATIONS (MESSAGE FORMAT, LIQUIDITY, ETC) AND HOLDS THE AMOUNT IN DEBTOR AGENT");
            Account payer = accountStorageService.getAccountByAccountNumberAndBankCode(request.getInitiatorAccountNumber(), request.getInitiator());
            if (payer == null) {
                DefaultResponse response = DefaultResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message("Initiator account not found")
                        .messageType("camt.004")
                        .messageStatus("RJCT")
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            //debit liquidity from debtor agent
            bankStorageService.updateLiquidityBalance(request.getInitiator(), request.getAmount(),"DEBIT");

            log.info("[STEP 003] FORWARDS THE PACS.008 REQUEST TO THE CREDITOR AGENT");
            log.info("[STEP 004] CREDITOR AGENT VALIDATES THE PACS.008 AND VERIFIES THE PAYEE DETAILS");

            Account payee = accountStorageService.getAccountByAccountNumberAndBankCode(request.getRecipientAccountNumber(), request.getRecipient());
            log.info("payee details: {}",objectMapper.writeValueAsString(payee));
            if (payee == null) {
                DefaultResponse response = DefaultResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message("Payee account not found")
                        .messageType("pacs.002")
                        .messageStatus("RJCT")
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            log.info("[STEP 005] CREDITOR AGENT SENDS PACS.002");
            log.info("[STEP 006] IPS VALIDATES THE PACS.002 AND CLEARS THE TRANSACTION ACROSS PARTICIPANTS ACCOUNTS");

            bankStorageService.updateLiquidityBalance(request.getRecipient(), request.getAmount(), "CREDIT");

            log.info("[STEP 007] IPS SENDS PACS.002 DEBTOR AGENT AND DEBTOR AGENT DEBITS THE FUNDS FROM PAYER");
            //debit funds from payer account
            accountStorageService.updateAccountBalance(payer, request.getAmount(), "DEBIT");

            log.info("[STEP 008] IPS SENDS PACS.002 DEBTOR CREDITOR AND CREDITOR AGENT CREDITS THE FUNDS TO PAYER");
            //credit funds from payee account
            log.info("payee details: {}",objectMapper.writeValueAsString(payee));
            accountStorageService.updateAccountBalance(payee, request.getAmount(), "CREDIT");

            DefaultResponse response = DefaultResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Transaction Successful")
                    .messageType("pacs.002")
                    .messageStatus("ACCP")
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (JsonProcessingException e) {
            log.error("Error processing transaction: {}", e.getMessage());
            DefaultResponse response = DefaultResponse.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .message("Internal Server Error")
                    .messageType("pacs.002")
                    .messageStatus("RJCT")
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
