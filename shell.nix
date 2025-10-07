{ pkgs ? import <nixpkgs> {} }:
let
  ade = pkgs.stdenv.mkDerivation rec {
    pname = "ade";
    version = "0.1.2e";
    
    src = pkgs.fetchFromGitHub {
      owner = "opencv";
      repo = "ade";
      rev = "v${version}";
      hash = "sha256-1z5ChmXyanEghBLpopJlRIjOMu+GFAON0X8K2ZhYVlA=";
    };
    
    nativeBuildInputs = [ pkgs.cmake ];
    
    cmakeFlags = [
      "-DBUILD_SHARED_LIBS=OFF"
    ];
  };


  opencv4100 = pkgs.opencv4.overrideAttrs (oldAttr: rec {
    version = "4.10.0";
    src = pkgs.fetchFromGitHub {
      owner = "opencv";
      repo = "opencv";
      rev = version;
      hash = "sha256-s+KvBrV/BxrxEvPhHzWCVFQdUQwhUdRJyb0wcGDFpeo=" ; 
    };

    buildInputs = (oldAttr.buildInputs or []) ++ [ ade ];
    
    nativeBuildInputs =
      oldAttr.nativeBuildInputs
      ++ (with pkgs; [
        ant
        openjdk
        python3
        python3Packages.numpy
      ]);
      
    cmakeFlags =
      oldAttr.cmakeFlags
      ++ [
        "-DBUILD_JAVA=ON"
        "-DBUILD_opencv_dnn=OFF"
        "-DBUILD_opencv_gapi=ON"
        "-DWITH_ADE=ON"
        "-Dade_DIR=${ade}/lib/cmake/ade"
      ];

    postInstall = (oldAttr.postInstall or "") + ''
      cd $out/lib
      for lib in libopencv_*.so.4.10.0; do
        if [ -f "$lib" ]; then
          base=$(basename "$lib" .so.4.10.0)
          ln -sf "$lib" "$base.so.4.10"
        fi
      done
    '';
  });
in
pkgs.mkShell {
  buildInputs = with pkgs; [
    openjdk21
    cmake
    opencv4100
    clang
    lapack
    suitesparse
    pnpm
  ];
  
  shellHook = ''
    export LD_LIBRARY_PATH=${opencv4100}/share/java/opencv4:${opencv4100}/lib:$LD_LIBRARY_PATH
  '';
}