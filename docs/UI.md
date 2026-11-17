# VaultCore UI & Visual Identity

VaultCore includes a server-rendered, offline-first web interface built with **Thymeleaf**, vanilla **CSS**, and modern **vanilla JavaScript**. It requires no Node.js, npm, webpack, external bundlers, or remote CDN links.

The interface adheres to a unified **"soft-light glass"** aesthetic: a single, meticulously toned-down light theme using frosted glassmorphic panels over a soft misty blue-grey background with subtle, living motion.

---

## 🎨 Visual Identity & Logo

### Design Concept
The VaultCore emblem embodies security, financial precision, and virtual value custody:
- **Shield / Hexagonal Vault Door**: A rounded hexagon perimeter with precision geometry representing cryptographic fortification.
- **Concentric Vault Dial**: A dial ring with 4 cardinal bolt lugs evoking physical high-security bank vaults.
- **Amber Gold Core Medallion**: A glowing inner disc symbolizing virtual currency wealth (Gold Coins, Diamonds).
- **Core Keyhole / Coin Slot**: A central geometry that reads crisply as both an access keyhole and a coin deposit notch.

### Scalability & Formats
- **Favicon (`src/main/resources/static/favicon.svg`)**: Streamlined vectors optimized for pixel alignment at 16x16 and 32x32 pixels.
- **Icon (`src/main/resources/static/img/vaultcore-icon.svg`)**: Standalone 64x64 vector icon for avatars, mobile headers, and badges.
- **Full Brand Logo (`src/main/resources/static/img/vaultcore-logo.svg`)**: Icon paired with the "VaultCore" wordmark. The wordmark uses bold typography with "Vault" in deep navy (`#0F1B33`) and "Core" in golden amber (`#8A4B08`).
- **Inline Header SVG (`src/main/resources/templates/layout.html`)**: Directly embedded in the shared header so that the `.vault-dial-ring` element can be animated via pure CSS keyframes.

---

## 💎 Soft-Light Glass Design System

VaultCore features a single theme: **light, but slightly toned down** (a soft misty blue-grey, not stark white) ensuring that every piece of typography is crisp, readable, and satisfies WCAG AA contrast standards.

### Color Tokens

All colors are declared as CSS custom properties on `:root` and used consistently throughout:

| Token | Hex / CSS Value | Description & Purpose |
|---|---|---|
| `--page-base` | `#D5DCE8` | Canvas background base (soft mist blue-grey) |
| `--page-grad-a` | `#E3E8F2` | Gradient top-left tone |
| `--page-grad-b` | `#C9D2E3` | Gradient bottom-right tone |
| `--glass-bg` | `rgba(255, 255, 255, 0.55)` | Primary card and table container surface |
| `--glass-bg-strong` | `rgba(255, 255, 255, 0.72)` | Form cards, inputs, dropdown menus |
| `--glass-border` | `rgba(255, 255, 255, 0.70)` | 1px frosted translucent border |
| `--glass-shadow` | `0 8px 32px rgba(31, 45, 90, 0.14)` | Ambient elevation shadow |
| `--glass-shadow-elevated`| `0 16px 40px rgba(31, 45, 90, 0.18)` | Hover elevation shadow |
| `--ink` | `#0F1B33` | Primary text, headings, balances |
| `--ink-2` | `#2E3D5C` | Secondary text, card titles |
| `--muted` | `#4A5A78` | Captions, table headers, helper text |
| `--indigo` | `#27337A` | Links, active navigation, focus ring base |
| `--indigo-soft` | `rgba(39, 51, 122, 0.12)` | Navigation hover and active pill background |
| `--amber` | `#F2A93B` | Primary button fill, logo accents (decorative) |
| `--amber-text` | `#8A4B08` | Amber-colored text (minimum 5.4:1 contrast) |
| `--amber-chip` | `#F7E2B8` | Amber chip background |
| `--success` | `#166534` | Success status, credit amounts |
| `--success-bg` | `#C9EBD4` | Success badge background |
| `--danger` | `#9F1239` | Error status, debit amounts, validation errors |
| `--danger-bg` | `#F8D5DC` | Danger badge background |
| `--info` | `#1D4ED8` | Topup/Bonus type badges, informational alerts |
| `--info-bg` | `#D6E2FB` | Info badge background |

---

## ♿ WCAG AA Contrast Audit

Every text and interactive element in VaultCore exceeds the WCAG AA requirement of **4.5:1** for standard text and **3.0:1** for large text / graphical controls:

| Element & Text Token | Text Hex | Background Surface | Background Hex | Contrast Ratio | WCAG AA Status |
|---|---|---|---|---|---|
| Primary Text (`--ink`) | `#0F1B33` | Page Background | `#D5DCE8` | **12.4:1** | PASS (AAA) |
| Primary Text (`--ink`) | `#0F1B33` | Glass Card (`--glass-bg`) | Composite `~#EAF0F8` | **14.9:1** | PASS (AAA) |
| Secondary Text (`--ink-2`) | `#2E3D5C` | Page Background | `#D5DCE8` | **7.9:1** | PASS (AAA) |
| Secondary Text (`--ink-2`) | `#2E3D5C` | Glass Card (`--glass-bg`) | Composite `~#EAF0F8` | **9.4:1** | PASS (AAA) |
| Muted Text (`--muted`) | `#4A5A78` | Page Background | `#D5DCE8` | **5.0:1** | PASS (AA) |
| Muted Text (`--muted`) | `#4A5A78` | Glass Card (`--glass-bg`) | Composite `~#EAF0F8` | **6.0:1** | PASS (AA) |
| Indigo Links (`--indigo`)| `#27337A` | Page Background | `#D5DCE8` | **8.3:1** | PASS (AAA) |
| Indigo Links (`--indigo`)| `#27337A` | Glass Card (`--glass-bg`) | Composite `~#EAF0F8` | **9.9:1** | PASS (AAA) |
| Amber Text (`--amber-text`)| `#8A4B08`| Amber Chip (`--amber-chip`)| `#F7E2B8` | **5.4:1** | PASS (AA) |
| Amber Text (`--amber-text`)| `#8A4B08`| Glass Surface | Composite `~#EAF0F8` | **5.9:1** | PASS (AA) |
| Success Text (`--success`)| `#166534` | Success Chip (`--success-bg`)| `#C9EBD4` | **5.5:1** | PASS (AA) |
| Danger Text (`--danger`)  | `#9F1239` | Danger Chip (`--danger-bg`)| `#F8D5DC` | **5.9:1** | PASS (AA) |
| Info Text (`--info`)      | `#1D4ED8` | Info Chip (`--info-bg`)  | `#D6E2FB` | **5.2:1** | PASS (AA) |
| Primary Button Text       | `#0F1B33` | Amber Gradient Fill      | `#F2A93B` | **8.6:1** | PASS (AAA) |
| Skip Link / Inverted Pill | `#FFFFFF` | Deep Indigo (`--indigo`) | `#27337A` | **11.4:1**| PASS (AAA) |

> [!NOTE]
> `--amber` (`#F2A93B`) is strictly reserved for button fills and decorative accents. All amber-colored typography uses `--amber-text` (`#8A4B08`), guaranteeing high legibility.

---

## 🧊 Glassmorphism & Background Architecture

### Glass Recipe (`.glass`)
Cards, headers, tables, forms, and alerts share a cohesive frosted glass look:
```css
.glass {
  background: var(--glass-bg);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border: 1px solid var(--glass-border);
  border-radius: 16px;
  box-shadow: var(--glass-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.85);
  position: relative;
  overflow: hidden;
}
```

- **Specular Light Highlight**: Glass cards feature a subtle top-left specular highlight pseudo-element (`::before`) simulating ambient light reflection.
- **Graceful Fallback**: For browsers without `backdrop-filter` support, `@supports not` falls back to an opaque, tinted background: `rgba(246, 248, 252, 0.96)`.

### Layered Background Blobs
Fixed behind the content (`z-index: -1`, `pointer-events: none`), 3 large, low-opacity (0.24), heavily blurred (80px) colour blobs drift across the viewport:
- **Indigo Blob** (`#5B6CFF`, 540x540px) at top-left
- **Amber Blob** (`#F5B042`, 480x480px) at middle-right
- **Teal Blob** (`#4FD1C5`, 580x580px) at bottom-left

Because opacity is capped at 0.24 with 80px blur, background saturation never interferes with foreground text legibility.

---

## ⚡ "Living" Interface Motion

To deliver a calm, organic fintech feel without visual clutter:
1. **Ambient Drifting Blobs**: Blobs execute smooth, non-linear translate/scale cycles (30s to 42s durations).
2. **Tab Visibility Pausing**: When the user switches tabs, `document.hidden` pauses all background animations to conserve CPU cycles and battery.
3. **Staggered Entrance (`.animate-enter`)**: Cards and panels fade and rise gently upon page render using CSS `animation-delay: calc(var(--i, 0) * 60ms)`.
4. **Count-Up Animated Numbers**: Balance counters and audit totals dynamically count up from 0 to their target value over 700ms using a quartic ease-out function (`requestAnimationFrame`).
5. **Interactive Card Hover**: Cards lift (`translateY(-3px)`), elevate their shadow, and sweep a soft specular light sheen across the glass surface.
6. **Primary Button Sheen & Feedback**: The amber submit button features a soft light sweep on hover, scale feedback (`scale(0.98)`) on press, and an inline spinner while the request is in flight.
7. **Breathing Status Dot**: Successful transactions display a pulsing green breathing dot (3-second cycle).
8. **Interactive Vault Dial**: The logo dial ring rotates continuously at a stately 60-second period, accelerating into a 600ms vault spin when hovered.
9. **Reduced Motion Accessibility**: All motion animations are wrapped inside `@media (prefers-reduced-motion: no-preference)`. When users configure reduced motion, the UI renders instantly and static counters are presented.

---

## 🖥️ Pages & User Flows

### 1. Dashboard (`/`)
- **User Selector**: Header dropdown populated dynamically from `GET /api/v1/users`. Remembers the selected user in `localStorage` (`vaultcore-selected-user`).
- **Asset Balance Cards**: Real-time display of credit balances across all active assets with dedicated vector asset icons:
  - 🪙 **Gold Coins**: Metallic gold coin badge with coin notch.
  - 💎 **Diamonds**: Precision faceted blue gem badge.
  - ⭐ **Loyalty Points**: Vibrant emerald star badge.
- **Monospace Tabular Values**: Balances format numbers with monospace tabular glyphs and thousands separators.
- **Recent Activity**: Displays the 5 most recent transactions with asset name, asset code subtitle, credit/debit indicators, breathing status dots, and local timestamps.

### 2. New Transaction (`/transactions/new`)
A form allowing users to execute `TOPUP`, `BONUS`, or `SPEND` transactions:
- **Segmented Type Control**: Modern iOS/macOS style segmented radio control (`TOPUP (Credit)`, `BONUS (Grant)`, `SPEND (Debit)`).
- **Accessible Form Controls**: 44px minimum touch targets, 2px focus ring with translucent indigo halo, and inline validation alerts.
- **Client Idempotency Key**: Active UUID displayed with a "New Key" regeneration button.
- **Asynchronous Execution**: Dispatches via `fetch` to `POST /api/v1/transactions` with the submit button locked in a loading state.
- **Distinct Outcome Alerts**:
  - `HTTP 201 Created`: Green banner announcing "Transaction Completed Successfully" with transaction ID, amount, and updated balance (`balanceAfter`).
  - `HTTP 200 OK`: Amber banner alerting "Idempotent Replay (Previously Processed)", proving that the request was processed earlier and returning the exact historical balance at that instant.
  - `HTTP 400 / 404`: Red error banner detailing the server's rejection reason (e.g. `Insufficient balance`, `User not found`).
  - `Network Failure`: Red error banner explaining connection loss, preserving the idempotency key for safe retries.

### 3. Transaction History (`/history`)
- **Complete User Log**: Tabular view of all ledger transactions touching accounts owned by the selected user.
- **Visual Distinctions**: Zebra striping, sticky table headers, colored transaction type badges, credit (`+`) vs debit (`-`) indicators, and local datetime formatting via JavaScript.
- **Pill Pagination**: Glass pill pagination controls supporting server-side pagination with "Previous" and "Next" buttons.

### 4. Ledger Integrity & Audit (`/audit`)
- **System Invariant Check**: Proves in real time that &sum; Balances across all accounts equals 0.
- **Treasury Overview**: Displays the negative balance of system accounts for each asset, representing total circulating liabilities.
- **Full Account Reconciliation**: Tabular comparison of every account's cached balance against the ledger sum (`SELECT SUM(amount)`). Reconciled rows display verified chips: `✓ Reconciled` (green) or `✕ Mismatch` (red).

### 5. Error Pages (`/error/404`, `/error/error`)
- Server-rendered 404 and 500 error pages sharing the standard VaultCore layout, providing navigation back to the dashboard or API documentation.

---

## 🛡️ Browser Idempotency Handling

To prevent duplicate credits or debits from network failures, page refreshes, or repeated button clicks:

```
[Form Loads]
     │
     ▼
Generate UUID (crypto.randomUUID) ──► Stored in #idempotencyKey
     │
     ▼
User Clicks "Submit Transaction" ──► Button Disabled (Loading Spinner)
     │
     ▼
fetch('/api/v1/transactions')
     │
     ├───► [Network Error / Connection Lost]
     │         Show Retry Alert with explanation
     │         PRESERVE active idempotency key
     │         User clicks Retry ──► Exact same key is reused safely!
     │
     ├───► [HTTP 201 Created (Success)]
     │         Show Success Alert with balanceAfter
     │         Generate NEW idempotency key for next transaction
     │
     ├───► [HTTP 200 OK (Idempotent Replay)]
     │         Show Replay Alert with historical balanceAfter
     │         Generate NEW idempotency key for next transaction
     │
     └───► [HTTP 400 / 404 (Rejection)]
               Show Error Alert with server message
               Generate NEW idempotency key so user can correct and retry
```

---

## ♿ Accessibility Standards
- **Color Scheme Meta**: `<meta name="color-scheme" content="light">` and `color-scheme: light` on `:root` ensure all browser native elements (scrollbars, autofill) align with the light theme.
- **Keyboard Navigation**: Full tab ordering, visible high-contrast focus rings (`:focus-visible`), and skip-to-content links.
- **ARIA Live Regions**: Dynamic status alerts (`#outcomeAlert`) use `role="alert"` and `aria-live="polite"` so screen readers immediately announce transaction results.
- **Mobile Responsive**: Fully responsive grid layout with horizontal scrolling navigation on viewports below 640px.
- **Strict Output Escaping**: All dynamic data is escaped through standard Thymeleaf `th:text`. No unescaped `th:utext` is permitted.
