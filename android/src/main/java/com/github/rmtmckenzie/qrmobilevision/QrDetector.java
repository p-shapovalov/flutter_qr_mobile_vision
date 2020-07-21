package com.github.rmtmckenzie.qrmobilevision;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.util.List;

/**
 * Allows QrCamera classes to send frames to a Detector
 */

class QrDetector implements OnSuccessListener<List<FirebaseVisionBarcode>>, OnFailureListener {
    private static final String TAG = "cgr.qrmv.QrDetector";
    private final QrReaderCallbacks communicator;
    private final FirebaseVisionBarcodeDetector detector;

    public interface Frame {
        FirebaseVisionImage toImage();

        void close();

        int getWidth();
        int getHeight();
    }

    @GuardedBy("this")
    private Frame latestFrame;

    @GuardedBy("this")
    private Frame processingFrame;
    private int width;
    private int height;

    QrDetector(QrReaderCallbacks communicator, FirebaseVisionBarcodeDetectorOptions options) {
        this.communicator = communicator;
        this.detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options);
    }

    void detect(Frame frame) {
        if (latestFrame != null) latestFrame.close();
        latestFrame = frame;

        if (processingFrame == null) {
            processLatest();
        }
    }

    private synchronized void processLatest() {
        if (processingFrame != null) processingFrame.close();
        processingFrame = latestFrame;
        latestFrame = null;
        if (processingFrame != null) {
            processFrame(processingFrame);
        }
    }

    private void processFrame(Frame frame) {
        FirebaseVisionImage image;
        try {
            image = frame.toImage();
        } catch (IllegalStateException ex) {
            // ignore state exception from making frame to image
            // as the image may be closed already.
            return;
        }

        if (this.width == 0 && this.height == 0) {
            this.width = frame.getWidth();
            this.height = frame.getHeight();
        }

        detector.detectInImage(image)
            .addOnSuccessListener(this)
            .addOnFailureListener(this);
    }

    @Override
    public void onSuccess(List<FirebaseVisionBarcode> firebaseVisionBarcodes) {
        for (FirebaseVisionBarcode barcode : firebaseVisionBarcodes) {
            if (processingFrame != null) {
                Rect rect = barcode.getBoundingBox();
                Rect center = new Rect(width / 3, height / 3, width * 2 / 3, height * 2 / 3);
                if (rect.intersect(center)) {
                    communicator.qrRead(barcode.getRawValue());
                }
            }
        }
        processLatest();
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        Log.w(TAG, "Barcode Reading Failure: ", e);
    }
}