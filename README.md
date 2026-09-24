# SplitMyPay: Google Wallet Expense Capture & Tricount Integration

**SplitMyPay** is an Android application that intercepts Google Wallet contactless NFC payments (and supported bank notifications), extracts the merchant and price, and facilitates instant splitting into Tricount without manual data entry.

Users can either 1-tap **"Quick Split"** directly from an interactive notification or open a sleek bottom sheet to customize the target Tricount group, payer, participants, allocation ratios (Equal, Custom Amounts, or Weights), and category.

The app interfaces directly with Tricount's native backend API (`api.tricount.bunq.com`) using device-level cryptographic session authentication (RSA 2048-bit key pairs), enabling background synchronization.

---

## Key Features

1. **Automatic Contactless Payment Interception**
   - Intercepts transactions from Google Wallet (`com.google.android.apps.walletnfcsic`) and Google Play Services (`com.google.android.gms`).
   - Supports popular banking apps: Revolut, N26, BBVA, Santander, CaixaBank, Wise, and Chase.
   - Heuristic parsing engine handles currency symbols (`€`, `$`, `£`, `¥`, `₹`, `CHF`, `CAD`, `AUD`) and varying decimal/thousands formats (`18,45 €`, `$4.50`, `1.299,00 €`, `1,299.00 €`).

2. **1-Tap Quick Split Notification**
   - When a payment is detected, a high-priority interactive notification is posted.
   - **Quick Split**: Immediately divides the expense equally among all members of the default Tricount group using the configured default payer, posting to Tricount via background broadcast receiver without launching the app.
   - **Customize**: Launches the customized splitting screen with pre-filled details.
   - **Dismiss**: Ignores the transaction and updates local history.

3. **Custom Split Screen**
   - Pre-filled Concept/Merchant and Amount.
   - Dynamic Tricount group selector.
   - Payer selection chip row.
   - **Three Split Modes**:
     - *Equal Split (Default)*: Interactive checkboxes with auto-calculated cent-exact shares.
     - *Custom Amounts*: Individual text inputs with live validation ensuring allocations balance to the cent.
     - *Weights / Shares*: Stepper multipliers (1x, 2x, etc.) for proportional allocation.
   - Category picker: `FOOD_AND_DRINK`, `GROCERIES`, `SHOPPING`, `TRANSPORT`, `ENTERTAINMENT`, `TRAVEL`, `OTHER`.

4. **Group Management & Clipboard Auto-Detection**
   - Import Tricount groups using full URLs (`https://tricount.com/t...`) or raw tokens (`tMjbqgw...`).
   - Automatic clipboard scanning on focus prompts to import detected Tricount links in 1 tap.
   - **"Which member is me?"**: Designates the user's identity within each group to establish default payer settings.

5. **Built-in Payment Simulator**
   - Test presets on the Home Screen (e.g., "Mercadona €18.45", "Starbucks $4.50") trigger real interactive notification prompts, enabling complete end-to-end testing without physical NFC terminals.

---

## Tech Stack & Architecture

- **UI**: Jetpack Compose BOM 2024.10.01, Material 3, Compose Navigation.
- **Local Persistence**: AndroidX Room 2.6.1 with KSP (Kotlin Symbol Processing).
- **Networking & Serialization**: OkHttp 4.12.0, Kotlinx Serialization JSON 1.7.3.
- **Cryptography**: Standard Java RSA 2048-bit `KeyPairGenerator`, X.509 PKCS#8 PEM formatting.
- **Asynchrony**: Kotlin Coroutines & Flow (StateFlow, Dispatchers.IO).
- **Minimum SDK**: Android 8.0 Oreo (API 26) • **Target SDK**: Android 15 (API 35).

---

## Tricount Bunq API Integration

The app communicates directly with `https://api.tricount.bunq.com`:

1. **Session Installation (`POST /v1/session-registry-installation`)**
   - Generates an RSA 2048-bit key pair and formats the public key as X.509 PEM:
     ```
     -----BEGIN PUBLIC KEY-----
     MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...
     -----END PUBLIC KEY-----
     ```
   - Registers device with `app_installation_uuid` and `User-Agent: com.bunq.tricount.android:RELEASE:7.0.7:3174:ANDROID:13:C`.
   - Extracts session authentication token (`X-Bunq-Client-Authentication`) and `UserPerson.id`.

2. **Registry Synchronization (`POST /v1/user/{userId}/registry-synchronization`)**
   - Imports active groups by public token and retrieves full member lists with aliases and nicknames.

3. **Expense Creation (`POST /v1/user/{userId}/registry/{tricountId}/registry-entry`)**
   - Adheres to Tricount's negative amount convention (`"-18.45"` total and negative member allocation shares).
   - Date format: `yyyy-MM-dd HH:mm:ss.SSSSSS`.

---

## Build & Test Instructions

### Run Unit Tests
```bash
./gradlew test
```
Executes all unit tests covering:
- `NotificationPaymentParserTest`: Google Wallet bullets, Google Play Services, Revolut, BBVA, N26, Wise, thousands separators, and non-payment filters.
- `SplitCalculationTest`: Cent-exact remainder distribution for 3, 6, and 1 member equal splits and weighted distributions.
- `TricountApiClientTest`: MockWebServer verification of RSA PEM registration payload, session extraction, and negative expense math.

### Build Debug APK
```bash
./gradlew assembleDebug
```
Generates the installable debug APK at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Setup on Device

1. Install `app-debug.apk` on an Android device running Android 8.0+.
2. Launch **SplitMyPay**.
3. Tap **"Enable in Settings"** on the notification banner to grant Notification Listener access.
4. Import your Tricount group link in the **Groups** tab and tap **"Set as Me"** next to your name.
5. Tap **"Mercadona €18.45"** under the Test Simulator on the Home screen to experience the complete flow!
