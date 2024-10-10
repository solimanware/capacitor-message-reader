# 📱 Message Reader Plugin

![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)
![npm version](https://img.shields.io/npm/v/capacitor-message-reader.svg)

A Capacitor well optimized plugin for reading SMS "and MMS" messages on Android devices.

## Features

- 📥 Read SMS and MMS messages
- 🔍 Advanced filtering options
- 🔐 Permission handling
- 📅 Date range filtering
- 🔢 Pagination support

## Installation

```bash
npm i @solimanware/capacitor-message-reader
npx cap sync
```

## Usage

First, import the plugin in your TypeScript file:

```typescript
import { MessageReader } from '@solimanware/capacitor-message-reader';
```

### Checking Permissions

Before accessing messages, check if your app has the necessary permissions, you can use `@awesome-cordova-plugins/android-permissions` for now.

### Reading Messages

Use the `getMessages` method to retrieve messages based on various filters:

```typescript
const getMessages = async () => {
const filter = {
    body: 'Hello',
    sender: '+1234567890',
    minDate: Date.now() - 7 24 60 60 1000, // Last 7 days
    limit: 50
};

try {
    const result = await MessageReader.getMessages(filter);
    console.log('Retrieved messages:', result.messages);
} catch (error) {
    console.error('Error fetching messages:', error);
}
};
```
## API

### MessageReaderPlugin

#### getMessages(filter: GetMessageFilterInput): Promise<{ messages: MessageObject[] }>

Retrieves messages based on the provided filter criteria.

##### GetMessageFilterInput

| Property   | Type       | Description                                            |
|------------|------------|--------------------------------------------------------|
| ids        | string[]   | Array of message IDs to filter by                      |
| body       | string     | Text to search for in the message body                 |
| sender     | string     | Phone number or address to filter by                   |
| minDate    | number     | Minimum date (in milliseconds since epoch) to filter   |
| maxDate    | number     | Maximum date (in milliseconds since epoch) to filter   |
| indexFrom  | number     | Starting index for pagination                          |
| indexTo    | number     | Ending index for pagination                            |
| limit      | number     | Maximum number of messages to return                   |


### MessageObject

Represents a message with the following properties:

| Property    | Type                | Description                                     |
|-------------|---------------------|-------------------------------------------------|
| id          | string              | Unique identifier of the message                |
| date        | number              | Timestamp of the message (milliseconds)         |
| messageType | 'sms' \| 'mms'      | Type of the message                             |
| sender      | string              | Phone number or address of the sender/recipient |
| body        | string              | Content of the message                          |

## Permissions

This plugin requires the following permissions:

- `android.permission.READ_SMS`

Make sure to include these permissions in your `AndroidManifest.xml` file:
```xml
<uses-permission android:name="android.permission.READ_SMS" />
```

# Notes

- This plugin is designed for Android devices only.
- Ensure compliance with privacy laws and regulations when using this plugin to access user messages.
- Always request user consent before reading their messages.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License.

## Donate

If you find this plugin helpful and want to support its development, consider making a donation. Your contribution helps maintain and improve the Message Reader Plugin.

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.me/solimanware)

Every donation, no matter how small, is greatly appreciated and motivates us to keep enhancing this plugin. Thank you for your support!

