# Commit Plan

This document outlines the sequential git commit roadmap. Run these commands yourself, in this order. Each commit message is **2 to 3 words**, grouped by phases, and each commit leaves the project compiling. Run everything from the project root.

```bash
J=src/main/java/com/vaultcore/wallet
T=src/test/java/com/vaultcore/wallet
```

---

## Initial Service Foundations (Commits 1 - 16)

### Phase 1: Foundation
**1. Project scaffold**
```bash
git add build.gradle settings.gradle gradlew gradlew.bat gradle .gitignore .gitattributes \
        src/main/resources/application.yml $J/VaultCoreApplication.java
git commit -m "Project scaffold"
```

**2. Add docs**
```bash
git add README.md docs
git commit -m "Add docs"
```

### Phase 2: Domain
**3. Domain enums**
```bash
git add $J/enums
git commit -m "Domain enums"
```

**4. Core exceptions**
```bash
git add $J/exception/ResourceNotFoundException.java $J/exception/InsufficientFundsException.java
git commit -m "Core exceptions"
```

**5. Domain entities**
```bash
git add $J/model
git commit -m "Domain entities"
```

**6. Add repositories**
```bash
git add $J/repository
git commit -m "Add repositories"
```

### Phase 3: API contracts
**7. API contracts**
```bash
git add $J/dto $J/exception/GlobalExceptionHandler.java
git commit -m "API contracts"
```

### Phase 4: Business logic
**8. Transaction processor**
```bash
git add $J/service/TransactionProcessor.java $J/service/TransactionResult.java
git commit -m "Transaction processor"
```

**9. Transaction service**
```bash
git add $J/service/TransactionService.java
git commit -m "Transaction service"
```

### Phase 5: REST and seeding
**10. REST controller**
```bash
git add $J/controller
git commit -m "REST controller"
```

**11. Seed data**
```bash
git add $J/config
git commit -m "Seed data"
```

### Phase 6: Tests
**12. Test setup**
```bash
git add src/test/resources/application-test.yml $T/support $T/VaultCoreApplicationTests.java
git commit -m "Test setup"
```

**13. Unit tests**
```bash
git add $T/model $T/dto
git commit -m "Unit tests"
```

**14. Integration tests**
```bash
git add $T/service/TransactionServiceIntegrationTest.java $T/config $T/controller
git commit -m "Integration tests"
```

**15. Concurrency tests**
```bash
git add $T/service/ConcurrencyIntegrationTest.java
git commit -m "Concurrency tests"
```

### Phase 7: Containerisation
**16. Docker setup**
```bash
git add Dockerfile .dockerignore docker-compose.yml
git commit -m "Docker setup"
```

---

## VaultCore Rebrand and Web UI (Commits 17 - 32)

### Phase 1: Rebrand
**17. Rebrand project metadata**
```bash
git add build.gradle settings.gradle docker-compose.yml \
        src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "Rebrand project metadata"
```

**18. Rename Java packages**
```bash
git add src/main/java src/test/java
git commit -m "Rename Java packages"
```

**19. Add OpenAPI config**
```bash
git add $J/config/OpenApiConfig.java
git commit -m "Add OpenAPI config"
```

### Phase 2: Logo and Visual Identity
**20. Add SVG branding**
```bash
git add src/main/resources/static/img/vaultcore-icon.svg \
        src/main/resources/static/img/vaultcore-logo.svg \
        src/main/resources/static/favicon.svg
git commit -m "Add SVG branding"
```

**21. Add theme styles**
```bash
git add src/main/resources/static/css/vaultcore.css \
        src/main/resources/static/css/swagger-custom.css
git commit -m "Add theme styles"
```

### Phase 3: Backend Read Endpoints
**22. Add read contracts**
```bash
git add $J/dto/response/AssetResponseDto.java \
        $J/dto/response/UserResponseDto.java \
        $J/dto/response/UserAssetBalanceResponseDto.java \
        $J/dto/response/AccountReconciliationDto.java \
        $J/dto/response/TreasuryBalanceDto.java \
        $J/dto/response/AuditReconciliationResponseDto.java \
        $J/dto/response/ListTransactionResponseDto.java
git commit -m "Add read contracts"
```

**23. Add read queries**
```bash
git add $J/repository/AccountRepository.java \
        $J/repository/TransactionRepository.java
git commit -m "Add read queries"
```

**24. Add read services**
```bash
git add $J/service/TransactionService.java
git commit -m "Add read services"
```

**25. Add read endpoints**
```bash
git add $J/controller/TransactionController.java
git commit -m "Add read endpoints"
```

**26. Test read endpoints**
```bash
git add $T/controller/TransactionControllerTest.java \
        $T/service/TransactionServiceIntegrationTest.java
git commit -m "Test read endpoints"
```

### Phase 4: UI Layout and Pages
**27. Add UI script**
```bash
git add src/main/resources/static/js/vaultcore.js
git commit -m "Add UI script"
```

**28. Add UI templates**
```bash
git add src/main/resources/templates
git commit -m "Add UI templates"
```

**29. Add UI controller**
```bash
git add $J/controller/UiController.java
git commit -m "Add UI controller"
```

### Phase 5: UI Tests
**30. Add UI tests**
```bash
git add $T/controller/UiControllerTest.java
git commit -m "Add UI tests"
```

### Phase 6: Documentation
**31. Add UI guide**
```bash
git add docs/UI.md
git commit -m "Add UI guide"
```

**32. Update project documentation**
```bash
git add README.md docs/README.md docs/API.md docs/ARCHITECTURE.md \
        docs/TESTING.md docs/IMPLEMENTATION_PLAN.md docs/COMMIT_PLAN.md
git commit -m "Update project documentation"
```

---

## Soft-Light Glass Theme Conversion (Commits 33 - 38)

### Phase 7: Theme Removal & Tokens
**33. Drop dark mode**
```bash
git add src/main/resources/static/css/vaultcore.css \
        src/main/resources/static/js/vaultcore.js \
        src/main/resources/templates/layout.html
git commit -m "Drop dark mode"
```

**34. Glass design tokens**
```bash
git add src/main/resources/static/css/vaultcore.css \
        src/main/resources/static/css/swagger-custom.css
git commit -m "Glass design tokens"
```

**35. Glass brand assets**
```bash
git add src/main/resources/static/img/vaultcore-logo.svg \
        src/main/resources/static/img/vaultcore-icon.svg \
        src/main/resources/static/favicon.svg
git commit -m "Glass brand assets"
```

### Phase 8: Template Polish
**36. Polish view templates**
```bash
git add src/main/resources/templates/dashboard.html \
        src/main/resources/templates/transaction-new.html \
        src/main/resources/templates/history.html \
        src/main/resources/templates/audit.html \
        src/main/resources/templates/error/404.html \
        src/main/resources/templates/error/error.html
git commit -m "Polish view templates"
```

### Phase 9: Verification & Documentation
**37. Update UI tests**
```bash
git add src/test/java/com/vaultcore/wallet/controller/UiControllerTest.java
git commit -m "Update UI tests"
```

**38. Update UI documentation**
```bash
git add docs/UI.md README.md docs/README.md docs/COMMIT_PLAN.md
git commit -m "Update UI documentation"
```

---

## Verification Commands

Verify complete test passing and clean build status:

```bash
./gradlew clean test
```
