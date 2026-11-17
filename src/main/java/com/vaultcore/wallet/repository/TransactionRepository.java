package com.vaultcore.wallet.repository;

import com.vaultcore.wallet.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT DISTINCT t
        FROM Transaction t
        JOIN t.ledgerEntries le
        JOIN le.account a
        JOIN a.user u
        WHERE u.email = :email
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByUserEmail(@Param("email") String email);

    @Query("""
        SELECT DISTINCT t
        FROM Transaction t
        JOIN t.ledgerEntries le
        JOIN le.account a
        JOIN a.user u
        WHERE u.email = :email
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByUserEmail(@Param("email") String email, Pageable pageable);

    @Query("""
        SELECT COUNT(DISTINCT t)
        FROM Transaction t
        JOIN t.ledgerEntries le
        JOIN le.account a
        JOIN a.user u
        WHERE u.email = :email
    """)
    long countByUserEmail(@Param("email") String email);
}
