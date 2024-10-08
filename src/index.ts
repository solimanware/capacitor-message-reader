import { registerPlugin } from '@capacitor/core';
import type { MessageReaderPlugin } from './definitions';

const MessageReader = registerPlugin<MessageReaderPlugin>('MessageReader');

export * from './definitions';
export { MessageReader };

