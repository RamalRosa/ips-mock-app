package com.ips.mock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Account {
    private String accountNumber;
    private String bankCode;
    private String accountHolderName;
    private String holderIdentificationType; //"NIC", "PASSPORT"
    private String holderIdentificationNumber;
    private BigDecimal balance;
    private String status; //"ACTIVE", "INACTIVE", "CLOSED"
    private String currency; //"MVR", "USD"
}
