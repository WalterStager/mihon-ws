{
  outputs = { self, nixpkgs }: let
    system = "x86_64-linux";
    pkgs = import nixpkgs {
      system = "${system}";
      config.allowUnfree = true;
      config.android_sdk.accept_license = true;
    };
    androidComposition = pkgs.androidenv.composeAndroidPackages {
      buildToolsVersions = [ "35.0.0" "36.0.0" ];
      platformVersions = [ "35" "36" "37" ];
      includeNDK = true;
      ndkVersions = [ "29.0.14206865" ];
      includeEmulator = false;
      includeSystemImages = false;
    };
    androidSdk = androidComposition.androidsdk;
  in {
    devShells.${system}.default = pkgs.mkShell {
      packages = [
        pkgs.jdk21_headless
        pkgs.git
        androidSdk
      ];
      ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
      ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
      ANDROID_NDK_ROOT = "${androidSdk}/libexec/android-sdk/ndk/29.0.14206865";
      shellHook = "echo 'mihon-ws dev activated'";
    };
  };
}
