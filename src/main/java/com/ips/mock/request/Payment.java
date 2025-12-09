package com.ips.mock.request;

import com.ips.mock.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class Payment {

    private String id;
    private String reference;

    private BigDecimal amount;
    private String currency;

    private String debtorName;
    private String debtorAccount;
    private String debtorAddress;

    private String creditorName;
    private String creditorAccount;
    private String creditorAddress;

    private String remittanceInfo;

    private PaymentStatus status;

    private String mt103Raw;
    private String mt910Raw;

    public Payment() {
        this.id = UUID.randomUUID().toString();
        this.status = PaymentStatus.NEW;
    }
}
