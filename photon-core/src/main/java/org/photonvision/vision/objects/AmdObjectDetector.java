package org.photonvision.vision.objects;

import java.awt.Color;
import java.lang.ref.Cleaner;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.common.util.ColorHelper;
import org.photonvision.jni.AmdDetectorJNI;
import org.photonvision.vision.pipe.impl.NeuralNetworkPipeResult;
import org.teamdeadbolts.amd.AmdJNI;


public class AmdObjectDetector implements ObjectDetector {
  private static final Logger logger = new Logger(AmdDetectorJNI.class, LogGroup.General);

  private final Cleaner cleaner = Cleaner.create();

  private AtomicBoolean released = new AtomicBoolean(false);

  private final long objPointer;

  private final AmdModel model;

  private final Size inSize;

  @Override
  public Model getModel() {
    return model;
  }

  public AmdObjectDetector(AmdModel model, Size inputSize) {
    this.model = model;
    this.inSize = inputSize;

    this.objPointer = AmdJNI.create(model.modelFile.getPath(), model.properties.labels().size(), model.properties.version().ordinal(), -1);
    if (objPointer <= 0) {
      throw new RuntimeException("Failed to create AMD detector from path " + model.modelFile.getPath());
    } else if (!AmdJNI.isQuantized(objPointer)) {
      throw new IllegalArgumentException("Model must be quantized");
    }

    logger.debug("Created AMD detector with pointer " + objPointer);

    cleaner.register(this, this::release);
  }

  @Override
  public void release() {
    if (released.compareAndSet(false, true)) {
      if (this.objPointer <= 0) {
        logger.error("Detector is not initialized! Model: " + model.getNickname());
        return;
      }

      AmdJNI.destroy(objPointer);
      logger.debug("Released AMD detector with pointer " + objPointer);
    }
  }

  @Override
  public List<String> getClasses() {
    return model.properties.labels();
  }

  @Override
  public List<NeuralNetworkPipeResult> detect(Mat in, double nmsThresh, double boxThresh) {
    if (this.objPointer <= 0) {
      logger.error("Detector is not initialized! Model: " + model.getNickname());
      return List.of();
    }

    Mat letterboxed = new Mat();
    Letterbox scale = Letterbox.letterbox(in, letterboxed, this.inSize, ColorHelper.colorToScalar(Color.GRAY));

    if (!letterboxed.size().equals(this.inSize)) {
      letterboxed.release();
      throw new RuntimeException("Letterboxed image has incorrect size! Expected " + this.inSize + " but got " + letterboxed.size());
    }

    var results = AmdJNI.detect(this.objPointer, letterboxed.getNativeObjAddr(), nmsThresh, boxThresh);
    letterboxed.release();

    if (results == null) {
      return List.of();
    }

    return scale.resizeDetections(
      List.of(results).stream()
      .map(it -> new NeuralNetworkPipeResult(it.rect, it.getClassId(), it.getConf()))
      .toList());
  }

} 