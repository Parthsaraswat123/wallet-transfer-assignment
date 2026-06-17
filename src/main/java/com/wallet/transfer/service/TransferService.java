package com.wallet.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.transfer.exception.*;
import com.wallet.transfer.model.*;
import com.wallet.transfer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public TransferService(WalletRepository walletRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           IdempotencyRecordRepository idempotencyRepository,
                           ObjectMapper objectMapper) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if a request has already been processed and cached.
     */
    public Optional<TransferResult> getCachedResult(String idempotencyKey) {
        return idempotencyRepository.findById(idempotencyKey)
                .map(record -> new TransferResult(record.getResponseStatus(), record.getResponseBody()));
    }

    /**
     * Executes a transfer under strict concurrency control and idempotency guarantees.
     */
    @Transactional
    public TransferResult executeTransfer(TransferRequest request) {
        String key = request.getIdempotencyKey();
        String fromId = request.getFromWalletId();
        String toId = request.getToWalletId();
        BigDecimal amount = request.getAmount();

        // 1. Basic input validation
        if (fromId.equals(toId)) {
            throw new SelfTransferException("Cannot transfer to the same wallet");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Transfer amount must be greater than zero");
        }

        // 2. Double-check idempotency table inside the transaction/lock boundary
        Optional<IdempotencyRecord> existingRecord = idempotencyRepository.findById(key);
        if (existingRecord.isPresent()) {
            IdempotencyRecord record = existingRecord.get();
            return new TransferResult(record.getResponseStatus(), record.getResponseBody());
        }

        // 3. Acquire pessimistic locks in alphabetical order to prevent deadlocks
        String firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        String secondId = fromId.compareTo(toId) < 0 ? toId : fromId;

        Wallet firstWallet = walletRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + firstId));
        Wallet secondWallet = walletRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + secondId));

        Wallet fromWallet = firstWallet.getId().equals(fromId) ? firstWallet : secondWallet;
        Wallet toWallet = firstWallet.getId().equals(toId) ? firstWallet : secondWallet;

        // 4. Validate Balance and Process
        if (fromWallet.getBalance().compareTo(amount) < 0) {
            // Insufficient funds: Record as FAILED transfer and cache the 422 error
            Transfer failedTransfer = Transfer.builder()
                    .id(UUID.randomUUID().toString())
                    .idempotencyKey(key)
                    .fromWalletId(fromId)
                    .toWalletId(toId)
                    .amount(amount)
                    .status(TransferStatus.FAILED)
                    .build();
            transferRepository.save(failedTransfer);

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("status", 422);
            errorBody.put("error", "Insufficient funds in wallet: " + fromId);
            String jsonError = toJson(errorBody);

            IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
                    .key(key)
                    .responseStatus(422)
                    .responseBody(jsonError)
                    .build();
            idempotencyRepository.save(idempotencyRecord);

            return new TransferResult(422, jsonError);
        }

        // 5. Deduct and credit
        fromWallet.setBalance(fromWallet.getBalance().subtract(amount));
        toWallet.setBalance(toWallet.getBalance().add(amount));
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // 6. Create transfer record
        Transfer processedTransfer = Transfer.builder()
                .id(UUID.randomUUID().toString())
                .idempotencyKey(key)
                .fromWalletId(fromId)
                .toWalletId(toId)
                .amount(amount)
                .status(TransferStatus.PROCESSED)
                .build();
        transferRepository.save(processedTransfer);

        // 7. Double-entry ledger entries
        LedgerEntry debit = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .walletId(fromId)
                .transferId(processedTransfer.getId())
                .type(LedgerEntryType.DEBIT)
                .amount(amount)
                .build();
        LedgerEntry credit = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .walletId(toId)
                .transferId(processedTransfer.getId())
                .type(LedgerEntryType.CREDIT)
                .amount(amount)
                .build();
        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        // 8. Cache response in idempotency records
        TransferResponse responseDto = TransferResponse.builder()
                .id(processedTransfer.getId())
                .idempotencyKey(key)
                .fromWalletId(fromId)
                .toWalletId(toId)
                .amount(amount)
                .status(processedTransfer.getStatus().name())
                .createdAt(processedTransfer.getCreatedAt())
                .build();
        String jsonSuccess = toJson(responseDto);

        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
                .key(key)
                .responseStatus(201)
                .responseBody(jsonSuccess)
                .build();
        idempotencyRepository.save(idempotencyRecord);

        return new TransferResult(201, jsonSuccess);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }
}
