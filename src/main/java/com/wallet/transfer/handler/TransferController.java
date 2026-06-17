package com.wallet.transfer.handler;

import com.wallet.transfer.model.TransferRequest;
import com.wallet.transfer.model.TransferResult;
import com.wallet.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<String> createTransfer(@Valid @RequestBody TransferRequest request) {
        // Fast-path: Check cached result outside the write lock / transaction boundary
        Optional<TransferResult> cached = transferService.getCachedResult(request.getIdempotencyKey());
        if (cached.isPresent()) {
            TransferResult result = cached.get();
            return ResponseEntity.status(result.getStatusCode())
                    .header("Content-Type", "application/json")
                    .body(result.getResponseBody());
        }

        // Execute transfer inside lock / transaction boundary
        TransferResult result = transferService.executeTransfer(request);
        return ResponseEntity.status(result.getStatusCode())
                .header("Content-Type", "application/json")
                .body(result.getResponseBody());
    }
}
