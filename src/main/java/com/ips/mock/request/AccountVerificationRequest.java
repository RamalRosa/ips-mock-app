package com.ips.mock.request;

import lombok.Data;

@Data
public class AccountVerificationRequest {

    // Requesting bank
    private String requestingBankBic; // e.g. "HNBLSLFRXXX"

    // Bank where account is held
    private String targetBankBic;     // e.g. "DEUTDEFFXXX"

    // Account to be verified
    private String accountNumber;     // e.g. "DE89370400440532013000"

    // Optional external reference
    private String reference;         // e.g. "AV-REF-12345"
}
