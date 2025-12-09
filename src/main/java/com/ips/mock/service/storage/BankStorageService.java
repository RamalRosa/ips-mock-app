package com.ips.mock.service.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.mock.dto.Bank;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BankStorageService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final File file = new File("src/main/java/com/ips/mock/data/Banks.json");

    @Getter
    private List<Bank> banks = new ArrayList<>();

    public BankStorageService() {
        loadBanks();
    }

    private void loadBanks() {
        try {
            if (!file.exists()) {
                log.info("File does not exist");
                file.getParentFile().mkdirs();
                file.createNewFile();
                mapper.writeValue(file, banks);
            } else {
                log.info("File exists");
                banks = mapper.readValue(file, new TypeReference<List<Bank>>() {
                });
            }
            log.info("Banks loaded successfully: {}", mapper.writeValueAsString(banks));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveBanks() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, banks);
            log.info("Banks saved successfully: {}", mapper.writeValueAsString(banks));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Bank getBankByCode(String bankCode) {
        log.info("Getting bank by bank code: {}", bankCode);
        for (Bank bank : banks) {
            if (bank.getBankCode().equals(bankCode)) {
                return bank;
            }
        }
        return null;
    }

    public void updateLiquidityBalance(String bankCode, BigDecimal amount, String transferType) {
        for (Bank bank : banks) {
            if (bank.getBankCode().equals(bankCode)) {
                BigDecimal newBalance = BigDecimal.ZERO;
                newBalance = transferType.equals("CREDIT")
                        ?  bank.getLiquidityBalance().add(amount)
                        : bank.getLiquidityBalance().subtract(amount);

                log.info("[{}]. Updating liquidity balance from {} to {}", bank.getBankName(), bank.getLiquidityBalance(), newBalance);
                bank.setLiquidityBalance(newBalance);
                saveBanks();
                return;
            }
        }
    }

}
