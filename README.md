# BudgetFlow

A modern Android budget tracking app built with Jetpack Compose, featuring recurring budget sources, automatic savings carry-forward, and an AI assistant for natural-language budget/transaction entry.

---

## Features

- **Home** — Daily transaction feed, sortable by time or amount, tap any entry for full details
- **Budget** — Donut chart visualizing spend vs. budget across Daily/Weekly/Monthly/Yearly views, with one card per income source
- **Assistant** — Chat (text or voice) with an AI agent that can add transactions, create budget sources, and answer spending questions in plain language
- **Analytics** — Analytics is a placeholder for future charts
- **Settings** — Settings holds the Gemini API key and backend server URL

### Budget engine

Three kinds of money are tracked, computed entirely client-side:

| Pool | Behavior |
|---|---|
| **Lump Sum** | One-time income (bonus, gift, bet winnings). Active for the day it's created, then expires. |
| **Timely Template** | Recurring income (salary, pocket money). Never counted directly — it spawns **slots**. |
| **Timely Slot** | One per period (minute/day/week/month/year), auto-generated from a template. Counted in the budget while its period is active. |
| **Savings** | A running total. When a Lump Sum or Slot expires with money left unspent, the leftover moves here automatically. |

Deleting a recurring source removes the template (stopping future slots) and its **currently active** slot — but past, already-expired slots stay untouched, preserving historical accuracy (e.g. you can stop a salary mid-year without erasing the months you were actually paid).

If the app is closed across multiple period boundaries (e.g. closed at 8:27, reopened at 8:29 with a per-minute budget), the recurrence engine backfills every missed period on next launch so nothing is silently lost.

---

## Project structure

```
BudgetFlow/
└── app/                          Android app (Kotlin + Jetpack Compose)
    └── src/main/java/com/example/budgetflow/
        ├── MainActivity.kt           App entry point, navigation, top-level state
        ├── Models.kt                 Data models + budget recurrence engine
        ├── DataStore.kt              Persistence (transactions, budget, settings)
        ├── SecureStorage.kt          Encrypted storage for the Gemini API key
        ├── HomeScreen.kt             Transaction feed
        ├── BudgetScreen.kt           Budget chart + source cards
        ├── BudgetForm.kt             Add budget source dialog
        ├── TransactionForm.kt        Add transaction dialog
        ├── TransactionDetailScreen.kt
        ├── AssistantScreen.kt        Chat UI + voice input
        ├── AssistantViewModel.kt     Chat state, calls backend, executes actions
        ├── AssistantRepository.kt    HTTP client for the backend
        ├── SettingsScreen.kt         API key + server URL configuration
        ├── AnalyticsScreen.kt        (placeholder)
        └── PopUpForm.kt              Routes the [+] FAB to the right form
```

> **Note:** The AI agent backend (Node.js + LangChain) that powers the Assistant tab lives in a **separate repository**: [BudgetFlowServer](https://github.com/Jiten-JTB/BudgetFlowServer.git). It must be set up and running for the Assistant tab to work — see that repo's README for setup instructions.

---

## Setup

### 1. Backend (required for the Assistant tab)

The AI agent backend is **not part of this repository**. Clone and run it separately:

```
git clone https://github.com/Jiten-JTB/BudgetFlowServer.git
```

Follow the setup instructions in that repo's README (install, configure, and start the server). Once running, note the URL it's served on — you'll need it in step 3 below.


### 2. Android app

Add these to **`app/build.gradle.kts`**:

```kotlin
plugins {
    // ...existing plugins
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

dependencies {
    // ...existing dependencies

    // Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Encrypted storage for the API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Assistant ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
}
```

Add to **`AndroidManifest.xml`**, inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

And inside `<manifest>`:

```xml
<queries>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

Sync Gradle, then build and run.

### 3. Configure the Assistant

1. Get a free Gemini API key at [aistudio.google.com](https://aistudio.google.com)
2. In the app, open **Settings**
3. Paste the API key and the backend server URL
4. Tap **Save Settings**
5. Open the **Assistant** tab and start chatting

---

## How the Assistant works

```
 You type/speak             Backend (Node.js)          Gemini 2.5 Flash
 "I bought ice    ──POST──▶  LangChain agent  ──────▶  decides which
  cream for 50"      /chat   picks a tool                    tool(s) to call
                                  │
                                  ▼
                         add_transaction tool
                         returns structured JSON
                                  │
         ◀── { reply, actions[] } ┘
 Android applies the
 action to local state
 (Transaction/BudgetEntry)
```

- The **API key never touches the backend's disk** — it's sent fresh with each request via the `Authorization: Bearer` header and used only for that call.
- The backend has no database; your transactions and budget data stay on your phone and are sent as context with each message so the agent can answer questions like "how much did I save in May."
- Multi-item messages ("bought ice cream for 50, milk for 40") result in multiple `add_transaction` tool calls — one per item.

See the backend repo's README [BudgetFlowServer](https://github.com/Jiten-JTB/BudgetFlowServer.git) for the full request/response schema and the list of supported action types.

---

## Tech stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Local persistence | Jetpack DataStore (Preferences) |
| Secure storage | EncryptedSharedPreferences (AES-256-GCM, Android Keystore-backed) |
| Serialization | kotlinx.serialization |
| Voice input | Android `SpeechRecognizer` |
| AI backend | Node.js, Express, TypeScript |
| AI framework | LangChain (agent) |
| LLM | Google Gemini 2.5 Flash |

---

## Known limitations / next steps

- **Analytics tab** is currently a placeholder — charts and trend views are planned
- `EVERY_MINUTE` budget frequency exists mainly for testing/demoing the recurrence engine quickly; real-world use cases are Daily through Yearly