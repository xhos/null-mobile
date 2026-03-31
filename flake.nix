{
  description = "null-mobile dev environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          android_sdk.accept_license = true;
          allowUnfree = true;
        };
      };

      androidSdk = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" ];
        buildToolsVersions = [ "36.0.0" ];
        includeNDK = false;
      };

      androidHome = "${androidSdk.androidsdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          (writeShellScriptBin "bump-protos" ''
            git -C proto fetch origin
            git -C proto checkout main
            git -C proto pull --ff-only
            git add proto
            git commit -m "chore: bump proto files"
            git push
          '')
          
          (writeShellScriptBin "regen" ''
            rm -rf app/src/main/java/dev/xhos/null_mobile/proto/
            ${buf}/bin/buf generate
          '')
        ];

        buildInputs = with pkgs; [ jdk21 buf gradle sqlc ];

        ANDROID_HOME = androidHome;
        JAVA_HOME = "${pkgs.jdk21}";
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidHome}/build-tools/36.0.0/aapt2";

        shellHook = ''
          export PATH="$ANDROID_HOME/platform-tools:$PATH"
          echo "sdk.dir=$ANDROID_HOME" > "$PWD/local.properties"
        '';
      };
    };
}
