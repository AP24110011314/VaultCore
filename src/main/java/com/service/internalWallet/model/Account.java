package com.service.internalWallet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.exception.InsufficientFundsException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_asset", columnNames = {"user_id", "asset_id"})
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountType type;

    @Column(nullable = false)
    private long balance = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<LedgerEntry> ledgerEntries = new HashSet<>();

    public Account() {
    }

    public Account(UUID id, User user, Asset asset, AccountType type, long balance) {
        this.id = id;
        this.user = user;
        this.asset = asset;
        this.type = type;
        this.balance = balance;
    }

    public Account(UUID id, User user, Asset asset, AccountType type, long balance, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.asset = asset;
        this.type = type;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void credit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        try {
            this.balance = Math.addExact(this.balance, amount);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount would overflow the balance", e);
        }
    }

    public void debit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (this.type == AccountType.USER && this.balance < amount) {
            throw new InsufficientFundsException("Insufficient balance");
        }
        try {
            this.balance = Math.subtractExact(this.balance, amount);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount would overflow the balance", e);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<LedgerEntry> getLedgerEntries() {
        return ledgerEntries;
    }

    public void setLedgerEntries(Set<LedgerEntry> ledgerEntries) {
        this.ledgerEntries = ledgerEntries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
