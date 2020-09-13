declare type RecordScreenConfigType = {
    width?: number;
    height?: number;
};
declare type RecordingSuccessResponse = {
    status: 'success';
    result: {
        outputURL: string;
    };
};
declare type RecordingErrorResponse = {
    status: 'error';
    result: {
        outputURL: string;
    };
};
declare type RecordingResponse = RecordingSuccessResponse | RecordingErrorResponse;
declare class ReactNativeRecordScreenClass {
    private _screenWidth;
    private _screenHeight;
    setup(config?: RecordScreenConfigType): void;
    startRecording(config?: RecordScreenConfigType): Promise<void>;
    stopRecording(): Promise<RecordingResponse>;
    clean(): Promise<string>;
}
declare const ReactNativeRecordScreen: ReactNativeRecordScreenClass;
export default ReactNativeRecordScreen;
