package com.ips.mock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.mock.dto.Account;
import com.ips.mock.request.AccountVerificationRequest;
import com.ips.mock.request.CreditTransferRequest;
import com.ips.mock.service.storage.AccountStorageService;
import com.ips.mock.service.storage.BankStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class TransactionService {

    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private final AccountStorageService accountStorageService;
    private final BankStorageService bankStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransactionService(AccountStorageService accountStorageService,
                              BankStorageService bankStorageService) {
        this.accountStorageService = accountStorageService;
        this.bankStorageService = bankStorageService;
    }

    // ========================================================================
    // 1) ACCOUNT VERIFICATION (camt.004-style)
    // ========================================================================
    public ResponseEntity<String> verifyAccount(AccountVerificationRequest request) {

        String msgId = "AV-" + OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String queryRef = request.getReference() != null
                ? request.getReference()
                : "AVQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String requestingBic = request.getRequestingBankBic(); // e.g. HNBLSLFRXXX
        String targetBic = request.getTargetBankBic();     // e.g. DEUTDEFFXXX

        try {
            log.info("[ACCOUNT-VERIFY] Incoming request: {}",
                    objectMapper.writeValueAsString(request));

            Account account = accountStorageService.getAccountByAccountNumberAndBankCode(
                    request.getAccountNumber(),
                    targetBic
            );

            boolean exists = account != null;

            String xml = buildCamt004(
                    msgId,
                    queryRef,
                    requestingBic,
                    targetBic,
                    request.getAccountNumber(),
                    account.getAccountHolderName().toUpperCase(),
                    exists
            );

            HttpStatus status = exists ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);

        } catch (JsonProcessingException e) {
            log.error("[ACCOUNT-VERIFY] Error: {}", e.getMessage(), e);

            String xml = buildCamt004Error(
                    msgId,
                    queryRef,
                    requestingBic,
                    targetBic,
                    request.getAccountNumber(),
                    "U999",
                    "Internal error during account verification."
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);
        }
    }

    // Build success camt.004-like response
    private String buildCamt004(String msgId,
                                String queryRef,
                                String requestingBic,
                                String targetBic,
                                String accountNumber,
                                String accountHolderName,
                                boolean exists) {

        String now = OffsetDateTime.now(ZoneOffset.UTC).format(ISO_OFFSET);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.004.001.08\">\n");
        xml.append("  <GetAcctResponse>\n");
        xml.append("    <MsgHdr>\n");
        xml.append("      <MsgId>").append(esc(msgId)).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(now).append("</CreDtTm>\n");
        xml.append("      <ReqngPty>\n");
        xml.append("        <Pty>\n");
        xml.append("          <Id>\n");
        xml.append("            <OrgId>\n");
        xml.append("              <AnyBIC>").append(esc(requestingBic)).append("</AnyBIC>\n");
        xml.append("            </OrgId>\n");
        xml.append("          </Id>\n");
        xml.append("        </Pty>\n");
        xml.append("      </ReqngPty>\n");
        xml.append("    </MsgHdr>\n");
        xml.append("    <RptOrErr>\n");

        if (exists) {
            xml.append("      <Rpt>\n");
            xml.append("        <Acct>\n");
            xml.append("          <Id>\n");
            xml.append("            <Othr><Id>").append(esc(accountNumber)).append("</Id></Othr>\n");
            xml.append("          </Id>\n");
            xml.append("          <Nm>").append(esc(accountHolderName)).append("</Nm>\n");
            xml.append("          <Svcr>\n");
            xml.append("            <FinInstnId>\n");
            xml.append("              <BICFI>").append(esc(targetBic)).append("</BICFI>\n");
            xml.append("            </FinInstnId>\n");
            xml.append("          </Svcr>\n");
            xml.append("          <Sts>ACTV</Sts>\n");
            xml.append("        </Acct>\n");
            xml.append("      </Rpt>\n");
        } else {
            xml.append("      <Err>\n");
            xml.append("        <ErrCd>AC04</ErrCd>\n");
            xml.append("        <Desc>Invalid or unknown account at target agent.</Desc>\n");
            xml.append("      </Err>\n");
        }

        xml.append("    </RptOrErr>\n");
        xml.append("  </GetAcctResponse>\n");
        xml.append("</Document>\n");

        return xml.toString();
    }

    // Build camt.004-like error (internal error)
    private String buildCamt004Error(String msgId,
                                     String queryRef,
                                     String requestingBic,
                                     String targetBic,
                                     String accountNumber,
                                     String errCode,
                                     String errDesc) {
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(ISO_OFFSET);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.004.001.08\">\n" +
                "  <GetAcctResponse>\n" +
                "    <MsgHdr>\n" +
                "      <MsgId>" + esc(msgId) + "</MsgId>\n" +
                "      <CreDtTm>" + now + "</CreDtTm>\n" +
                "    </MsgHdr>\n" +
                "    <RptOrErr>\n" +
                "      <Err>\n" +
                "        <ErrCd>" + esc(errCode) + "</ErrCd>\n" +
                "        <Desc>" + esc(errDesc) + "</Desc>\n" +
                "      </Err>\n" +
                "    </RptOrErr>\n" +
                "  </GetAcctResponse>\n" +
                "</Document>\n";

        return xml;
    }

    // ========================================================================
    // 2) CREDIT TRANSFER (pacs.008 in / pacs.002 out)
    // ========================================================================
    public ResponseEntity<String> processCreditTransfer(CreditTransferRequest request) {
        String businessMessageId = "BM" + OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String originalMessageId = "CT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String endToEndId = request.getEndToEndId() != null
                ? request.getEndToEndId()
                : "E2E-" + request.getInitiatorAccountNumber() + "-" + request.getRecipientAccountNumber();

        String debtorAgentBic = request.getInitiatorBic();   // e.g. "HNBLSLFRXXX"
        String creditorAgentBic = request.getRecipientBic();   // e.g. "DEUTDEFFXXX"
        String currency = request.getCurrency() != null ? request.getCurrency() : "LKR";

        try {
            log.info("[CT] IPS RECEIVES pacs.008 CREDIT TRANSFER REQUEST");
            log.info("[CT] Incoming request: {}", objectMapper.writeValueAsString(request));

            // 1. Validate debtor account
            Account payer = accountStorageService.getAccountByAccountNumberAndBankCode(
                    request.getInitiatorAccountNumber(),
                    debtorAgentBic
            );

            if (payer == null) {
                log.warn("[CT] Initiator account not found: bank={} account={}",
                        debtorAgentBic, request.getInitiatorAccountNumber());

                String xml = buildPacs002(
                        businessMessageId,
                        originalMessageId,
                        endToEndId,
                        debtorAgentBic,
                        creditorAgentBic,
                        "RJCT",
                        "AC04",
                        "Initiator account not found at debtor agent.",
                        request.getAmount(),
                        currency,
                        request.getInitiatorAccountNumber(),
                        request.getRecipientAccountNumber()
                );

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_XML)
                        .body(xml);
            }

            // 2. Hold liquidity at debtor agent
            bankStorageService.updateLiquidityBalance(
                    debtorAgentBic,
                    request.getAmount(),
                    "DEBIT"
            );

            // 3. Validate creditor account
            Account payee = accountStorageService.getAccountByAccountNumberAndBankCode(
                    request.getRecipientAccountNumber(),
                    creditorAgentBic
            );

            if (payee == null) {
                log.warn("[CT] Payee account not found: bank={} account={}",
                        creditorAgentBic, request.getRecipientAccountNumber());

                // Optional: release liquidity hold
                // bankStorageService.updateLiquidityBalance(debtorAgentBic, request.getAmount(), "CREDIT");

                String xml = buildPacs002(
                        businessMessageId,
                        originalMessageId,
                        endToEndId,
                        debtorAgentBic,
                        creditorAgentBic,
                        "RJCT",
                        "AC04",
                        "Recipient account not found at creditor agent.",
                        request.getAmount(),
                        currency,
                        request.getInitiatorAccountNumber(),
                        request.getRecipientAccountNumber()
                );

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_XML)
                        .body(xml);
            }

            // 4. Clear & settle
            bankStorageService.updateLiquidityBalance(
                    creditorAgentBic,
                    request.getAmount(),
                    "CREDIT"
            );

            accountStorageService.updateAccountBalance(
                    payer,
                    request.getAmount(),
                    "DEBIT"
            );

            accountStorageService.updateAccountBalance(
                    payee,
                    request.getAmount(),
                    "CREDIT"
            );

            String xml = buildPacs002(
                    businessMessageId,
                    originalMessageId,
                    endToEndId,
                    debtorAgentBic,
                    creditorAgentBic,
                    "ACSC",  // AcceptedSettlementCompleted
                    null,
                    "Transaction successfully settled through IPS.",
                    request.getAmount(),
                    currency,
                    request.getInitiatorAccountNumber(),
                    request.getRecipientAccountNumber()
            );

            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);

        } catch (JsonProcessingException e) {
            log.error("[CT] Error processing transaction: {}", e.getMessage(), e);

            String xml = buildPacs002(
                    businessMessageId,
                    originalMessageId,
                    endToEndId,
                    debtorAgentBic,
                    creditorAgentBic,
                    "RJCT",
                    "U999",
                    "Internal server error while processing credit transfer.",
                    request.getAmount(),
                    currency,
                    request.getInitiatorAccountNumber(),
                    request.getRecipientAccountNumber()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);
        }
    }

    // Build pacs.002.001.12 FIToFIPmtStsRpt
    private String buildPacs002(String businessMessageId,
                                String originalMessageId,
                                String endToEndId,
                                String debtorAgentBic,
                                String creditorAgentBic,
                                String txStatus,
                                String reasonCode,
                                String additionalInfo,
                                BigDecimal amount,
                                String currency,
                                String debtorAccount,
                                String creditorAccount) {

        String now = OffsetDateTime.now(ZoneOffset.UTC).format(ISO_OFFSET);
        String msgName = "pacs.008.001.08";

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n");
        xml.append("  <FIToFIPmtStsRpt>\n");
        xml.append("    <GrpHdr>\n");
        xml.append("      <MsgId>").append(esc(businessMessageId)).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(now).append("</CreDtTm>\n");
        xml.append("      <InstgAgt>\n");
        xml.append("        <FinInstnId><BICFI>").append(esc(creditorAgentBic)).append("</BICFI></FinInstnId>\n");
        xml.append("      </InstgAgt>\n");
        xml.append("      <InstdAgt>\n");
        xml.append("        <FinInstnId><BICFI>").append(esc(debtorAgentBic)).append("</BICFI></FinInstnId>\n");
        xml.append("      </InstdAgt>\n");
        xml.append("    </GrpHdr>\n");
        xml.append("    <OrgnlGrpInfAndSts>\n");
        xml.append("      <OrgnlMsgId>").append(esc(originalMessageId)).append("</OrgnlMsgId>\n");
        xml.append("      <OrgnlMsgNmId>").append(msgName).append("</OrgnlMsgNmId>\n");
        xml.append("      <GrpSts>").append(txStatus).append("</GrpSts>\n");
        xml.append("    </OrgnlGrpInfAndSts>\n");
        xml.append("    <OrgnlPmtInfAndSts>\n");
        xml.append("      <TxInfAndSts>\n");
        xml.append("        <OrgnlInstrId>").append(esc(originalMessageId)).append("</OrgnlInstrId>\n");
        xml.append("        <OrgnlEndToEndId>").append(esc(endToEndId)).append("</OrgnlEndToEndId>\n");
        xml.append("        <TxSts>").append(txStatus).append("</TxSts>\n");

        if (reasonCode != null || additionalInfo != null) {
            xml.append("        <StsRsnInf>\n");
            if (reasonCode != null) {
                xml.append("          <Rsn><Cd>").append(reasonCode).append("</Cd></Rsn>\n");
            }
            if (additionalInfo != null) {
                xml.append("          <AddtlInf>").append(esc(additionalInfo)).append("</AddtlInf>\n");
            }
            xml.append("        </StsRsnInf>\n");
        }

        if (amount != null) {
            xml.append("        <OrgnlTxRef>\n");
            xml.append("          <Amt>\n");
            xml.append("            <InstdAmt Ccy=\"").append(esc(currency)).append("\">")
                    .append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append("</InstdAmt>\n");
            xml.append("          </Amt>\n");
            if (debtorAccount != null) {
                xml.append("          <DbtrAcct><Id><Othr><Id>")
                        .append(esc(debtorAccount))
                        .append("</Id></Othr></Id></DbtrAcct>\n");
            }
            if (creditorAccount != null) {
                xml.append("          <CdtrAcct><Id><Othr><Id>")
                        .append(esc(creditorAccount))
                        .append("</Id></Othr></Id></CdtrAcct>\n");
            }
            xml.append("        </OrgnlTxRef>\n");
        }

        xml.append("      </TxInfAndSts>\n");
        xml.append("    </OrgnlPmtInfAndSts>\n");
        xml.append("  </FIToFIPmtStsRpt>\n");
        xml.append("</Document>\n");

        return xml.toString();
    }

    // Simple XML escaping
    private String esc(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // In TransactionService

    public ResponseEntity<String> balanceInquiryFromIsoXml(AccountVerificationRequest dto) {
        return balanceInquiry(dto);
    }

    // In TransactionService

    private ResponseEntity<String> balanceInquiry(AccountVerificationRequest request) {

        String msgId = request.getReference() != null
                ? request.getReference()
                : "BI-" + OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String requestingBic = request.getRequestingBankBic(); // e.g. HNBLSLFRXXX
        String targetBic = request.getTargetBankBic();     // e.g. DEUTDEFFXXX

        try {
            log.info("[BAL-INQ] Incoming request: requestingBankBic={}, targetBankBic={}, account={}",
                    requestingBic, targetBic, request.getAccountNumber());

            Account account = accountStorageService.getAccountByAccountNumberAndBankCode(
                    request.getAccountNumber(),
                    targetBic
            );

            if (account == null) {
                log.warn("[BAL-INQ] Account not found for bank={} account={}",
                        targetBic, request.getAccountNumber());

                String xml = buildCamt004Error(
                        msgId,
                        msgId,         // reuse as query ref
                        requestingBic,
                        targetBic,
                        request.getAccountNumber(),
                        "AC04",
                        "Unknown account for balance inquiry."
                );

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_XML)
                        .body(xml);
            }

            // Assume your Account DTO has getBalance() and (optionally) getCurrency()
            BigDecimal balance = account.getBalance();
            String currency = "LKR";
            try {
                // only if you have currency on account
                if (account.getCurrency() != null) {
                    currency = account.getCurrency();
                }
            } catch (Exception ignored) {
            }

            String xml = buildCamt004WithBalance(
                    msgId,
                    requestingBic,
                    targetBic,
                    request.getAccountNumber(),
                    balance,
                    currency
            );

            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);

        } catch (Exception e) {
            log.error("[BAL-INQ] Error: {}", e.getMessage(), e);

            String xml = buildCamt004Error(
                    msgId,
                    msgId,
                    requestingBic,
                    targetBic,
                    request.getAccountNumber(),
                    "U999",
                    "Internal error during balance inquiry."
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);
        }
    }

    // In TransactionService

    private String buildCamt004WithBalance(String msgId,
                                           String requestingBic,
                                           String targetBic,
                                           String accountNumber,
                                           BigDecimal balance,
                                           String currency) {
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(ISO_OFFSET);

        String balStr = balance != null
                ? balance.setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "0.00";

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.004.001.08\">\n" +
                "  <GetAcctResponse>\n" +
                "    <MsgHdr>\n" +
                "      <MsgId>" + esc(msgId) + "</MsgId>\n" +
                "      <CreDtTm>" + now + "</CreDtTm>\n" +
                "      <ReqngPty>\n" +
                "        <Pty>\n" +
                "          <Id>\n" +
                "            <OrgId>\n" +
                "              <AnyBIC>" + esc(requestingBic) + "</AnyBIC>\n" +
                "            </OrgId>\n" +
                "          </Id>\n" +
                "        </Pty>\n" +
                "      </ReqngPty>\n" +
                "    </MsgHdr>\n" +
                "    <RptOrErr>\n" +
                "      <Rpt>\n" +
                "        <Acct>\n" +
                "          <Id><Othr><Id>" + esc(accountNumber) + "</Id></Othr></Id>\n" +
                "          <Nm>SIMULATED ACCOUNT HOLDER</Nm>\n" +
                "          <Svcr><FinInstnId><BICFI>" + esc(targetBic) + "</BICFI></FinInstnId></Svcr>\n" +
                "          <Sts>ACTV</Sts>\n" +
                "          <Bal>\n" +
                "            <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>\n" +
                "            <Amt Ccy=\"" + esc(currency) + "\">" +
                balStr + "</Amt>\n" +
                "          </Bal>\n" +
                "        </Acct>\n" +
                "      </Rpt>\n" +
                "    </RptOrErr>\n" +
                "  </GetAcctResponse>\n" +
                "</Document>\n";

        return xml;
    }

}
