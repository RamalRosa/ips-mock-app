package com.ips.mock.controller;

import com.ips.mock.PaymentStatus;
import com.ips.mock.request.Payment;
import com.ips.mock.service.SwiftMTService;
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
@RequestMapping("/api/swift")
public class SwiftSimulationController {
    private final SwiftMTService swiftMtFactoryService;

    // simple in-memory storage
    private final Map<String, Payment> paymentsById = new ConcurrentHashMap<>();
    private final Map<String, Payment> paymentsByReference = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    public SwiftSimulationController(SwiftMTService swiftMtFactoryService) {
        this.swiftMtFactoryService = swiftMtFactoryService;
    }

    // ---------- DTOs for JSON ----------

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
        public String mt103Message;
    }

    public static class PaymentStatusResponse {
        public String paymentId;
        public String reference;
        public String status;
        public BigDecimal amount;
        public String currency;
    }

    // =========================================================
    //  /bankA/payments  (JSON) → create payment & build MT103
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

        // generate reference like REF20251209-AB12CD34
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

        // build MT103 (also sets mt103Raw + status SENT_MT103)
        String mt103 = swiftMtFactoryService.buildMt103(p);

        // store in-memory
        paymentsById.put(p.getId(), p);
        paymentsByReference.put(p.getReference(), p);

        CreatePaymentResponse resp = new CreatePaymentResponse();
        resp.paymentId = p.getId();
        resp.reference = p.getReference();
        resp.status = p.getStatus().name();
        resp.mt103Message = mt103;

        return ResponseEntity.ok(resp);
    }

    // =========================================================
    //  /bankB/swift/mt103  (text/plain) → Bank B receives MT103
    // =========================================================
    @PostMapping(
            value = "/bankB/swift/mt103",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> receiveMt103AtBankB(
            @RequestBody String rawMt103
    ) {
        // parse MT103 into a Payment-like object
        Payment p = swiftMtFactoryService.parseMt103(rawMt103);

        // In a real “Bank B” we might assign a different id, but for simulation this is fine
        paymentsById.put(p.getId(), p);
        paymentsByReference.put(p.getReference(), p);

        // accept and generate MT910 confirmation
        String mt910 = swiftMtFactoryService.buildMt910(p);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(mt910);
    }

    // =========================================================
    //  /bankA/swift/mt910  (text/plain) → Bank A receives MT910
    // =========================================================
    @PostMapping(
            value = "/bankA/swift/mt910",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PaymentStatusResponse> receiveMt910AtBankA(
            @RequestBody String rawMt910
    ) {

        String reference = extractReferenceFromMt910(rawMt910);
        if (reference == null) {
            return ResponseEntity.badRequest().build();
        }

        Payment p = paymentsByReference.get(reference);
        if (p == null) {
            // not found in Bank A records
            return ResponseEntity.notFound().build();
        }

        p.setMt910Raw(rawMt910);
        p.setStatus(PaymentStatus.CREDITED);

        PaymentStatusResponse resp = new PaymentStatusResponse();
        resp.paymentId = p.getId();
        resp.reference = p.getReference();
        resp.status = p.getStatus().name();
        resp.amount = p.getAmount();
        resp.currency = p.getCurrency();

        return ResponseEntity.ok(resp);
    }

    // =========================================================
    //  Helper: extract :20: reference from MT910
    // =========================================================
    private String extractReferenceFromMt910(String raw) {
        if (raw == null) return null;
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(":20:")) {
                return line.substring(4).trim();
            }
        }
        // fallback: try :21:
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(":21:")) {
                return line.substring(4).trim();
            }
        }
        return null;
    }
}
