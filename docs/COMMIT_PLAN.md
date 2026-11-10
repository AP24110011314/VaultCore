# Commit Plan

Nothing has been committed. Run these commands yourself, in this order. Each commit is 2 to 3 words and each one
leaves the project compiling (docs aside). Run everything from the project root.

```bash
git init            # skip if already a repository
chmod +x gradlew    # file permissions are not preserved in the download; do this BEFORE the first `git add`
J=src/main/java/com/service/internalWallet
T=src/test/java/com/service/internalWallet
```

## Phase 1: Foundation

**1. Project scaffold**
```bash
git add build.gradle settings.gradle gradlew gradlew.bat gradle .gitignore .gitattributes \
        src/main/resources/application.yml $J/InternalWalletApplication.java
git commit -m "Project scaffold"
```

**2. Add docs**
```bash
git add README.md docs
git commit -m "Add docs"
```

## Phase 2: Domain

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

## Phase 3: API contracts

**7. API contracts**
```bash
git add $J/dto $J/exception/GlobalExceptionHandler.java
git commit -m "API contracts"
```

## Phase 4: Business logic

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

## Phase 5: REST and seeding

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

## Phase 6: Tests

**12. Test setup**
```bash
git add src/test/resources/application-test.yml $T/support $T/InternalWalletApplicationTests.java
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

## Phase 7: Containerisation

**16. Docker setup**
```bash
git add Dockerfile .dockerignore docker-compose.yml
git commit -m "Docker setup"
```

## Check that nothing was missed

```bash
git status --short      # should print nothing
git log --oneline       # should show the 16 commits above
```
