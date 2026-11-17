package com.vaultcore.wallet.repository;

import com.vaultcore.wallet.enums.AccountType;
import com.vaultcore.wallet.model.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserIdAndAssetId(UUID userId, UUID assetId);

    java.util.List<Account> findByUserId(UUID userId);

    Optional<Account> findByTypeAndAssetId(AccountType type, UUID assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT a.id FROM Account a WHERE a.user.id = :userId AND a.asset.id = :assetId")
    Optional<UUID> findIdByUserIdAndAssetId(@Param("userId") UUID userId, @Param("assetId") UUID assetId);

    @Query("SELECT a.id FROM Account a WHERE a.type = :type AND a.asset.id = :assetId")
    Optional<UUID> findIdByTypeAndAssetId(@Param("type") AccountType type, @Param("assetId") UUID assetId);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a")
    long sumAllBalances();
}
