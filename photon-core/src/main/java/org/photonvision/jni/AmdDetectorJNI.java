package org.photonvision.jni;

import java.util.List;

import org.photonvision.common.util.TestUtils;

public class AmdDetectorJNI extends PhotonJNICommon {
    private boolean isLoaded;
    private static AmdDetectorJNI instance = null;

    private AmdDetectorJNI() {
        isLoaded = false;
    }

    public static AmdDetectorJNI getInstance() {
        if (instance == null) instance = new AmdDetectorJNI();

        return instance;
    }

    public static synchronized void forceLoad() throws Exception {
        TestUtils.loadLibraries();

        forceLoad(
                getInstance(),
                AmdDetectorJNI.class,
                List.of("amd_jni"));
    }

    @Override
    public boolean isLoaded() {
        return isLoaded;
    }

    @Override
    public void setLoaded(boolean state) {
        isLoaded = state;
    }
  
}
