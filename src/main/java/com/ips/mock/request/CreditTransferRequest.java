package com.ips.mock.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditTransferRequest {

    // Debtor agent (initiating bank)
    private String initiatorBic;           // e.g. "HNBLSLFRXXX"
    private String initiatorAccountNumber; // e.g. "LK1230000001"

    // Creditor agent (receiving bank)
    private String recipientBic;           // e.g. "DEUTDEFFXXX"
    private String recipientAccountNumber; // e.g. "DE89370400440532013000"

    private BigDecimal amount;            // e.g. 1000.00
    private String currency;              // e.g. "EUR" or "LKR" (defaulted if null)

    // Optional client reference
    private String endToEndId;            // if null, we generate one
}
