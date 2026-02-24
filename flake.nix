{
  description = "null-mobile dev environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};

      fhsEnv = pkgs.buildFHSEnv {
        name = "null-mobile-env";
        targetPkgs = pkgs: with pkgs; [
          jdk21
          buf
          gradle
          zlib
          stdenv.cc.cc.lib
        ];
        profile = ''
          export JAVA_HOME="${pkgs.jdk21}"
          export ANDROID_HOME="$HOME/Android/Sdk"
          export GRADLE_USER_HOME="$PWD/.gradle-home"
          export PATH="$ANDROID_HOME/platform-tools:$PATH"
        '';
        runScript = "bash";
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        buildInputs = [ fhsEnv pkgs.buf ];

        shellHook = ''
          echo "Run 'null-mobile-env' to enter the FHS build environment"
          echo "Or use Android Studio which handles this natively"
        '';
      };
    };
}
