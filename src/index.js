import { NativeModules, Dimensions } from 'react-native';
const { RecordScreen } = NativeModules;
const RS = RecordScreen;
class ReactNativeRecordScreenClass {
    constructor() {
        this._screenWidth = Dimensions.get('window').width;
        this._screenHeight = Dimensions.get('window').height;
    }
    setup(config = {}) {
        const conf = Object.assign({
            width: this._screenWidth,
            height: this._screenHeight,
        }, config);
        RS.setup(conf);
    }
    async startRecording(config = {}) {
        Object.keys(config).length && this.setup(config);
        return new Promise((resolve, reject) => {
            RS.startRecording().then(resolve).catch(reject);
        });
    }
    stopRecording() {
        return new Promise((resolve, reject) => {
            RS.stopRecording().then(resolve).catch(reject);
        });
    }
    clean() {
        return new Promise((resolve, reject) => {
            RS.clean().then(resolve).catch(reject);
        });
    }
}
const ReactNativeRecordScreen = new ReactNativeRecordScreenClass();
export default ReactNativeRecordScreen;
