<div align="center">

# 🌿 EcoDrive
### AI-Powered Vehicle Emissions Estimation & Eco-Driving Recommendation System

[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-brightgreen?style=for-the-badge&logo=android)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Language-Java%208-orange?style=for-the-badge&logo=java)](https://www.java.com)
[![API](https://img.shields.io/badge/Min%20SDK-API%2024-blue?style=for-the-badge)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Gradle%208.13-red?style=for-the-badge&logo=gradle)](https://gradle.org)
[![MediaPipe](https://img.shields.io/badge/AI-MediaPipe%20GenAI-yellow?style=for-the-badge)](https://developers.google.com/mediapipe)

<br/>

**EcoDrive** is a physics-grounded, AI-augmented Android application that delivers real-time, personalised vehicular CO₂ emission estimation and intelligent eco-driving recommendations — entirely without paid APIs or additional hardware.

[Features](#-features) • [Architecture](#-architecture) • [Installation](#-installation) • [How It Works](#-how-it-works) • [Screenshots](#-screenshots) • [Tech Stack](#-tech-stack) • [Contributing](#-contributing)

</div>

---

## 🚗 The Problem

India's vehicular fleet crossed **320 million registered vehicles** in 2024, with road transport contributing ~13% of national CO₂ emissions. Yet no consumer-grade mobile app exists that gives an individual driver accurate, real-time, personalised insight into the environmental cost of their trip.

Existing tools are either:
- 📊 **Fleet-aggregate dashboards** — not individual trip-level (VAHAN)
- 🔒 **Opaque black-box models** — no CO₂ number shown (Google Maps)
- 🖥️ **Desktop-only software** — inaccessible to everyday users (COPERT)
- 🔌 **Hardware-dependent** — require OBD-II dongles (Torque Pro)

**EcoDrive solves all five gaps in a single, free, open-source Android app.**

---

## ✨ Features

### 🔬 Physics-First Emission Engine
- Full **Newtonian vehicle dynamics model** — rolling resistance, aerodynamic drag, grade resistance, inertial force
- Supports **4 powertrain types**: Petrol, Diesel, CNG, Electric Vehicle (EV)
- Calibrated to **Indian BS-VI fuel standards** and CEA national grid carbon intensity (0.716 kg CO₂/kWh)
- **100+ Indian vehicles** in a bundled CSV dataset with BHP-derived physics parameters

### 📊 Monte Carlo Probabilistic Modelling
- **N = 50 stochastic iterations** per estimation — Gaussian perturbations on speed, headwind, road gradient
- Every result comes with a **90% confidence interval** — no false precision
- **SensitivityAnalysisActivity** runs N = 2,000 iterations per parameter sweep across speed, acceleration, traffic, AC load, terrain, and road grade

### 🌐 Zero-Cost 5-API Sensor Fusion
All APIs are **completely free** — no subscription, no paid tier:

| API | Data Provided |
|-----|---------------|
| [Open-Meteo](https://open-meteo.com) | Temperature, wind speed, humidity → air density ρ |
| [OSRM](https://project-osrm.org) | Route distance, duration, steps, alternatives |
| [Nominatim](https://nominatim.org) | Text → lat/lon geocoding |
| [Open Elevation](https://open-elevation.com) | 3-point elevation → terrain classification |
| [Overpass API](https://overpass-api.de) | Live road type, land use, traffic signal count |

### 🤖 Dual-LLM AI Architecture
- **Tier 1 — On-Device** (MediaPipe GenAI): 8 configurable quantised models, works completely offline
- **Tier 2 — Cloud Fallback**: Google Gemini or Anthropic Claude (user-configurable, AES-encrypted keys)
- **AutonomousAgent**: Weekly personalised telemetry reports with structured AI confidence scoring (`[CONFIDENCE: N]`)

#### Supported On-Device Models
| Model | Size | Best For |
|-------|------|----------|
| Gemma 3-1B IT | ~600 MB | Low-end devices, fastest |
| Gemma 3n-E2B IT | ~1.2 GB | **Default** — best balance |
| Gemma 3n-E4B IT | ~2.4 GB | Higher quality, mid-range |
| Gemma 4-E2B IT | ~1.4 GB | Latest generation |
| Gemma 4-E4B IT | ~2.8 GB | Flagship devices |
| Gemma 2B IT | ~1.35 GB | Broad compatibility |
| Phi-2 (Microsoft) | ~1.6 GB | Strong reasoning |
| Falcon-7B | ~4.2 GB | Max capability |

### 🗺️ Multi-Route CO₂ Comparison
- Fetches up to **3 alternative routes** from OSRM
- **DynamicRouteAnalyzer** classifies each road segment (motorway/primary/secondary/residential) using a 3-tier heuristic tuned to Indian road naming (NH/SH references)
- Routes ranked by estimated CO₂ on an **OpenStreetMap tile map** with colour-coded polylines

### 📱 Complete Android App
- Live **GPS foreground tracking** (FusedLocationProvider)
- **TripContextEngine** state machine: `DRIVING → IDLING → PREDICTED_OFF`
- **Trip History Dashboard** — Today / Week / Month / All Time, filtered by fuel type
- **PDF Report Export** via Android FileProvider
- **PlaceLearningManager** — frequent destination auto-complete

---

## 🏗️ Architecture

EcoDrive is organised into **6 distinct layers** with strict separation of concerns:

```
┌─────────────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER                                                 │
│  MainActivity | DashboardActivity | AIChatActivity                  │
│  RouteComparisonActivity | SensitivityAnalysisActivity              │
│  SettingsActivity | DeveloperActivity                               │
├─────────────────────────────────────────────────────────────────────┤
│  SERVICE LAYER (Background)                                         │
│  EcoDriveTrackingService | LocationTracker | TripContextEngine      │
├─────────────────────────────────────────────────────────────────────┤
│  AI / ML LAYER                                                      │
│  DualLLMRouter | OnDeviceLLMManager | EmissionsMLModel              │
│  TripAIAssistant | AutonomousAgent                                  │
├─────────────────────────────────────────────────────────────────────┤
│  PHYSICS ENGINE LAYER (Monte Carlo Core)                            │
│  DynamicVehicleEmissions | PhysicsFeatureSet | VehicleSpecs         │
│  TripResult | SensitivityAnalysisActivity (N=2000)                  │
├─────────────────────────────────────────────────────────────────────┤
│  DATA / PERSISTENCE LAYER                                           │
│  EcoDatabase (Room/SQLite) | TripDao | HistoryManager               │
│  VehicleDatabase (CSV) | PlaceLearningManager | SecurityManager     │
├─────────────────────────────────────────────────────────────────────┤
│  EXTERNAL API LAYER (All Free / Open-Source)                        │
│  OSRM | Open-Meteo | Overpass API | Nominatim | Open Elevation      │
│  Google Gemini (opt.) | Anthropic Claude (opt.) | HuggingFace LiteRT│
└─────────────────────────────────────────────────────────────────────┘
```

---

## ⚙️ How It Works

### The Emission Formula

For each of **N = 50 Monte Carlo iterations**, EcoDrive computes:

```
F_total  =  F_roll  +  F_aero  +  F_grade  +  F_acc

where:
  F_roll   =  m · g · C_rr
  F_aero   =  ½ · ρ · A_f · C_d · v_apparent²
  F_grade  =  m · g · sin(arctan(grade% / 100))
  F_acc    =  m · a
```

The final CO₂ for each iteration after ML telemetry fusion:

```
CO₂_final  =  (CO₂_base × Φ_driving × k_terrain) / F_AC  +  CO₂_idle

where:
  Φ_driving  =  1.0 + (λ_accel × Δv) + λ_jerk     (driver aggression coefficient)
  k_terrain  ∈  {1.00, 1.01, 1.03, 1.06, 1.12}    (terrain multiplier)
  F_AC       =  0.85 when AC active, else 1.0       (AC thermal penalty)
  CO₂_idle   =  t_idle × 0.8 L/hr × EF             (explicit traffic idle emissions)
```

Results: **mean CO₂**, **5th–95th percentile confidence interval**, mean fuel, mean energy.

### Emission Factors (Indian Standards)

| Fuel | Calorific Value | CO₂ Factor |
|------|----------------|------------|
| Petrol | 32 MJ/L | 2.31 kg/L |
| Diesel | 36 MJ/L | 2.68 kg/L |
| CNG | 47.7 MJ/kg | 2.75 kg/kg |
| EV | — | 0.716 kg/kWh (Indian grid, CEA 2023) |

---

## 📲 Installation

### Prerequisites
- Android Studio **Hedgehog** or later
- JDK 8+
- Android device / emulator with **API Level 24+** (Android 7.0+)
- Gradle 8.13

### Clone & Build

```bash
git clone https://github.com/yourusername/ecodrive.git
cd ecodrive
```

Open in Android Studio → **Sync Gradle** → **Run**

### Optional: On-Device LLM Setup

1. Open the app → **Developer Activity** (menu icon)
2. Enter your [HuggingFace access token](https://huggingface.co/settings/tokens) (required for Gemma 3n/4 gated models)
3. Select your preferred model from the dropdown
4. Tap **Download Model** — progress shown in real time
5. Model is cached locally; no re-download needed

> **Note:** Gemma 3-1B and Gemma 2B-IT are publicly accessible (no HuggingFace token needed).

### Optional: Cloud LLM Setup

1. Open **Settings** → **AI Provider**
2. Paste your **Google Gemini API key** (get it free at [ai.google.dev](https://ai.google.dev)) or your **Anthropic Claude API key**
3. Keys are stored using **AES encryption** — never in plaintext

---

## 📸 Screenshots

| Main Calculator | Route Comparison | AI Chat |Developer Logs| Sensitivity Analysis | Trip Dashboard | Settings |
<img width="1440" height="3168" alt="Screenshot_20260505_112616 jpg" src="https://github.com/user-attachments/assets/cd85fade-e151-4469-a881-f578149948ef" />
<img width="1440" height="3168" alt="Screenshot_20260505_112608 jpg" src="https://github.com/user-attachments/assets/3eaf57e5-1873-4a2a-88ba-7cc6ac736bbd" />
<img width="1440" height="3168" alt="Screenshot_20260505_112602 jpg" src="https://github.com/user-attachments/assets/e03b6755-e40c-4155-bb88-97a30cde219f" />
<img width="1440" height="3168" alt="Screenshot_20260505_112553 jpg" src="https://github.com/user-attachments/assets/4456508c-9624-4b6f-ac7d-74e7726ed085" />
<img width="1440" height="3168" alt="Screenshot_20260505_111755 jpg" src="https://github.com/user-attachments/assets/75f813dc-f9e3-4c4f-9725-afa81ad9d6fa" />
<img width="1440" height="3168" alt="Screenshot_20260505_111023 jpg" src="https://github.com/user-attachments/assets/aa7cc0ef-40c0-41dd-bdc2-d46efccbd092" />
<img width="1440" height="3168" alt="Screenshot_20260505_103513 jpg" src="https://github.com/user-attachments/assets/0cfa5546-653d-43df-b935-fecac0f8a38b" />
<img width="1440" height="11387" alt="Screenshot_2026_0505_112544 jpg" src="https://github.com/user-attachments/assets/3d7ef8a0-04f8-4e45-bdd6-aaa2d4fd5d07" />
<img width="1440" height="5282" alt="Screenshot_2026_0505_112439 jpg" src="https://github.com/user-attachments/assets/b0a6f1f7-7442-412e-84ab-373460b2dac3" />
<img width="1440" height="12627" alt="Screenshot_2026_0505_112350 jpg" src="https://github.com/user-attachments/assets/d045a750-c26f-41f7-b198-ddba751b196a" />
<img width="1440" height="10936" alt="Screenshot_2026_0505_112107 jpg" src="https://github.com/user-attachments/assets/3b1f9be4-9f22-444f-9bd0-c0ac7330d830" />
<img width="1440" height="11991" alt="Screenshot_2026_0505_103559 jpg" src="https://github.com/user-attachments/assets/f40437dd-7287-42c2-91c5-b22972483c75" />

---

## 🧪 Sample Results

Validated against **ARAI BS-VI certification benchmarks**:

| Vehicle | Scenario | Mean CO₂ | 90% CI | g/km |
|---------|----------|-----------|--------|------|
| Maruti Swift 1.2L Petrol | 12 km urban, high traffic, AC ON | 1.82 kg | [1.65, 2.01] | 151.7 |
| Maruti Swift 1.2L Petrol | 12 km urban, low traffic, AC OFF | 1.31 kg | [1.19, 1.44] | 109.2 |
| Mahindra XUV700 Diesel 2.2L | 25 km highway, AC ON | 3.12 kg | [2.90, 3.35] | 124.8 |
| Tata Nexon EV | 20 km urban, AC ON | 2.87 kg* | [2.51, 3.24] | 143.5 |
| Honda City CNG 1.5L | 15 km rolling terrain | 2.21 kg | [2.05, 2.38] | 147.3 |
| Generic Petrol SUV | 10 km hilly, aggressive | 2.48 kg | [2.18, 2.78] | 248.0 |

*\*EV CO₂ represents Indian grid electricity carbon, not tailpipe*

> The Maruti Swift urban result (151.7 g/km) is higher than ARAI's certification figure of 113 g/km — this is **physically expected** and **intentional**: ARAI certification uses the MIDC lab cycle with no AC, no traffic, and no real headwinds. EcoDrive models actual conditions.

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 8 |
| Min SDK | API 24 (Android 7.0) |
| Target SDK | API 34 (Android 14) |
| Build | Gradle 8.13 / AGP 8.13.2 |
| UI | Material Design 3 |
| Maps | osmdroid 6.1.18 (OpenStreetMap) |
| Charts | MPAndroidChart v3.1.0 |
| On-Device AI | MediaPipe GenAI 0.10.20 |
| Model Format | INT4 Quantised `.task` / `.bin` (LiteRT) |
| Model Hub | HuggingFace litert-community |
| Database | Room 2.6.1 (SQLite) |
| GPS | Google Play Services Location 21.0.1 |
| Security | AES Encryption (SecurityManager) |
| Weather | Open-Meteo REST API (free) |
| Routing | OSRM (open-source, free) |
| Geocoding | Nominatim / OSM (free) |
| Road Context | Overpass API / OSM (free) |
| Terrain | Open Elevation API (free) |
| Cloud AI | Google Gemini / Anthropic Claude (optional, user key) |

---

## 📁 Project Structure

```
ecodrive/
├── app/src/main/java/com/ecodrive/
│   ├── ui/                          # Android Activities (Presentation Layer)
│   │   ├── MainActivity.java        # Primary emission calculator
│   │   ├── DashboardActivity.java   # Trip history & analytics
│   │   ├── AIChatActivity.java      # Dual-LLM chat interface
│   │   ├── RouteComparisonActivity.java
│   │   ├── SensitivityAnalysisActivity.java
│   │   ├── SettingsActivity.java
│   │   └── DeveloperActivity.java   # LLM model management
│   │
│   ├── physics/                     # Physics Engine Layer
│   │   ├── DynamicVehicleEmissions.java  # Monte Carlo engine
│   │   ├── PhysicsFeatureSet.java
│   │   ├── VehicleSpecs.java
│   │   └── TripResult.java
│   │
│   ├── ml/                          # AI / ML Layer
│   │   ├── EmissionsMLModel.java    # Telemetry fusion
│   │   ├── DualLLMRouter.java       # LLM routing logic
│   │   ├── OnDeviceLLMManager.java  # MediaPipe GenAI
│   │   ├── TripAIAssistant.java
│   │   └── AutonomousAgent.java     # Weekly AI reports
│   │
│   ├── tracking/                    # Service Layer
│   │   ├── EcoDriveTrackingService.java
│   │   ├── LocationTracker.java
│   │   └── TripContextEngine.java   # DRIVING/IDLING state machine
│   │
│   ├── api/                         # Context & Routing Layer
│   │   ├── TelemetryFetcher.java    # 5-API sensor fusion
│   │   ├── ContextualDataFetcher.java  # Overpass API
│   │   ├── DynamicRouteAnalyzer.java   # Segment-level CO₂
│   │   └── RouteComparator.java
│   │
│   └── data/                        # Data / Persistence Layer
│       ├── EcoDatabase.java         # Room database
│       ├── TripEntity.java
│       ├── TripDao.java
│       ├── VehicleDatabase.java     # CSV vehicle dataset
│       ├── HistoryManager.java
│       ├── PlaceLearningManager.java
│       └── SecurityManager.java     # AES key encryption
│
├── app/src/main/assets/
│   └── final_cars_dataset.csv       # 100+ Indian vehicles
│
└── app/src/main/res/                # Layouts, drawables, strings
```

---

## 🔑 Key Design Decisions

**Why physics-first instead of ML regression?**
A regression model needs thousands of labelled real-world trip records per vehicle type — data that doesn't exist freely for Indian vehicles. The physics model needs only standard manufacturer specs, is fully interpretable, and every output number traces back to a physical force.

**Why Monte Carlo instead of deterministic?**
Real-world driving is stochastic. A 90% confidence interval is scientifically honest — it tells users not just the expected emission but the realistic range, unlike the false precision of single-point calculators.

**Why on-device LLM first?**
4G coverage in India is extensive but not ubiquitous, especially in Uttarakhand, Himachal, and the North-East. An eco-driving assistant that fails when connectivity drops isn't viable. On-device inference guarantees a useful response regardless of network state.

**Why all-free APIs?**
Sustainability tools must themselves be sustainable. A zero-variable-cost API stack means EcoDrive can be deployed and used at scale without requiring any commercial subscription from users.

---

## 🔭 Roadmap

- [ ] OBD-II Bluetooth integration (ELM327) — reduce uncertainty from ±15% to ±5%
- [ ] State-wise + time-of-day Indian grid intensity for EV accuracy
- [ ] IMU-based jerk computation for real-time driver aggressiveness scoring
- [ ] Full elevation profile along route (per-vertex SRTM queries)
- [ ] Live traffic integration (OSM OpenTrafficData / HERE Traffic)
- [ ] Fleet management multi-user Firebase backend
- [ ] Federated learning for personalised Φ_driving coefficients
- [ ] Carbon credit gamification layer
- [ ] iOS port (React Native / Flutter + MediaPipe iOS bindings)

---

## 👥 Team

| Name | Enrollment |
|------|-----------|
| Ronit Bhattacherjee | R2142230402 |
| Harsh Vardhan Sharma | R2142230391 |
| Arin Bansal | R2142231615 |
| Aditya Agarwal | R2142231136 |

**Project Guide:** Mr. Kaustubh Ijardar, Assistant Professor, School of Computer Science, UPES Dehradun

**Institution:** University of Petroleum and Energy Studies (UPES), Dehradun
**Academic Year:** 2023–2027

---

## 🙏 Acknowledgements

- [OSRM](https://project-osrm.org) — World-class open routing engine
- [OpenStreetMap](https://www.openstreetmap.org) contributors — Geographic data backbone
- [Open-Meteo](https://open-meteo.com) — Free, accurate weather API
- [Google MediaPipe](https://developers.google.com/mediapipe) — On-device LLM inference
- [HuggingFace litert-community](https://huggingface.co/litert-community) — Quantised model weights
- [ARAI](https://www.araiindia.com) & [CPCB](https://cpcb.nic.in) — Emission factor benchmarks
- [CEA India](https://cea.nic.in) — Grid carbon intensity data

---

## 📄 License

**EcoDrive Proprietary Source-Available License v1.0**

Copyright (c) 2025–2026 EcoDrive Core Team — UPES Dehradun

> This project is **NOT open-source**. It is **source-available** under a custom proprietary license.

| Permission | Status |
|------------|--------|
| ✅ Download & read source code | **Allowed** |
| ✅ Run on your own device | **Allowed** |
| ❌ Modify the source code | **Prohibited** — Core Team only |
| ❌ Distribute copies | **Prohibited** — Core Team only |
| ❌ Sell or commercialise | **Prohibited** — Core Team only |
| ❌ Create derivative works | **Prohibited** — Core Team only |
| ❌ Sublicense to others | **Prohibited** |

**The right to modify, distribute, sell, or build upon this software is reserved exclusively and permanently to the four named Core Team members.** The Core Team reserves the right to commercialise this project at any future date.

See the full [`LICENSE`](LICENSE) file for complete legal terms.

For commercial licensing enquiries: `[arinbansal02@gmail.com]`

---

<div align="center">

**Built with 🧠😎🤯 for a greener India 🌿**

*"The science of vehicle emission modelling deserves to be democratised, made transparent, and placed directly in the hands of every driver."*

</div>
