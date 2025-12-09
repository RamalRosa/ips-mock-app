package com.ips.mock.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DefaultResponse {
    private int status;
    private String message;
    private Object data;
    private String messageId;
    private String messageType;
    private String messageStatus;
    private ErrorDetails error;
}

