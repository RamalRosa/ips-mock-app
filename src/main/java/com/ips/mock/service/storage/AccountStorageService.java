package com.ips.mock.service.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.mock.dto.Account;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Service
@Slf4j
public class AccountStorageService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final File file = new File("src/main/java/com/ips/mock/data/Accounts.json");

    @Getter
    private List<Account> Accounts = new ArrayList<>();

    public AccountStorageService() {
        loadAccounts();
    }

    private void loadAccounts() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                mapper.writeValue(file, Accounts);
            } else {
                Accounts = mapper.readValue(file, new TypeReference<List<Account>>() {
                });
            }
            log.info("Accounts loaded successfully: {}", mapper.writeValueAsString(Accounts));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveAccounts() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, Accounts);
            log.info("Accounts saved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateAccountBalance(Account account, BigDecimal amount, String transferType) {
        try {
            for (Account acc : Accounts) {

                if (!acc.getAccountNumber().equals(account.getAccountNumber()) ||
                        !acc.getBankCode().equals(account.getBankCode())) {
                    continue;
                }

                BigDecimal newBalance = transferType.equals("CREDIT")
                        ? acc.getBalance().add(amount)
                        : acc.getBalance().subtract(amount);

                acc.setBalance(newBalance);
                saveAccounts();
                log.info("Account balance updated successfully: {}", mapper.writeValueAsString(acc));
                return;
            }

            log.warn("Account not found for update: {}", mapper.writeValueAsString(account));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    public Account getAccountByAccountNumberAndBankCode(String accountNumber, String bankCode) {
        log.info("Getting account by account number {} and bank code {}", accountNumber, bankCode);
        for (Account acc : Accounts) {
            if (acc.getAccountNumber().equals(accountNumber) && acc.getBankCode().equals(bankCode)) {
                return acc;
            }
        }
        return  null;
    }

    public Account getAccountByAccountNumber(String accountNumber) {
        for (Account acc : Accounts) {
            if (acc.getAccountNumber().equals(accountNumber)) {
                return acc;
            }
        }
        return  null;
    }
}
