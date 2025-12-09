package com.ips.mock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Bank {
    private String bankCode;
    private String bankName;
    private BigDecimal liquidityBalance;
}
