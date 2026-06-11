# macOS External Drive Setup Guide — KMP + Rust

This guide configures a **Kotlin Multiplatform (KMP)** development environment with a **Rust core** on macOS, storing all Rust and Android toolchain artifacts on an external drive named **DevDrive** mounted at `/Volumes/DevDrive`. This keeps your primary **Macintosh HD** free of large SDK and toolchain downloads.

> **Prerequisites**
> - macOS with Zsh as your default shell
> - External drive **DevDrive** mounted at `/Volumes/DevDrive`
> - Internet access for downloads

---

## Phase 1: Environment Variables and Directory Creation

Before installing Rust, create dedicated directories on the external drive and configure your shell profile so every tool installs to DevDrive instead of your home directory on the internal disk.

### 1.1 Create directories on DevDrive

```bash
mkdir -p /Volumes/DevDrive/.cargo
mkdir -p /Volumes/DevDrive/.rustup
```

| Directory | Purpose |
|-----------|---------|
| `/Volumes/DevDrive/.cargo` | Cargo registry, installed binaries (`cargo install`), and build cache |
| `/Volumes/DevDrive/.rustup` | Rustup toolchains, targets, and component data |

### 1.2 Add environment variables to `~/.zshrc`

Append the following block to your Zsh profile. These variables tell **rustup** and **cargo** to use the external drive for all Rust-related data:

```bash
cat >> ~/.zshrc << 'EOF'

# --- Rust / Cargo on DevDrive (external drive) ---
export CARGO_HOME="/Volumes/DevDrive/.cargo"
export RUSTUP_HOME="/Volumes/DevDrive/.rustup"
export PATH="$CARGO_HOME/bin:$PATH"
EOF
```

| Variable | Why it matters |
|----------|----------------|
| `CARGO_HOME` | Redirects Cargo's registry, cache, and globally installed crates to DevDrive |
| `RUSTUP_HOME` | Redirects Rust toolchains and targets to DevDrive |
| `PATH` | Ensures `rustc`, `cargo`, and `rustup` (installed via rustup) are found in your shell |

### 1.3 Apply changes to the current terminal session

Changes to `~/.zshrc` do not take effect until you reload the profile or open a new terminal:

```bash
source ~/.zshrc
```

Verify the variables are set:

```bash
echo "CARGO_HOME=$CARGO_HOME"
echo "RUSTUP_HOME=$RUSTUP_HOME"
```

Expected output:

```
CARGO_HOME=/Volumes/DevDrive/.cargo
RUSTUP_HOME=/Volumes/DevDrive/.rustup
```

> **Tip:** Always ensure DevDrive is mounted before opening a terminal or building the project. If the drive is unmounted, Rust commands will fail because `CARGO_HOME` and `RUSTUP_HOME` point to a non-existent path.

---

## Phase 2: macOS Build Prerequisites

Rust and native Android libraries rely on a C compiler and linker provided by Apple's developer tools. Install the **Xcode Command Line Tools** — this is strictly required for compiling C bindings and Rust code on macOS.

```bash
xcode-select --install
```

A GUI dialog will appear. Click **Install** and wait for the process to finish.

Verify installation:

```bash
xcode-select -p
```

Expected output (path may vary slightly by macOS version):

```
/Library/Developer/CommandLineTools
```

Confirm the C compiler is available:

```bash
clang --version
```

---

## Phase 3: Rust Toolchain Installation

Because `CARGO_HOME` and `RUSTUP_HOME` are already exported in your Zsh profile, the official Rust installer **automatically detects them** and installs all toolchains directly to `/Volumes/DevDrive` — not to `~/.cargo` or `~/.rustup` on your internal drive.

> **Important:** Run the installer only **after** completing Phase 1 and running `source ~/.zshrc`.

### 3.1 Install Rust via rustup

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

When prompted, choose the default installation profile (option **1**). The installer will write toolchains to `/Volumes/DevDrive/.rustup` and binaries to `/Volumes/DevDrive/.cargo/bin`.

Verify the installation:

```bash
rustc --version
cargo --version
rustup --version
```

Confirm toolchains live on DevDrive:

```bash
rustup show home
```

Expected output should reference `/Volumes/DevDrive/.rustup`.

### 3.2 Install Android cross-compilation targets

KMP Android builds require Rust libraries compiled for each Android ABI. Add all four standard Android targets:

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add i686-linux-android
rustup target add x86_64-linux-android
```

Or as a single command:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

List installed targets to confirm:

```bash
rustup target list --installed
```

### 3.3 Install cargo-ndk

**cargo-ndk** automates building native C libraries and linking them correctly for each Android ABI using the NDK. Install it as a global Cargo binary (it will be placed in `$CARGO_HOME/bin` on DevDrive):

```bash
cargo install cargo-ndk
```

Verify:

```bash
cargo ndk --version
```

---

## Phase 4: Android NDK Configuration

The **Android NDK (Native Development Kit)** provides the cross-compilers and sysroots that `cargo-ndk` uses to build Rust code for Android devices and emulators.

### 4.1 Download the Android NDK

Choose **one** of the following methods.

#### Option A — Android Studio SDK Manager (recommended)

1. Open **Android Studio**.
2. Go to **Settings / Preferences → Languages & Frameworks → Android SDK → SDK Tools**.
3. Check **NDK (Side by side)** and click **Apply** to download.

Note the installed NDK path. It is typically:

```
~/Library/Android/sdk/ndk/<version>/
```

If you want the NDK on DevDrive instead, use Option B or copy/symlink the downloaded NDK to DevDrive after installation.

#### Option B — Manual download to DevDrive

1. Download the NDK zip for macOS from the [Android NDK downloads page](https://developer.android.com/ndk/downloads).
2. Extract it to DevDrive:

```bash
mkdir -p /Volumes/DevDrive/android-ndk
```

Extract the downloaded zip (adjust the zip filename to match your download):

```bash
unzip ~/Downloads/android-ndk-r*-darwin.zip -d /Volumes/DevDrive/android-ndk
```

After extraction, note the full path to the NDK root directory. It will look like:

```
/Volumes/DevDrive/android-ndk/android-ndk-r26d
```

The NDK root is the directory that contains `ndk-build`, `toolchains/`, and `build/` subdirectories.

### 4.2 Set `ANDROID_NDK_HOME`

Append the NDK path to your Zsh profile. **Replace the path below** with your exact NDK directory:

```bash
cat >> ~/.zshrc << 'EOF'

# --- Android NDK on DevDrive ---
export ANDROID_NDK_HOME="/Volumes/DevDrive/android-ndk/android-ndk-r26d"
EOF
```

Apply the change:

```bash
source ~/.zshrc
```

Verify:

```bash
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
ls "$ANDROID_NDK_HOME/ndk-build"
```

The `ls` command should print the path to the `ndk-build` script without error.

> **Why this is required:** `cargo-ndk` reads `ANDROID_NDK_HOME` to locate the NDK toolchain when cross-compiling Rust libraries for Android. Without it, Android builds will fail with missing compiler or linker errors.

### 4.3 (Optional) Relocate Android SDK to DevDrive

If you also want the full Android SDK on DevDrive (not just the NDK), set these additional variables in `~/.zshrc`:

```bash
cat >> ~/.zshrc << 'EOF'

# --- Android SDK on DevDrive (optional) ---
export ANDROID_HOME="/Volumes/DevDrive/android-sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
EOF
```

Then install the SDK via Android Studio, pointing the SDK location to `/Volumes/DevDrive/android-sdk`, or use the [command-line SDK tools](https://developer.android.com/tools/sdkmanager).

---

## Verification Checklist

Run these commands to confirm your environment is fully configured:

```bash
# Rust on DevDrive
echo "CARGO_HOME=$CARGO_HOME"
echo "RUSTUP_HOME=$RUSTUP_HOME"
rustc --version

# Android targets
rustup target list --installed | grep android

# cargo-ndk
cargo ndk --version

# Android NDK
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
ls "$ANDROID_NDK_HOME/ndk-build"
```

All commands should succeed with no errors.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `rustc: command not found` | Run `source ~/.zshrc` or open a new terminal. Confirm `$CARGO_HOME/bin` is on your `PATH`. |
| Toolchains installed to `~/.cargo` instead of DevDrive | Remove `~/.cargo` and `~/.rustup`, ensure Phase 1 env vars are set, then re-run the rustup installer. |
| DevDrive not mounted | Mount DevDrive before opening terminals. Rust and NDK paths will be invalid if the drive is disconnected. |
| `cargo ndk` fails with NDK errors | Verify `ANDROID_NDK_HOME` points to the NDK **root** directory (the one containing `ndk-build`). |
| Xcode CLT missing | Re-run `xcode-select --install` and accept the license: `sudo xcodebuild -license accept`. |

---

## Summary of DevDrive Paths

| Component | Path on DevDrive |
|-----------|-----------------|
| Cargo home | `/Volumes/DevDrive/.cargo` |
| Rustup home | `/Volumes/DevDrive/.rustup` |
| Android NDK (manual install) | `/Volumes/DevDrive/android-ndk/android-ndk-r<version>` |
| Android SDK (optional) | `/Volumes/DevDrive/android-sdk` |
| This KMP project | `/Volumes/DevDrive/Repositories/KMM` |

With this setup, Rust toolchains, Cargo caches, and Android native dependencies live on your external drive, keeping Macintosh HD free for the OS and applications.
