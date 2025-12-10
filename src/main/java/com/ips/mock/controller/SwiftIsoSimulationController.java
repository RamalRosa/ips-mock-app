package com.ips.mock.controller;

import com.ips.mock.PaymentStatus;
import com.ips.mock.request.Payment;
import com.ips.mock.service.Iso20022PaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class SwiftIsoSimulationController {

    private final Iso20022PaymentService isoService;

    private final Map<String, Payment> paymentsById = new ConcurrentHashMap<>();
    private final Map<String, Payment> paymentsByReference = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    public SwiftIsoSimulationController(Iso20022PaymentService isoService) {
        this.isoService = isoService;
    }

    // ---------- DTOs ----------

    public static class CreatePaymentRequest {
        public BigDecimal amount;
        public String currency;

        public String debtorName;
        public String debtorAccount;
        public String debtorAddress;

        public String creditorName;
        public String creditorAccount;
        public String creditorAddress;

        public String remittanceInfo;
    }

    public static class CreatePaymentResponse {
        public String paymentId;
        public String reference;
        public String status;
        public String pacs008Message;
    }

    public static class PaymentStatusResponse {
        public String paymentId;
        public String reference;
        public String status;
        public BigDecimal amount;
        public String currency;
    }

    // =========================================================
    //  /bankA/payments (JSON) → create payment & build pacs.008
    // =========================================================
    @PostMapping(
            value = "/bankA/payments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CreatePaymentResponse> createPaymentAtBankA(
            @RequestBody CreatePaymentRequest req
    ) {

        Payment p = new Payment();

        String datePart = LocalDate.now().format(DATE_ID_FORMAT);
        String randomPart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        String reference = "REF" + datePart + "-" + randomPart;

        p.setReference(reference);
        p.setAmount(req.amount);
        p.setCurrency(req.currency);

        p.setDebtorName(req.debtorName);
        p.setDebtorAccount(req.debtorAccount);
        p.setDebtorAddress(req.debtorAddress);

        p.setCreditorName(req.creditorName);
        p.setCreditorAccount(req.creditorAccount);
        p.setCreditorAddress(req.creditorAddress);

        p.setRemittanceInfo(req.remittanceInfo);

        // build pacs.008 XML
        String pacs008 = isoService.buildPacs008(p);

        paymentsById.put(p.getId(), p);
        paymentsByReference.put(p.getReference(), p);

        CreatePaymentResponse resp = new CreatePaymentResponse();
        resp.paymentId = p.getId();
        resp.reference = p.getReference();
        resp.status = p.getStatus().name();
        resp.pacs008Message = pacs008;

        return ResponseEntity.ok(resp);
    }

    // =========================================================
    //  /bankB/swift/pacs008 (application/xml) → receives pacs.008, returns camt.054
    // =========================================================
    @PostMapping(
            value = "/bankB/swift/pacs008",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> receivePacs008AtBankB(
            @RequestBody String pacs008Xml
    ) {
        Payment p = isoService.parsePacs008(pacs008Xml);

        paymentsById.put(p.getId(), p);
        paymentsByReference.put(p.getReference(), p);

        // accept & build camt.054 notification
        String camt054 = isoService.buildCamt054(p);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(camt054);
    }

    // =========================================================
    //  /bankA/swift/camt054 (application/xml) → Bank A receives camt.054
    // =========================================================
    @PostMapping(
            value = "/bankA/swift/camt054",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PaymentStatusResponse> receiveCamt054AtBankA(
            @RequestBody String camt054Xml
    ) {
        // For simplicity, reuse parsePacs008 logic just to get reference & amount
        // or write a tiny camt.054 parser; here we only grab EndToEndId.
        String reference = extractBetween(camt054Xml, "<EndToEndId>", "</EndToEndId>");
        if (reference == null) {
            return ResponseEntity.badRequest().build();
        }

        Payment p = paymentsByReference.get(reference);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }

        p.setMt910Raw(camt054Xml);
        p.setStatus(PaymentStatus.CREDITED);

        PaymentStatusResponse resp = new PaymentStatusResponse();
        resp.paymentId = p.getId();
        resp.reference = p.getReference();
        resp.status = p.getStatus().name();
        resp.amount = p.getAmount();
        resp.currency = p.getCurrency();

        return ResponseEntity.ok(resp);
    }

    // ------ tiny helper just for camt.054 reference extraction ------
    private String extractBetween(String text, String startTag, String endTag) {
        int s = text.indexOf(startTag);
        if (s < 0) return null;
        s += startTag.length();
        int e = text.indexOf(endTag, s);
        if (e < 0) return null;
        return text.substring(s, e).trim();
    }
}