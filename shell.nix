let
  pkgs = import (fetchTarball {
     url = "https://github.com/NixOS/nixpkgs/archive/e3bbbf91cf661f0972fc1c2d3db526513f6e6229.tar.gz";
  }) {}; 
in
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

    postPatch = (oldAttr.postPatch or "") + ''
      substituteInPlace cmake/OpenCVGenPkgconfig.cmake \
        --replace "cmake_minimum_required(VERSION 2.8.12.2)" "cmake_minimum_required(VERSION 3.5)"
    '';

    buildInputs = (oldAttr.buildInputs or []) ++ [ ade ];
    
    nativeBuildInputs =
      oldAttr.nativeBuildInputs
      ++ (with pkgs; [
        ant
        openjdk
        # python3
        # python3Packages.numpy
      ]);
      
    cmakeFlags =
      oldAttr.cmakeFlags
      ++ [
        "-DBUILD_JAVA=ON"
        "-DBUILD_opencv_dnn=OFF"
        "-DBUILD_opencv_gapi=ON"
        "-DWITH_ADE=ON"
        "-Dade_DIR=${ade}/lib/cmake/ade"
        "-DBUILD_opencv_python2=OFF"
        "-DBUILD_opencv_python3=OFF"
        "-DOPENCV_GENERATE_TYPING_STUBS=OFF"
        "-DCMAKE_POLICY_VERSION_MINIMUM=3.5"
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

  buildInputs = with pkgs; [
    openjdk17
    cmake
    opencv4100
    clang
    lapack
    suitesparse
    pnpm
    re2
  ];
in
pkgs.mkShell {
  buildInputs = buildInputs;
  
  shellHook = ''

    export LD_LIBRARY_PATH=${pkgs.lib.makeLibraryPath buildInputs}:$LD_LIBRARY_PATH

    export LD_LIBRARY_PATH=${opencv4100}/share/java/opencv4:$LD_LIBRARY_PATH
    export JAVA_HOME=${pkgs.openjdk17}
  '';
}