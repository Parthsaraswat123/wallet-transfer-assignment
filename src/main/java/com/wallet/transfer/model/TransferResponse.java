package com.wallet.transfer.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {
    private String id;
    private String idempotencyKey;
    private String fromWalletId;
    private String toWalletId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
}
