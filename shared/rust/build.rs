fn main() {
    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os == "android" {
        link_android_cpp();
    }
}

fn link_android_cpp() {
    let ndk_home = std::env::var("ANDROID_NDK_HOME")
        .or_else(|_| std::env::var("NDK_HOME"))
        .expect("ANDROID_NDK_HOME must be set for Android builds");

    let abi = std::env::var("CARGO_CFG_TARGET_ARCH").unwrap();
    let abi_folder = match abi.as_str() {
        "aarch64" => "aarch64-linux-android",
        "arm" => "arm-linux-androideabi",
        "x86" => "i686-linux-android",
        "x86_64" => "x86_64-linux-android",
        other => panic!("Unsupported Android arch: {other}"),
    };

    let host_tag = if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") {
            "darwin-x86_64"
        } else {
            "darwin-x86_64"
        }
    } else if cfg!(target_os = "linux") {
        "linux-x86_64"
    } else {
        "darwin-x86_64"
    };

    let prebuilt = format!("{ndk_home}/toolchains/llvm/prebuilt/{host_tag}");
    let lib_dir = format!("{prebuilt}/sysroot/usr/lib/{abi_folder}");
    println!("cargo:rustc-link-search={lib_dir}");
    println!("cargo:rustc-link-lib=c++_shared");
}
