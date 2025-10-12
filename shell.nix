{ pkgs ? import <nixpkgs> {} }:
let
  onnxruntimeRocm = let
    rocprim' = pkgs.rocmPackages.rocprim.overrideAttrs (oldRocprim: {
      patches = (oldRocprim.patches or [] ) ++ [
        ./nix/patches/00-rocPRIM-nodiscard.patch
      ];
    });

    hipcub' = pkgs.rocmPackages.hipcub.overrideAttrs (oldHipcub: {
      patches = (oldHipcub.patches or [] ) ++ [
        ./nix/patches/01-hipCUB-nodiscard.patch
      ];
    });

    

  in pkgs.onnxruntime.overrideAttrs (old: rec {
    buildInputs = (old.buildInputs or []) ++ [
      pkgs.rocmPackages.rocblas
      pkgs.rocmPackages.miopen
      pkgs.rocmPackages.hipblas
      pkgs.rocmPackages.hipfft
      pkgs.rocmPackages.rocrand
      pkgs.rocmPackages.hiprand
      pkgs.rocmPackages.rccl
      pkgs.rocmPackages.roctracer
      pkgs.rocmPackages.rocm-smi
      pkgs.rocmPackages.hipsparse
      hipcub'
      rocprim'
      pkgs.rocmPackages.clr
      pkgs.rocmPackages.rocthrust
    ];

    nativeBuildInputs = (old.nativeBuildInputs or []) ++ [
      pkgs.rocmPackages.clr
      pkgs.perl
      pkgs.rocmPackages.hipify
    ];

    makeFlags = [ "VERBOSE=1" ];

    patches = (old.patches or []) ++ [
       ./nix/patches/02-onnxruntime-fix.patch
    ];

    preConfigure = (old.preConfigure or "") + ''
      export ROCM_PATH=${pkgs.rocmPackages.clr}
      export HIP_PATH=${pkgs.rocmPackages.clr}
      export HIP_PLATFORM=amd
      
      export CXXFLAGS="$CXXFLAGS -std=c++20 -Wno-deprecated-enum-float-conversion -Wno-error=deprecated-enum-float-conversion"
      export HIPFLAGS="$HIPFLAGS -std=c++20 -Wno-deprecated-enum-float-conversion -Wno-error=deprecated-enum-float-conversion"


      export CMAKE_CXX_FLAGS="$CXXFLAGS"
      export CMAKE_HIP_FLAGS="$HIPFLAGS"

      export CMAKE_PREFIX_PATH="${pkgs.rocmPackages.clr}:${pkgs.rocmPackages.rocblas}:${pkgs.rocmPackages.hiprand}:${pkgs.rocmPackages.rocrand}:${pkgs.rocmPackages.miopen}:${pkgs.rocmPackages.hipblas}:${pkgs.rocmPackages.hipfft}:${pkgs.rocmPackages.rccl}:${pkgs.rocmPackages.roctracer}:${pkgs.rocmPackages.rocm-smi}:${pkgs.rocmPackages.hipsparse}:${hipcub'}:${rocprim'}:${pkgs.rocmPackages.rocthrust}:$CMAKE_PREFIX_PATH"
      
      export CPATH="${pkgs.rocmPackages.hiprand}/include:${pkgs.rocmPackages.rocrand}/include:${pkgs.rocmPackages.hipsparse}/include:${pkgs.rocmPackages.rocblas}/include:${pkgs.rocmPackages.hipblas}/include:${pkgs.rocmPackages.hipfft}/include:${pkgs.rocmPackages.miopen}/include:${pkgs.rocmPackages.clr}/include:${rocprim'}/include:${hipcub'}/include:${pkgs.rocmPackages.rocthrust}/include:$CPATH"
      
      mkdir -p tools/ci_build
      ln -sf ${pkgs.rocmPackages.hipify}/bin/hipify-perl tools/ci_build/hipify-perl
    '';

    cmakeFlags = (old.cmakeFlags or []) ++ [
      "-Donnxruntime_USE_ROCM=ON"
      "-Donnxruntime_ROCM_HOME=${pkgs.rocmPackages.clr}"
      "-Donnxruntime_USE_MIOPEN=ON"
      "-Donnxruntime_USE_ROCBLAS=ON"
      "-Donnxruntime_BUILD_UNIT_TESTS=OFF"
      "-DCMAKE_BUILD_TYPE=Release"
      "-DMIOPEN_VERSION_H_PATH=${pkgs.rocmPackages.miopen}/include/miopen"
      "-Donnxruntime_USE_COMPOSABLE_KERNEL=OFF"
      "-DFETCHCONTENT_FULLY_DISCONNECTED=ON"
      "-DGPU_TARGETS=gfx1103"
      "-DAMDGPU_TARGETS=gfx1103"
      "-DCMAKE_CXX_STANDARD=20"
      "-DCMAKE_CXX_STANDARD_REQUIRED=ON"
      "-DCMAKE_HIP_STANDARD=20"
      "-DCMAKE_HIP_STANDARD_REQUIRED=ON"
      "-DCMAKE_CXX_EXTENSIONS=OFF"
       "-DCMAKE_CXX_FLAGS= -Wno-deprecated-enum-float-conversion -Wno-error=deprecated-enum-float-conversion -Wno-error -Wno-error=c++20-extensions"
  "-DCMAKE_HIP_FLAGS=--offload-arch=gfx1103  -Wno-deprecated-enum-float-conversion -Wno-error=deprecated-enum-float-conversion -Wno-error -Wno-error=c++20-extensions"
    ];
  });

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

  buildInputs = with pkgs; [
    openjdk17
    cmake
    opencv4100
    clang
    lapack
    suitesparse
    pnpm
    onnxruntimeRocm
    # onnxruntime
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