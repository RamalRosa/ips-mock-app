package com.ips.mock.service;

import com.ips.mock.dto.Account;
import com.ips.mock.dto.Bank;
import com.ips.mock.request.AccountVerificationRequest;
import com.ips.mock.response.AccountVerificationResponse;
import com.ips.mock.response.DefaultResponse;
import com.ips.mock.service.storage.AccountStorageService;
import com.ips.mock.service.storage.BankStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccountVerificationService {
    private final BankStorageService bankStorageService;
    private final AccountStorageService accountStorageService;

    public AccountVerificationService(BankStorageService bankStorageService, AccountStorageService accountStorageService) {
        this.bankStorageService = bankStorageService;
        this.accountStorageService = accountStorageService;
    }

    public ResponseEntity<DefaultResponse> accountVerificationAppReq(AccountVerificationRequest request) {
        try {
            Bank initiatorBank = getBankByCode(request.getInitiator());
            if (initiatorBank == null) {
                DefaultResponse response = DefaultResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message("Initiator bank not found")
                        .messageId(request.getMessageId())
                        .messageType("camt.004")
                        .messageStatus("RJCT")
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            Bank senderBank = getBankByCode(request.getSender());
            if (senderBank == null) {
                DefaultResponse response = DefaultResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message("Sender bank not found")
                        .messageId(request.getMessageId())
                        .messageType("camt.004")
                        .messageStatus("RJCT")
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            Bank receiverBank = getBankByCode(request.getReceiver());
            if (receiverBank == null) {
                DefaultResponse response = DefaultResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message("Receiver bank not found")
                        .messageId(request.getMessageId())
                        .messageType("camt.004")
                        .messageStatus("RJCT")
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            log.info("[ASSUMPTION] CALLING TO RECEIVER BANK SYSTEM TO VERIFY THE ACCOUNT DETAILS");
            Account recipeientAccount = accountStorageService.getAccountByAccountNumberAndBankCode(request.getAccountNumber(), request.getReceiver());
            log.info("[ASSUMPTION] RECEIVED RESPONSE FROM RECEIVER BANK SYSTEM");
            if (recipeientAccount == null) {
                DefaultResponse response = DefaultResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message("Account not found")
                        .messageId(request.getMessageId())
                        .messageType("camt.004")
                        .messageStatus("RJCT")
                        .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            DefaultResponse response = DefaultResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Account verification successful")
                    .data(AccountVerificationResponse.builder()
                            .accountNumber(recipeientAccount.getAccountNumber())
                            .accountHolderName(recipeientAccount.getAccountHolderName())
                            .bankName(receiverBank.getBankName())
                            .bankCode(receiverBank.getBankCode())
                            .build())
                    .messageId(request.getMessageId())
                    .messageType("camt.004")
                    .messageStatus("ACCP")
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            DefaultResponse response = DefaultResponse.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .message("Internal server error: " + e.getMessage())
                    .messageId(request.getMessageId())
                    .messageType("camt.004")
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Bank getBankByCode(String bankCode) {
        return bankStorageService.getBankByCode(bankCode);
    }
}
