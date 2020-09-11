#import "RecordScreen.h"
#import <React/RCTConvert.h>

@implementation RecordScreen

const int DEFAULT_FPS = 30;

- (NSDictionary *)errorResponse:(NSDictionary *)result;
{
    NSDictionary *json = [NSDictionary dictionaryWithObjectsAndKeys:
        @"error", @"status",
        result, @"result",nil];
    return json;

}

- (NSDictionary *) successResponse:(NSDictionary *)result;
{
    NSDictionary *json = [NSDictionary dictionaryWithObjectsAndKeys:
        @"success", @"status",
        result, @"result",nil];
    return json;

}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(setup: (NSDictionary *)config)
{
    self.screenWidth = [RCTConvert int: config[@"width"]];
    self.screenHeight = [RCTConvert int: config[@"height"]];
    self.prefix = [RCTConvert NSString: config[@"prefix"]];
}

RCT_REMAP_METHOD(startRecording, resolve:(RCTPromiseResolveBlock)resolve rejecte:(RCTPromiseRejectBlock)reject)
{
    self.screenRecorder = [RPScreenRecorder sharedRecorder];
    if (self.screenRecorder.isRecording) {
        return;
    }
    
    self.encounteredFirstBuffer = NO;
    
    NSArray *pathDocuments = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *outputURL = pathDocuments[0];

    NSString *videoOutPath = [[outputURL stringByAppendingPathComponent:[NSString stringWithFormat:@"%u", arc4random() % 1000]] stringByAppendingPathExtension:@"mp4"];
    
    NSError *error;
    self.writer = [AVAssetWriter assetWriterWithURL:[NSURL fileURLWithPath:videoOutPath] fileType:AVFileTypeMPEG4 error:&error];
    if (!self.writer) {
        NSLog(@"writer: %@", error);
        abort();
    }
    
    AudioChannelLayout acl = { 0 };
    acl.mChannelLayoutTag = kAudioChannelLayoutTag_Mono;
    self.audioInput = [[AVAssetWriterInput alloc] initWithMediaType:AVMediaTypeAudio outputSettings:@{ AVFormatIDKey: @(kAudioFormatMPEG4AAC), AVSampleRateKey: @(44100),  AVChannelLayoutKey: [NSData dataWithBytes: &acl length: sizeof( acl ) ], AVEncoderBitRateKey: @(64000)}];
    self.micInput = [[AVAssetWriterInput alloc] initWithMediaType:AVMediaTypeAudio outputSettings:@{ AVFormatIDKey: @(kAudioFormatMPEG4AAC), AVSampleRateKey: @(44100),  AVChannelLayoutKey: [NSData dataWithBytes: &acl length: sizeof( acl ) ], AVEncoderBitRateKey: @(64000)}];
    
    NSDictionary *compressionProperties = @{AVVideoProfileLevelKey         : AVVideoProfileLevelH264HighAutoLevel,
                                            AVVideoH264EntropyModeKey      : AVVideoH264EntropyModeCABAC,
                                            AVVideoAverageBitRateKey       : @(1920 * 1080 * 114),
                                            AVVideoMaxKeyFrameIntervalKey  : @60,
                                            AVVideoAllowFrameReorderingKey : @NO};

    if (@available(iOS 11.0, *)) {
        NSDictionary *videoSettings = @{AVVideoCompressionPropertiesKey : compressionProperties,
                                        AVVideoCodecKey                 : AVVideoCodecTypeH264,
                                        AVVideoWidthKey                 : @(self.screenWidth),
                                        AVVideoHeightKey                : @(self.screenHeight)};

        self.videoInput = [AVAssetWriterInput assetWriterInputWithMediaType:AVMediaTypeVideo outputSettings:videoSettings];
    } else {
        // Fallback on earlier versions
    }
    
    [self.writer addInput:self.audioInput];
    [self.writer addInput:self.micInput];
    [self.writer addInput:self.videoInput];
    [self.videoInput setMediaTimeScale:60];
    [self.writer setMovieTimeScale:60];
    [self.videoInput setExpectsMediaDataInRealTime:YES];

    self.screenRecorder.microphoneEnabled = YES;
    
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (granted) {
    [self.screenRecorder startCaptureWithHandler:^(CMSampleBufferRef sampleBuffer, RPSampleBufferType bufferType, NSError* error) {
        if (CMSampleBufferDataIsReady(sampleBuffer)) {
            if (self.writer.status == AVAssetWriterStatusUnknown && !self.encounteredFirstBuffer) {
                    self.encounteredFirstBuffer = YES;
                    NSLog(@"First buffer video");
                    [self.writer startWriting];
                    [self.writer startSessionAtSourceTime:CMSampleBufferGetPresentationTimeStamp(sampleBuffer)];
            } else if (self.writer.status == AVAssetWriterStatusFailed) {
                
            } else if (self.writer.status == AVAssetWriterStatusWriting) {
                switch (bufferType) {
                    case RPSampleBufferTypeVideo:
                        if (self.videoInput.isReadyForMoreMediaData) {
                            [self.videoInput appendSampleBuffer:sampleBuffer];
                        }
                        break;
                    case RPSampleBufferTypeAudioApp:
                        if (self.audioInput.isReadyForMoreMediaData) {
                            [self.audioInput appendSampleBuffer:sampleBuffer];
                        }
                        break;
                    case RPSampleBufferTypeAudioMic:
                        if (self.micInput.isReadyForMoreMediaData) {
                            [self.micInput appendSampleBuffer:sampleBuffer];
                        }
                    break;
                    default:
                        break;
                }
            }
        }
    } completionHandler:^(NSError* error) {
        NSLog(@"startCapture: %@", error);
    }];
            }
                });
            }];

    self.screenRecorder.microphoneEnabled = YES;

}

RCT_REMAP_METHOD(stopRecording, resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        [[RPScreenRecorder sharedRecorder] stopCaptureWithHandler:^(NSError * _Nullable error) {
            if (!error) {
                [self.audioInput markAsFinished];
                [self.micInput markAsFinished];
                [self.videoInput markAsFinished];
                [self.writer finishWritingWithCompletionHandler:^{

                    NSDictionary *result = [NSDictionary dictionaryWithObject:self.writer.outputURL.absoluteString forKey:@"videoUrl"];
                    resolve([self successResponse:result]);
                    
                    UISaveVideoAtPathToSavedPhotosAlbum(self.writer.outputURL.absoluteString, nil, nil, nil);
                    NSLog(@"finishWritingWithCompletionHandler: Recording stopped successfully. Cleaning up... %@", result);
                    self.audioInput = nil;
                    self.micInput = nil;
                    self.videoInput = nil;
                    self.writer = nil;
                    self.screenRecorder = nil;
                }];
            }
        }];
    });
}

RCT_REMAP_METHOD(clean,
                 cleanResolve:(RCTPromiseResolveBlock)resolve
                 cleanRejecte:(RCTPromiseRejectBlock)reject)
{

    NSArray *pathDocuments = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *path = pathDocuments[0];
    [[NSFileManager defaultManager] removeItemAtPath:path error:nil];
    resolve(@"cleaned");
}

@end


