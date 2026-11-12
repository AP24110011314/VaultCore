package com.service.internalWallet.repository;

import com.service.internalWallet.model.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountId(UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    @Query("SELECT COALESCE(SUM(CASE WHEN le.type = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) FROM LedgerEntry le WHERE le.account.id = :accountId")
    Long sumAmountsByAccountId(@Param("accountId") UUID accountId);
}
