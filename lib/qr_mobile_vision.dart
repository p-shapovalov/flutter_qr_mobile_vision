import 'dart:async';

import 'package:flutter/services.dart';

class PreviewDetails {
  num width;
  num height;
  int sensorOrientation;
  int textureId;

  PreviewDetails(
      this.width, this.height, this.sensorOrientation, this.textureId);
}

enum BarcodeFormats {
  ALL_FORMATS,
  AZTEC,
  CODE_128,
  CODE_39,
  CODE_93,
  CODABAR,
  DATA_MATRIX,
  EAN_13,
  EAN_8,
  ITF,
  PDF417,
  QR_CODE,
  UPC_A,
  UPC_E,
}

const _defaultBarcodeFormats = const [
  BarcodeFormats.ALL_FORMATS,
];

class QrMobileVision {
  static const MethodChannel _channel =
      const MethodChannel('com.github.rmtmckenzie/qr_mobile_vision');
  static QrChannelReader channelReader = new QrChannelReader(_channel);

  //Set target size before starting
  static Future<PreviewDetails> start({
    required int width,
    required int height,
    required QRCodeHandler qrCodeHandler,
    List<BarcodeFormats>? formats = _defaultBarcodeFormats,
  }) async {
    final _formats = formats ?? _defaultBarcodeFormats;
    assert(_formats.length > 0);

    List<String> formatStrings = _formats
        .map((format) => format.toString().split('.')[1])
        .toList(growable: false);

    channelReader.setQrCodeHandler(qrCodeHandler);
    var details = await _channel.invokeMethod('start', {
      'targetWidth': width,
      'targetHeight': height,
      'heartbeatTimeout': 0,
      'formats': formatStrings
    });

    // invokeMethod returns Map<dynamic,...> in dart 2.0
    assert(details is Map<dynamic, dynamic>);

    int textureId = details["textureId"];
    int orientation = details["surfaceOrientation"];
    num surfaceHeight = details["surfaceHeight"];
    num surfaceWidth = details["surfaceWidth"];

    return new PreviewDetails(
        surfaceWidth, surfaceHeight, orientation, textureId);
  }

  static Future stop() {
    channelReader.clearQrCodeHandler();
    return _channel.invokeMethod('stop').catchError(print);
  }

  static Future toggleFlash() {
    return _channel.invokeMethod('toggleFlash').catchError(print);
  }

  static Future heartbeat() {
    return _channel.invokeMethod('heartbeat').catchError(print);
  }

  static Future<List<List<int>>?> getSupportedSizes() {
    return _channel.invokeMethod<List<List<int>>>('getSupportedSizes');
  }
}

enum FrameRotation { none, ninetyCC, oneeighty, twoseventyCC }

typedef void QRCodeHandler(String qr);

class QrChannelReader {
  QrChannelReader(this.channel) {
    channel.setMethodCallHandler((MethodCall call) async {
      switch (call.method) {
        case 'qrRead':
          if (qrCodeHandler != null) qrCodeHandler!(call.arguments);
          break;
        default:
          print("QrChannelHandler: unknown method call received at "
              "${call.method}");
      }
    });
  }

  void setQrCodeHandler(QRCodeHandler qrch) {
    this.qrCodeHandler = qrch;
  }

  clearQrCodeHandler() => this.qrCodeHandler = null;

  MethodChannel channel;
  QRCodeHandler? qrCodeHandler;
}
