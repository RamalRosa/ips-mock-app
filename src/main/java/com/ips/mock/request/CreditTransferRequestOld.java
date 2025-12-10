package com.ips.mock.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditTransferRequestOld {
    private String initiator;
    private String initiatorAccountNumber;
    private String recipient;
    private String recipientAccountNumber;
    private BigDecimal amount;
}
