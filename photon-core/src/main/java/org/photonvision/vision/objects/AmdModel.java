package org.photonvision.vision.objects;

import java.io.File;

import org.opencv.core.Size;
import org.photonvision.common.configuration.NeuralNetworkModelManager.Family;
import org.photonvision.common.configuration.NeuralNetworkModelManager.Version;
import org.photonvision.common.configuration.NeuralNetworkPropertyManager.ModelProperties;

public class AmdModel implements Model {
    public final File modelFile;
    public final ModelProperties properties;

    public AmdModel(ModelProperties properties) throws IllegalArgumentException {
      this.modelFile = new File(properties.modelPath().toString());

      if (!modelFile.exists()) {
        throw new IllegalArgumentException("Model file does not exist: " + modelFile);
      }

      if (properties.labels() == null || properties.labels().isEmpty()) {
        throw new IllegalArgumentException("Labels must be provided");
      }

      if (properties.resolutionWidth() <= 0 || properties.resolutionHeight() <= 0) {
        throw new IllegalArgumentException("Resolution must be greater than 0");
      }

      if (properties.family() != Family.ONNX) {
        throw new IllegalArgumentException("Model family must be ONNX");
      }

      if (properties.version() != Version.YOLOV5
          && properties.version() != Version.YOLOV8
          && properties.version() != Version.YOLOV11) {
        throw new IllegalArgumentException("Model version must be YOLOV5, YOLOV8, or YOLOV11");
      }

      this.properties = properties;
    }

    @Override
    public String getUID() {
      return properties.modelPath().toString();
    }

    @Override
    public String getNickname() {
      return properties.nickname();
    }

    @Override
    public Family getFamily() {
      return properties.family();
    }

    @Override
    public ModelProperties getProperties() {
      return properties;
    }

    @Override
    public ObjectDetector load() {
      return new AmdObjectDetector(this, new Size(properties.resolutionWidth(), properties.resolutionHeight()));
    }

    @Override
    public String toString() {
      return "AmdModel{modelFile=" + modelFile + ", properties=" + properties + "}";
    }
  }
