package com.ips.mock.service;


import com.ips.mock.PaymentStatus;
import com.ips.mock.request.Payment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class Iso20022PaymentService {

    private static final String SENDER_BIC = "BKAALKLX";
    private static final String RECEIVER_BIC = "BKBKDEFF";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Build a simplified ISO 20022 pacs.008.001.08 FIToFICstmrCdtTrf message.
     */
    public String buildPacs008(Payment p) {
        if (p.getReference() == null || p.getReference().isEmpty()) {
            throw new IllegalArgumentException("Payment reference must be set before building pacs.008");
        }
        if (p.getCurrency() == null || p.getAmount() == null) {
            throw new IllegalArgumentException("Currency and amount must be set before building pacs.008");
        }

        String today = LocalDate.now().format(ISO_DATE);
        String msgId = p.getReference();
        String instrId = "INST-" + p.getReference();
        String txId = "TX-" + p.getReference();
        String amountStr = formatAmount(p.getAmount());

        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">" +
                        "  <FIToFICstmrCdtTrf>" +
                        "    <GrpHdr>" +
                        "      <MsgId>" + esc(msgId) + "</MsgId>" +
                        "      <CreDtTm>" + today + "T10:15:30</CreDtTm>" +
                        "      <NbOfTxs>1</NbOfTxs>" +
                        "      <SttlmInf>" +
                        "        <SttlmMtd>CLRG</SttlmMtd>" +
                        "      </SttlmInf>" +
                        "    </GrpHdr>" +
                        "    <CdtTrfTxInf>" +
                        "      <PmtId>" +
                        "        <InstrId>" + esc(instrId) + "</InstrId>" +
                        "        <EndToEndId>" + esc(p.getReference()) + "</EndToEndId>" +
                        "        <TxId>" + esc(txId) + "</TxId>" +
                        "      </PmtId>" +
                        "      <IntrBkSttlmAmt Ccy=\"" + esc(p.getCurrency()) + "\">" + amountStr + "</IntrBkSttlmAmt>" +
                        "      <IntrBkSttlmDt>" + today + "</IntrBkSttlmDt>" +
                        "      <ChrgBr>SHAR</ChrgBr>" +
                        "      <Dbtr>" +
                        "        <Nm>" + esc(p.getDebtorName()) + "</Nm>" +
                        "        <PstlAdr>" +
                        "          <AdrLine>" + esc(p.getDebtorAddress()) + "</AdrLine>" +
                        "        </PstlAdr>" +
                        "      </Dbtr>" +
                        "      <DbtrAcct>" +
                        "        <Id>" +
                        "          <Othr>" +
                        "            <Id>" + esc(p.getDebtorAccount()) + "</Id>" +
                        "          </Othr>" +
                        "        </Id>" +
                        "      </DbtrAcct>" +
                        "      <DbtrAgt>" +
                        "        <FinInstnId>" +
                        "          <BICFI>" + SENDER_BIC + "</BICFI>" +
                        "        </FinInstnId>" +
                        "      </DbtrAgt>" +
                        "      <CdtrAgt>" +
                        "        <FinInstnId>" +
                        "          <BICFI>" + RECEIVER_BIC + "</BICFI>" +
                        "        </FinInstnId>" +
                        "      </CdtrAgt>" +
                        "      <Cdtr>" +
                        "        <Nm>" + esc(p.getCreditorName()) + "</Nm>" +
                        "        <PstlAdr>" +
                        "          <AdrLine>" + esc(p.getCreditorAddress()) + "</AdrLine>" +
                        "        </PstlAdr>" +
                        "      </Cdtr>" +
                        "      <CdtrAcct>" +
                        "        <Id>" +
                        "          <IBAN>" + esc(p.getCreditorAccount()) + "</IBAN>" +
                        "        </Id>" +
                        "      </CdtrAcct>" +
                        "      <RmtInf>" +
                        "        <Ustrd>" + esc(p.getRemittanceInfo()) + "</Ustrd>" +
                        "      </RmtInf>" +
                        "    </CdtTrfTxInf>" +
                        "  </FIToFICstmrCdtTrf>" +
                        "</Document>";

        p.setStatus(PaymentStatus.SENT_MT103); // reuse status name; logically "sent"
        p.setMt103Raw(xml); // reuse the field to store XML if you want
        return xml;
    }

    /**
     * Build a simplified camt.054.001.08 credit notification for the Payment.
     */
    public String buildCamt054(Payment p) {
        String today = LocalDate.now().format(ISO_DATE);
        String msgId = "NOTIF-" + p.getReference();
        String notifId = "N-" + p.getReference();
        String amountStr = formatAmount(p.getAmount());

        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.054.001.08\">\n" +
                        "  <BkToCstmrDbtCdtNtfctn>\n" +
                        "    <GrpHdr>\n" +
                        "      <MsgId>" + esc(msgId) + "</MsgId>\n" +
                        "      <CreDtTm>" + today + "T11:22:33</CreDtTm>\n" +
                        "    </GrpHdr>\n" +
                        "    <Ntfctn>\n" +
                        "      <Id>" + esc(notifId) + "</Id>\n" +
                        "      <Acct>\n" +
                        "        <Id>\n" +
                        "          <Othr>\n" +
                        "            <Id>EUR12345678</Id>\n" +  // fake nostro at Bank B
                        "          </Othr>\n" +
                        "        </Id>\n" +
                        "      </Acct>\n" +
                        "      <Ntry>\n" +
                        "        <Amt Ccy=\"" + esc(p.getCurrency()) + "\">" + amountStr + "</Amt>\n" +
                        "        <CdtDbtInd>CRDT</CdtDbtInd>\n" +
                        "        <BookgDt><Dt>" + today + "</Dt></BookgDt>\n" +
                        "        <NtryDtls>\n" +
                        "          <TxDtls>\n" +
                        "            <Refs>\n" +
                        "              <EndToEndId>" + esc(p.getReference()) + "</EndToEndId>\n" +
                        "              <TxId>TX-" + esc(p.getReference()) + "</TxId>\n" +
                        "            </Refs>\n" +
                        "            <RltdPties>\n" +
                        "              <Dbtr><Nm>" + esc(p.getDebtorName()) + "</Nm></Dbtr>\n" +
                        "              <Cdtr><Nm>" + esc(p.getCreditorName()) + "</Nm></Cdtr>\n" +
                        "            </RltdPties>\n" +
                        "          </TxDtls>\n" +
                        "        </NtryDtls>\n" +
                        "      </Ntry>\n" +
                        "    </Ntfctn>\n" +
                        "  </BkToCstmrDbtCdtNtfctn>\n" +
                        "</Document>\n";

        p.setStatus(PaymentStatus.CREDITED);
        p.setMt910Raw(xml); // reuse field if you like
        return xml;
    }

    /**
     * Very simple pacs.008 parser – only extracts fields needed for the simulation.
     * NOT production-grade, just string-based.
     */
    public Payment parsePacs008(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("pacs.008 XML cannot be null or empty");
        }

        Payment p = new Payment();
        p.setMt103Raw(xml);

        // Reference from EndToEndId
        String ref = extractBetween(xml, "<EndToEndId>", "</EndToEndId>");
        p.setReference(ref);

        // Currency & amount from IntrBkSttlmAmt
        int amtTagStart = xml.indexOf("<IntrBkSttlmAmt");
        if (amtTagStart >= 0) {
            int ccyStart = xml.indexOf("Ccy=\"", amtTagStart);
            if (ccyStart >= 0) {
                ccyStart += 5;
                int ccyEnd = xml.indexOf("\"", ccyStart);
                if (ccyEnd > ccyStart) {
                    String ccy = xml.substring(ccyStart, ccyEnd);
                    p.setCurrency(ccy);
                }
            }
            int closeStart = xml.indexOf(">", amtTagStart);
            int closeEnd = xml.indexOf("</IntrBkSttlmAmt>", closeStart);
            if (closeStart >= 0 && closeEnd > closeStart) {
                String amtStr = xml.substring(closeStart + 1, closeEnd).trim();
                BigDecimal amount = new BigDecimal(amtStr);
                p.setAmount(amount);
            }
        }

        // Simple debtor/creditor fields
        String debtorName = extractBetween(xml, "<Dbtr>", "</Dbtr>");
        if (debtorName != null) {
            p.setDebtorName(extractBetween(debtorName, "<Nm>", "</Nm>"));
            p.setDebtorAddress(extractBetween(debtorName, "<AdrLine>", "</AdrLine>"));
        }
        String debtorAcctBlock = extractBetween(xml, "<DbtrAcct>", "</DbtrAcct>");
        if (debtorAcctBlock != null) {
            String acc = extractBetween(debtorAcctBlock, "<Id>", "</Id>");
            if (acc != null) {
                // try Othr/Id
                String acc2 = extractBetween(acc, "<Id>", "</Id>");
                if (acc2 != null) acc = acc2;
                p.setDebtorAccount(stripTags(acc));
            }
        }

        String creditorBlock = extractBetween(xml, "<Cdtr>", "</Cdtr>");
        if (creditorBlock != null) {
            p.setCreditorName(extractBetween(creditorBlock, "<Nm>", "</Nm>"));
            p.setCreditorAddress(extractBetween(creditorBlock, "<AdrLine>", "</AdrLine>"));
        }
        String creditorAcctBlock = extractBetween(xml, "<CdtrAcct>", "</CdtrAcct>");
        if (creditorAcctBlock != null) {
            String iban = extractBetween(creditorAcctBlock, "<IBAN>", "</IBAN>");
            p.setCreditorAccount(iban);
        }

        String rmt = extractBetween(xml, "<Ustrd>", "</Ustrd>");
        p.setRemittanceInfo(rmt);

        if (p.getReference() != null) {
            p.setStatus(PaymentStatus.SENT_MT103);
        }
        return p;
    }

    // --------- helpers ----------

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String extractBetween(String text, String startTag, String endTag) {
        int s = text.indexOf(startTag);
        if (s < 0) return null;
        s += startTag.length();
        int e = text.indexOf(endTag, s);
        if (e < 0) return null;
        return text.substring(s, e).trim();
    }

    private String stripTags(String s) {
        if (s == null) return null;
        return s.replaceAll("<[^>]+>", "").trim();
    }
}

