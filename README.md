# LanceDB Kotlin Multiplatform SDK

Community Kotlin Multiplatform wrapper for [LanceDB](https://lancedb.com/) using **UniFFI 0.28.3** and the [UbiqueInnovation uniffi-kotlin-multiplatform-bindings](https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings) Gradle plugin (`ch.ubique.uniffi.plugin`).

## Project layout

```
KMM/
├── shared/                 # KMP library + Rust crate (shared/rust)
├── androidApp/             # Jetpack Compose offline recipe search demo
└── tools/seed-data/        # Embedding + model download scripts
```

## Prerequisites

Complete [ENVIRONMENT_SETUP.md](ENVIRONMENT_SETUP.md). This project also uses:

- Toolchain under `/Volumes/DevDrive/Repositories/.cargo` and `.rustup` (see `gradle.properties`)
- Android SDK: `/Volumes/DevDrive/Android/sdk`
- Android NDK: `/Volumes/DevDrive/Android/sdk/ndk/26.2.11394342`
- JDK 17+ (Android Studio JBR configured in `gradle.properties`)
- `protoc` (`brew install protobuf`) for LanceDB Rust dependencies
- Python 3 + `sentence-transformers` to regenerate seed embeddings

## Build

```bash
source env.sh   # required in new terminals — sets CARGO_HOME, RUSTUP_HOME, NDK
cd /Volumes/DevDrive/Repositories/KMM
```

### Rust native library (arm64)

Use `--lib` so Cargo does not cross-compile the host-only `uniffi-bindgen` binary (that causes `pthread_atfork` linker errors on Android):

```bash
source env.sh
cd shared/rust
cargo ndk -t arm64-v8a -o ../build/ndk-arm64 build --package lancedb_kmp --lib
```

Output: `shared/build/ndk-arm64/arm64-v8a/liblancedb_kmp.so`

> **x86_64 emulators are not supported** — vendored OpenSSL fails to assemble SM3/SM4 on Android x86_64, and `ort` (fastembed) has no prebuilt ONNX Runtime for that target. Use a physical arm64 device or an **arm64-v8a** AVD on Apple Silicon.

### APK build

```bash
cd /Volumes/DevDrive/Repositories/KMM
./gradlew :shared:buildBindings
./gradlew :androidApp:assembleDebug
```

APK output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

## Demo data

```bash
pip install sentence-transformers
python3 tools/seed-data/generate_embeddings.py
./tools/seed-data/download_model.sh
```

Bundled assets:

- `androidApp/src/androidMain/assets/recipes_seed.json` — 25 recipes with 384-dim vectors
- `androidApp/src/androidMain/assets/models/` — MiniLM ONNX + tokenizer (offline query embedding)

## Airplane-mode demo

1. Install the APK on a physical **arm64** device.
2. Launch once (seeds DB + copies models to app storage).
3. Enable **Airplane Mode**.
4. Search: **spicy breakfast with eggs** — semantic matches (e.g. Shakshuka, Spicy Egg Tacos) with latency banner.

## Architecture

- **Rust** (`shared/rust`): LanceDB + fastembed, sync UniFFI surface over Tokio
- **Kotlin** (`shared`): UniFFI-generated bindings + `LanceDbRepository` (coroutines)
- **Android**: Compose UI → ViewModel → repository → native `liblancedb_kmp.so`
