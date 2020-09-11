# react-native-record-screen

A screen record with app audio + mic module for React Native.

- Support iOS >= 11.0

## Installation

```sh
npm install @beapp/react-native-screen-recorder
```

pod install

```
cd ios && pod install && cd ../
```

## Usage

### Recording full screen

```js
import RecordScreen from '@beapp/react-native-screen-recorder'

// recording start
RecordScreen.startRecording().catch((error) => console.error(error))

// recording stop
const res = await RecordScreen.stopRecording().catch((error) =>
  console.warn(error)
)

if (res) {
  const url = res.result.outputURL
}
```

### Clean Sandbox

```js
RecordScreen.clean();
```

## License

MIT
