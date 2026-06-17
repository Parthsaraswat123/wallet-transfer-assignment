package com.wallet.transfer.repository;

import com.wallet.transfer.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, String> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
