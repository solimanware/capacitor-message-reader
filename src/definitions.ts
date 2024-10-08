import type { PermissionState } from '@capacitor/core';

/**
 * Represents the permission status for accessing messages.
 */
export interface PermissionStatus {
  /** The current permission state for accessing messages. */
  messages: PermissionState;
}

/**
 * Represents a message object with its properties.
 */
export interface MessageObject {
  /** Unique identifier of the message. */
  id: string;
  /** Timestamp of the message in milliseconds since epoch. */
  date: number;
  /** Type of the message, either 'sms' or 'mms'. */
  messageType: 'sms' | 'mms';
  /** Phone number or address of the sender/recipient. */
  sender: string;
  /** Content of the message. */
  body: string;
}

/**
 * Input parameters for filtering messages.
 */
export interface GetMessageFilterInput {
  /** Array of message IDs to filter by. */
  ids?: string[];
  /** Text to search for in the message body. */
  body?: string;
  /** Phone number or address to filter by. */
  sender?: string;
  /** Minimum date (in milliseconds since epoch) to filter messages. */
  minDate?: number;
  /** Maximum date (in milliseconds since epoch) to filter messages. */
  maxDate?: number;
  /** Starting index for pagination. */
  indexFrom?: number;
  /** Ending index for pagination. */
  indexTo?: number;
  /** Maximum number of messages to return. */
  limit?: number;
}

/**
 * Plugin interface for reading messages from the device's inbox.
 */
export interface MessageReaderPlugin {
  /**
   * Retrieves messages based on the provided filter criteria.
   * @param filter - The filter criteria to apply when fetching messages.
   * @returns A promise that resolves with an array of filtered MessageObject.
   */
  getMessages(filter: GetMessageFilterInput): Promise<{ messages: MessageObject[] }>;

  /**
   * Checks the current permission status for accessing messages.
   * @returns A promise that resolves with the current PermissionStatus.
   */
  checkPermissions(): Promise<PermissionStatus>;

  /**
   * Requests permissions to access messages on the device.
   * @returns A promise that resolves with the updated PermissionStatus after the request.
   */
  requestPermissions(): Promise<PermissionStatus>;
}
