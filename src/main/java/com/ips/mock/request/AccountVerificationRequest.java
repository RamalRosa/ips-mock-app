package com.ips.mock.request;

import lombok.Data;
import lombok.NonNull;

import java.time.OffsetDateTime;
@Data
public class AccountVerificationRequest {

    private String messageId;
    private String initiator;      // Financial institution that created the identification assignment.
    private String sender;        // Party that assigns the identification assignment to another party. This is also the sender of the message.
    private String receiver;        // Party that the identification assignment is assigned to. This is also the receiver of the message.
    private String accountNumber;
}
