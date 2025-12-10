package com.ips.mock.service;

import com.ips.mock.PaymentStatus;
import com.ips.mock.request.Payment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class SwiftMTService {
    // Hard-coded BICs for simulation
    private static final String SENDER_BIC = "BKAALKLXADVP";
    private static final String RECEIVER_BIC = "BKBKDEFFMVP";

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * Build a simple MT103 customer credit transfer message
     * using the data from the Payment object.
     */
    public String buildMt103(Payment p) {
        if (p.getReference() == null || p.getReference().isEmpty()) {
            throw new IllegalArgumentException("Payment reference must be set before building MT103");
        }
        if (p.getCurrency() == null || p.getAmount() == null) {
            throw new IllegalArgumentException("Currency and amount must be set before building MT103");
        }

        String valueDate = LocalDate.now().format(YYMMDD);
        String amountStr = formatAmount(p.getAmount()); // e.g. 1000,00

        StringBuilder sb = new StringBuilder();

        sb.append("{1:F01").append(SENDER_BIC).append("0000000000}\n");
        sb.append("{2:I103").append(RECEIVER_BIC).append("N}\n");
        sb.append("{3:{108:").append(p.getReference()).append("}}\n");
        sb.append("{4:\n");
        sb.append(":20:").append(p.getReference()).append("\n");
        sb.append(":23B:CRED\n");
        sb.append(":32A:").append(valueDate)
                .append(p.getCurrency())
                .append(amountStr).append("\n");
        sb.append(":33B:").append(p.getCurrency())
                .append(amountStr).append("\n");

        // Debtor (:50K:)
        sb.append(":50K:/").append(nullSafe(p.getDebtorAccount())).append("\n");
        sb.append(nullSafe(p.getDebtorName())).append("\n");
        sb.append(nullSafe(p.getDebtorAddress())).append("\n");

        // Creditor (:59:)
        sb.append(":59:/").append(nullSafe(p.getCreditorAccount())).append("\n");
        sb.append(nullSafe(p.getCreditorName())).append("\n");
        sb.append(nullSafe(p.getCreditorAddress())).append("\n");

        // Remittance info (:70:)
        sb.append(":70:").append(nullSafe(p.getRemittanceInfo())).append("\n");
        sb.append(":71A:SHA\n");
        sb.append("-}\n");
        sb.append("{5:{CHK:AB12CD34EF56}}\n");

        String mt103 = sb.toString();
        p.setMt103Raw(mt103);
        p.setStatus(PaymentStatus.SENT_MT103);
        return mt103;
    }

    /**
     * Build a simple MT910 credit advice for the given Payment.
     * Assumes the payment has already been accepted/credited at Bank B.
     */
    public String buildMt910(Payment p) {
        if (p.getReference() == null || p.getReference().isEmpty()) {
            throw new IllegalArgumentException("Payment reference must be set before building MT910");
        }
        if (p.getCurrency() == null || p.getAmount() == null) {
            throw new IllegalArgumentException("Currency and amount must be set before building MT910");
        }

        String valueDate = LocalDate.now().format(YYMMDD);
        String amountStr = formatAmount(p.getAmount());

        StringBuilder sb = new StringBuilder();

        sb.append("{1:F01").append(RECEIVER_BIC).append("0000000000}\n");
        sb.append("{2:I910").append(SENDER_BIC).append("N}\n");
        sb.append("{3:{108:").append(p.getReference()).append("}}\n");
        sb.append("{4:\n");
        sb.append(":20:").append(p.getReference()).append("\n");
        sb.append(":21:").append(p.getReference()).append("\n");
        sb.append(":25:").append(p.getCurrency()).append("12345678").append("\n"); // fake account at Bank B
        sb.append(":32A:").append(valueDate)
                .append(p.getCurrency())
                .append(amountStr).append("\n");
        sb.append(":52A:").append(SENDER_BIC).append("\n");
        sb.append(":72:/ACC/").append(nullSafe(p.getCreditorAccount()))
                .append(" CREDITED\n");
        sb.append("-}\n");
        sb.append("{5:{CHK:ZX98YU76TR54}}\n");

        String mt910 = sb.toString();
        p.setMt910Raw(mt910);
        p.setStatus(PaymentStatus.CREDITED);
        return mt910;
    }

    /**
     * Very simple parser for an MT103 in the format created by buildMt103().
     * This is NOT a full SWIFT parser – just enough for your simulation.
     */
    public Payment parseMt103(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Raw MT103 message cannot be null or empty");
        }

        Payment p = new Payment();
        p.setMt103Raw(raw);

        String[] lines = raw.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith(":20:")) {
                p.setReference(line.substring(4).trim());
            } else if (line.startsWith(":32A:")) {
                // Example: :32A:251209EUR1000,00
                String data = line.substring(5).trim();
                if (data.length() >= 9) {
                    String currency = data.substring(6, 9);
                    String amountPart = data.substring(9);

                    // Remove trailing comma (if present) and convert comma to dot
                    amountPart = amountPart.replace(",", ".");
                    if (amountPart.endsWith(".")) {
                        amountPart = amountPart.substring(0, amountPart.length() - 1);
                    }

                    BigDecimal amount = new BigDecimal(amountPart);
                    p.setCurrency(currency);
                    p.setAmount(amount);
                }
            } else if (line.startsWith(":50K:")) {
                // Debtor block:
                // :50K:/ACCOUNT
                // NAME
                // ADDRESS
                String account = line.substring(5).trim();
                if (account.startsWith("/")) {
                    account = account.substring(1);
                }
                p.setDebtorAccount(account);

                // Next line = name
                if (i + 1 < lines.length) {
                    p.setDebtorName(lines[++i].trim());
                }
                // Next line (if not starting with :) = address
                if (i + 1 < lines.length && !lines[i + 1].trim().startsWith(":")) {
                    p.setDebtorAddress(lines[++i].trim());
                }
            } else if (line.startsWith(":59:")) {
                // Creditor block:
                // :59:/ACCOUNT
                // NAME
                // ADDRESS
                String account = line.substring(4).trim();
                if (account.startsWith("/")) {
                    account = account.substring(1);
                }
                p.setCreditorAccount(account);

                // Next line = name
                if (i + 1 < lines.length) {
                    p.setCreditorName(lines[++i].trim());
                }
                // Next line (if not starting with :) = address
                if (i + 1 < lines.length && !lines[i + 1].trim().startsWith(":")) {
                    p.setCreditorAddress(lines[++i].trim());
                }
            } else if (line.startsWith(":70:")) {
                p.setRemittanceInfo(line.substring(4).trim());
            }
        }

        // If reference was found, consider it at least SENT_MT103 level
        if (p.getReference() != null) {
            p.setStatus(PaymentStatus.SENT_MT103);
        }

        return p;
    }

    // ------- Helpers -------

    private String formatAmount(BigDecimal amount) {
        // Format as "1000,00" (2 decimal places, comma decimal separator)
        String s = amount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
        // Replace decimal point with comma
        s = s.replace('.', ',');
        return s;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
