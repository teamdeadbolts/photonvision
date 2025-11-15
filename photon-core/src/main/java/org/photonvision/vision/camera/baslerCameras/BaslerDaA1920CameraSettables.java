package org.photonvision.vision.camera.baslerCameras;

import edu.wpi.first.util.PixelFormat;
import org.photonvision.common.configuration.CameraConfiguration;
import org.photonvision.vision.camera.baslerCameras.BaslerCameraSource.BaslerVideoMode;
import org.photonvision.vision.camera.baslerCameras.BaslerCameraSource.BaslerVideoMode.BinMode;
import org.photonvision.vision.camera.baslerCameras.BaslerCameraSource.BaslerVideoMode.BinningConfig;

public class BaslerDaA1920CameraSettables extends GenericBaslerCameraSettables {

    protected BaslerDaA1920CameraSettables(CameraConfiguration configuration) {
        super(configuration);

        this.minExposure = 0.000019;
        this.maxExposure = 1000;

        this.maxGain = 48;

        // this.getConfiguration().cameraQuirks.quirks.put(CameraQuirk.Gain, true);
        // this.getConfiguration().cameraQuirks.quirks.put(CameraQuirk, null)
    }

    @Override
    protected void setupVideoModes() {
        videoModes.put(
                0,
                new BaslerVideoMode(
                        PixelFormat.kGray.getValue(), 1920, 1200, 100, new BinningConfig(BinMode.NONE, 0, 0)));

        videoModes.put(
                1,
                new BaslerVideoMode(
                        PixelFormat.kGray.getValue(),
                        1920 / 2,
                        1200 / 2,
                        100,
                        new BinningConfig(BinMode.AVERAGE, 2, 2)));

        videoModes.put(
                2,
                new BaslerVideoMode(
                        PixelFormat.kGray.getValue(),
                        1920 / 2,
                        1200 / 2,
                        100,
                        new BinningConfig(BinMode.SUM, 2, 2)));
    }
}
