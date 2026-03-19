package org.photonvision.vision.frame.provider;

import edu.wpi.first.util.RawFrame;
import org.opencv.core.Mat;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.common.util.math.MathUtils;
import org.photonvision.vision.camera.baslerCameras.GenericBaslerCameraSettables;
import org.photonvision.vision.opencv.CVMat;
import org.teamdeadbolts.basler.BaslerJNI;
import org.teamdeadbolts.basler.BaslerJNI.PylonResult;

public class BaslerFrameProvider extends CpuImageProcessor {

    private final GenericBaslerCameraSettables settables;

    static final Logger logger = new Logger(BaslerFrameProvider.class, LogGroup.Camera);

    private Runnable connectedCallback;

    private long timeOffsetNs = 0;
    private double filteredOffset = 0;
    private int syncFrames = 0;

    private volatile boolean isShuttingDown = false;

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
        logger.info("Releaseing camera " + settables.getConfiguration().nickname);
        BaslerJNI.stopCamera(settables.ptr);
        BaslerJNI.destroyCamera(settables.ptr);
    }

    @Override
    public boolean isConnected() {
        PylonResult<String[]> serials = BaslerJNI.getConnectedCameras();
        if (!serials.isOk()) {
            logger.error("Failed to get connected cameras");
            return false;
        }
        for (String serial : serials.unwrap()) {
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

        if (!BaslerJNI.awaitNewFrame(settables.ptr).isOk()) {
            logger.error("Failed to await new frame");
            return new CapturedFrame(
                    new CVMat(), settables.getFrameStaticProperties(), MathUtils.wpiNanoTime());
        }
        long hwTimestampNs =
                BaslerJNI.getLatestTimestamp(settables.ptr).orElse(MathUtils.wpiNanoTime());
        PylonResult<Long> matPtr = BaslerJNI.takeFrame(settables.ptr);
        if (!matPtr.isOk()) {
            logger.error("Failed to take frame");
            return new CapturedFrame(
                    new CVMat(), settables.getFrameStaticProperties(), MathUtils.wpiNanoTime());
        }

        long currentWpiTimeNs = MathUtils.wpiNanoTime();
        long rawOffset = currentWpiTimeNs - hwTimestampNs;
        if (syncFrames < 50) {
            if (syncFrames == 0) filteredOffset = rawOffset;
            else filteredOffset = 0.9 * filteredOffset + 0.1 * rawOffset;
            syncFrames++;
            timeOffsetNs = (long) filteredOffset;
        }

        long synchronizedTimestamp = hwTimestampNs + timeOffsetNs;

        Mat mat = new Mat(matPtr.unwrap());
        CVMat ret = new CVMat(mat, frame);
        return new CapturedFrame(ret, settables.getFrameStaticProperties(), synchronizedTimestamp);
    }

    @Override
    public void onCameraConnected() {
        super.onCameraConnected();
        this.connectedCallback.run();
    }
}
