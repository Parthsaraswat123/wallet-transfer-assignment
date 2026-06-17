package com.wallet.transfer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferResult {
    private final int statusCode;
    private final String responseBody;
}
