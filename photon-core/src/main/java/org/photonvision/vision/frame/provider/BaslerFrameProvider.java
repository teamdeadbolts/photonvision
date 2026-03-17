package org.photonvision.vision.frame.provider;

import edu.wpi.first.util.RawFrame;
import org.opencv.core.Mat;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.common.util.math.MathUtils;
import org.photonvision.vision.camera.baslerCameras.GenericBaslerCameraSettables;
import org.photonvision.vision.opencv.CVMat;
import org.teamdeadbolts.basler.BaslerJNI;

public class BaslerFrameProvider extends CpuImageProcessor {
    private final GenericBaslerCameraSettables settables;

    static final Logger logger = new Logger(BaslerFrameProvider.class, LogGroup.Camera);

    private Runnable connectedCallback;

    private long timeOffsetNs = 0;
    private boolean timeSyncDone = false;

    public BaslerFrameProvider(GenericBaslerCameraSettables settables, Runnable connectedCallback) {
        this.settables = settables;
        this.connectedCallback = connectedCallback;

        // var vidMode = settables.getCurrentVideoMode();
        // settables.setVideoMode(vidMode);

        BaslerJNI.startCamera(settables.ptr);
    }

    @Override
    public String getName() {
        return "BaslerCameraFrameProvider-" + this.settables.serial;
    }

    @Override
    public void release() {
        logger.info("Calling release");
        BaslerJNI.stopCamera(settables.ptr);
        BaslerJNI.destroyCamera(settables.ptr);
        // BaslerJNI.cleanUp();
    }

    @Override
    public boolean isConnected() {
        var serials = BaslerJNI.getConnectedCameras();
        for (String serial : serials) {
            if (serial.equals(settables.serial)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean checkCameraConnected() {
        boolean connected = isConnected();
        if (connected && !cameraPropertiesCached) {
            logger.info("Camera connected! running callback");
            onCameraConnected();
        }

        return connected;
    }

    @Override
    CapturedFrame getInputMat() {
        if (!cameraPropertiesCached && isConnected()) {
            onCameraConnected();
        }
        if (BaslerJNI.isCameraRemoved(settables.ptr)) {
            logger.error("Camera hardware was removed! Forcing pipeline restart.");
            throw new RuntimeException("Basler device physically disconnected.");
        }

        var cameraMode = settables.getCurrentVideoMode();
        var frame = new RawFrame();
        frame.setInfo(
                cameraMode.width, cameraMode.height, cameraMode.width * 3, cameraMode.pixelFormat);

        BaslerJNI.awaitNewFrame(settables.ptr);
        long hwTimestampNs = BaslerJNI.getLatestTimestamp(settables.ptr);
        long matPtr = BaslerJNI.takeFrame(settables.ptr);

        if (matPtr == 0) {
            return new CapturedFrame(
                    new CVMat(), settables.getFrameStaticProperties(), MathUtils.wpiNanoTime());
        }

        long currentWpiTimeNs = MathUtils.wpiNanoTime();
        if (!timeSyncDone && hwTimestampNs > 0) {
            timeOffsetNs = currentWpiTimeNs - hwTimestampNs;
            timeSyncDone = true;
        }

        long synchronizedTimestamp = hwTimestampNs + timeOffsetNs;

        Mat mat = new Mat(matPtr);
        CVMat ret = new CVMat(mat, frame);
        return new CapturedFrame(ret, settables.getFrameStaticProperties(), synchronizedTimestamp);
    }

    @Override
    public void onCameraConnected() {
        super.onCameraConnected();
        this.connectedCallback.run();
    }
}
