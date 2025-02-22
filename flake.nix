{
  description = "YaCy -- distributed search engine";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = inputs: let
    systems = ["x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin"];
    forEachSystem = inputs.nixpkgs.lib.genAttrs systems;
    pkgsForEach = inputs.nixpkgs.legacyPackages;
  in {
    devShells = forEachSystem (system: {
      default = pkgsForEach.${system}.mkShell {
        packages = with pkgsForEach.${system}; [
          pkgs.ant
          pkgs.temurin-bin-11
        ];
      };
    });
  };
}
