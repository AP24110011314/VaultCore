package com.vaultcore.wallet.config;

import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.enums.AccountType;
import com.vaultcore.wallet.enums.TransactionType;
import com.vaultcore.wallet.model.Account;
import com.vaultcore.wallet.model.Asset;
import com.vaultcore.wallet.model.User;
import com.vaultcore.wallet.repository.AccountRepository;
import com.vaultcore.wallet.repository.AssetRepository;
import com.vaultcore.wallet.repository.UserRepository;
import com.vaultcore.wallet.service.TransactionService;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "vaultcore.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;

    public DataSeeder(AssetRepository assetRepository,
                      UserRepository userRepository,
                      AccountRepository accountRepository,
                      TransactionService transactionService) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seed();
    }

    public void seed() {
        // 1. Assets
        Asset goldCoins = assetRepository.findByCode("GOLD_COINS")
                .orElseGet(() -> assetRepository.save(new Asset(null, "Gold Coins", "GOLD_COINS")));
        Asset diamonds = assetRepository.findByCode("DIAMONDS")
                .orElseGet(() -> assetRepository.save(new Asset(null, "Diamonds", "DIAMONDS")));
        Asset loyaltyPoints = assetRepository.findByCode("LOYALTY_POINTS")
                .orElseGet(() -> assetRepository.save(new Asset(null, "Loyalty Points", "LOYALTY_POINTS")));

        // 2. SYSTEM accounts (one per asset)
        for (Asset asset : List.of(goldCoins, diamonds, loyaltyPoints)) {
            if (accountRepository.findByTypeAndAssetId(AccountType.SYSTEM, asset.getId()).isEmpty()) {
                accountRepository.save(new Account(null, null, asset, AccountType.SYSTEM, 0L));
            }
        }

        // 3. Users
        User aditi = userRepository.findByEmail("aditisingh@gmail.com")
                .orElseGet(() -> userRepository.save(new User(null, "Aditi Singh", "aditisingh@gmail.com")));
        User priya = userRepository.findByEmail("priyajha@gmail.com")
                .orElseGet(() -> userRepository.save(new User(null, "Priya Jha", "priyajha@gmail.com")));

        // 4. USER accounts (Aditi and Priya, each with GOLD_COINS and DIAMONDS)
        for (User user : List.of(aditi, priya)) {
            for (Asset asset : List.of(goldCoins, diamonds)) {
                if (accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId()).isEmpty()) {
                    accountRepository.save(new Account(null, user, asset, AccountType.USER, 0L));
                }
            }
        }

        // 5. Opening balances via real transactions
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                500L,
                "seed-aditi-gold"
        ));

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                "priyajha@gmail.com",
                "GOLD_COINS",
                300L,
                "seed-priya-gold"
        ));
    }
}
