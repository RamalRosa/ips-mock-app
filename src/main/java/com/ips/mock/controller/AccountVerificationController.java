package com.ips.mock.controller;

import com.ips.mock.request.AccountVerificationRequestOld;
import com.ips.mock.response.DefaultResponse;
import com.ips.mock.service.AccountVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account-verification")
public class AccountVerificationController {
    private final AccountVerificationService accountVerificationService;

    public AccountVerificationController(AccountVerificationService accountVerificationService) {
        this.accountVerificationService = accountVerificationService;
    }

    @PostMapping("/verify")
    public ResponseEntity<DefaultResponse> verifyAccount(@RequestBody AccountVerificationRequestOld request) {
        return accountVerificationService.accountVerificationAppReq(request);
    }
}
