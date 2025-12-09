package com.ips.mock.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountVerificationResponse {
    private String accountNumber;
    private String bankName;
    private String bankCode;
    private String accountHolderName;
}
