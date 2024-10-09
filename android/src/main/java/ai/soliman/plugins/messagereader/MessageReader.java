package ai.soliman.plugins.messagereader;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class MessageReader {

    private Context context;

    public MessageReader(Context context) {
        this.context = context;
    }

    public JSONArray getMessages(GetMessageFilterInput filter) {
        JSONArray messages = new JSONArray();

        // If filter is null, create an empty filter
        final GetMessageFilterInput finalFilter = (filter == null) ? new GetMessageFilterInput() : filter;

        JSONArray smsMessages = readSMS(finalFilter);
        JSONArray mmsMessages = readMMS(finalFilter);

        // Combine SMS and MMS messages
        for (int i = 0; i < smsMessages.length(); i++) {
            messages.put(smsMessages.optJSONObject(i));
        }
        for (int i = 0; i < mmsMessages.length(); i++) {
            messages.put(mmsMessages.optJSONObject(i));
        }

        // Apply pagination filters
        return applyPaginationFilters(messages, finalFilter);
    }

    private JSONArray applyPaginationFilters(JSONArray messages, GetMessageFilterInput filter) {
        int totalMessages = messages.length();
        int startIndex = filter.getIndexFrom() != null ? filter.getIndexFrom() : 0;
        if (startIndex < 0) startIndex = 0;
        if (startIndex >= totalMessages) return new JSONArray();

        int limit = filter.getLimit() != null ? filter.getLimit() : totalMessages - startIndex;
        if (limit <= 0) return new JSONArray();

        int endIndex = Math.min(startIndex + limit, totalMessages);

        JSONArray filteredMessages = new JSONArray();
        for (int i = startIndex; i < endIndex; i++) {
            filteredMessages.put(messages.optJSONObject(i));
        }

        return filteredMessages;
    }

    private JSONArray readSMS(GetMessageFilterInput filter) {
        JSONArray messages = new JSONArray();

        Uri smsUri = Telephony.Sms.CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", String.valueOf(filter.getLimit() != null ? filter.getLimit() : 100))
                .appendQueryParameter("offset", String.valueOf(filter.getIndexFrom() != null ? filter.getIndexFrom() : 0))
                .build();

        ContentResolver cr = context.getContentResolver();

        String[] projection = {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
        };

        String selection = buildSmsSelection(filter);
        String[] selectionArgs = buildSmsSelectionArgs(filter);

        String sortOrder = Telephony.Sms.DATE + " DESC";

        try (Cursor cursor = cr.query(smsUri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(Telephony.Sms._ID);
                int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
                int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);

                while (cursor.moveToNext()) {
                    JSONObject sms = new JSONObject();
                    sms.put("id", cursor.getString(idIndex));
                    sms.put("sender", cursor.getString(addressIndex));
                    sms.put("body", cursor.getString(bodyIndex));
                    sms.put("date", cursor.getLong(dateIndex));
                    sms.put("messageType", "sms");
                    messages.put(sms);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    private JSONArray readMMS(GetMessageFilterInput filter) {
        JSONArray messages = new JSONArray();

        Uri mmsUri = Uri.parse("content://mms").buildUpon()
                .appendQueryParameter("limit", String.valueOf(filter.getLimit() != null ? filter.getLimit() : 100))
                .appendQueryParameter("offset", String.valueOf(filter.getIndexFrom() != null ? filter.getIndexFrom() : 0))
                .build();

        ContentResolver cr = context.getContentResolver();

        String[] projection = {
                "_id",
                "date"
        };

        String selection = buildMmsSelection(filter);
        String[] selectionArgs = buildMmsSelectionArgs(filter);

        String sortOrder = "date DESC";

        Set<String> mmsIds = new HashSet<>();

        try (Cursor cursor = cr.query(mmsUri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex("_id");

                while (cursor.moveToNext()) {
                    String id = cursor.getString(idIndex);
                    mmsIds.add(id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mmsIds.isEmpty()) {
            return messages;
        }

        // Fetch all MMS addresses and texts in bulk
        Map<String, JSONArray> addressesMap = getAllMmsAddresses(mmsIds);
        Map<String, String> textsMap = getAllMmsTexts(mmsIds);

        // Re-query MMS messages to get dates
        try (Cursor cursor = cr.query(mmsUri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex("_id");
                int dateIndex = cursor.getColumnIndex("date");

                while (cursor.moveToNext()) {
                    String id = cursor.getString(idIndex);
                    long date = cursor.getLong(dateIndex) * 1000L; // Convert to milliseconds

                    String mmsBody = textsMap.get(id);
                    JSONArray senders = addressesMap.get(id);

                    // Apply sender and body filters manually
                    boolean matchesSenderFilter = matchesSenderFilter(filter, senders);
                    boolean matchesBodyFilter = matchesBodyFilter(filter, mmsBody);

                    if (matchesSenderFilter && matchesBodyFilter) {
                        JSONObject mms = new JSONObject();
                        mms.put("id", id);
                        mms.put("date", date);

                        if (senders != null && senders.length() > 0) {
                            mms.put("sender", senders.getJSONObject(0).getString("sender"));
                        } else {
                            mms.put("sender", "");
                        }

                        mms.put("body", mmsBody != null && !mmsBody.isEmpty() ? mmsBody : "[No text content]");
                        mms.put("messageType", "mms");
                        messages.put(mms);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    private boolean matchesSenderFilter(GetMessageFilterInput filter, JSONArray senders) {
        if (filter.getSender() == null || filter.getSender().isEmpty()) {
            return true;
        }
        if (senders != null) {
            for (int i = 0; i < senders.length(); i++) {
                try {
                    JSONObject senderObj = senders.getJSONObject(i);
                    String sender = senderObj.getString("sender");
                    if (sender.equals(filter.getSender())) {
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private boolean matchesBodyFilter(GetMessageFilterInput filter, String body) {
        if (filter.getBody() == null || filter.getBody().isEmpty()) {
            return true;
        }
        return body != null && body.contains(filter.getBody());
    }

    private String buildSmsSelection(GetMessageFilterInput filter) {
        StringBuilder selection = new StringBuilder();
        List<String> clauses = new ArrayList<>();

        if (filter.getIds() != null && !filter.getIds().isEmpty()) {
            clauses.add(Telephony.Sms._ID + " IN (" + makePlaceholders(filter.getIds().size()) + ")");
        }

        if (filter.getMinDate() != null) {
            clauses.add(Telephony.Sms.DATE + " >= ?");
        }

        if (filter.getMaxDate() != null) {
            clauses.add(Telephony.Sms.DATE + " <= ?");
        }

        if (filter.getSender() != null && !filter.getSender().isEmpty()) {
            clauses.add(Telephony.Sms.ADDRESS + " = ?");
        }

        if (filter.getBody() != null && !filter.getBody().isEmpty()) {
            clauses.add(Telephony.Sms.BODY + " LIKE ?");
        }

        if (!clauses.isEmpty()) {
            selection.append(String.join(" AND ", clauses));
        }

        return selection.toString();
    }

    private String[] buildSmsSelectionArgs(GetMessageFilterInput filter) {
        List<String> args = new ArrayList<>();

        if (filter.getIds() != null && !filter.getIds().isEmpty()) {
            args.addAll(filter.getIds());
        }

        if (filter.getMinDate() != null) {
            args.add(String.valueOf(filter.getMinDate()));
        }

        if (filter.getMaxDate() != null) {
            args.add(String.valueOf(filter.getMaxDate()));
        }

        if (filter.getSender() != null && !filter.getSender().isEmpty()) {
            args.add(filter.getSender());
        }

        if (filter.getBody() != null && !filter.getBody().isEmpty()) {
            args.add("%" + filter.getBody() + "%");
        }

        return args.toArray(new String[0]);
    }

    private String buildMmsSelection(GetMessageFilterInput filter) {
        StringBuilder selection = new StringBuilder();

        List<String> clauses = new ArrayList<>();

        if (filter.getIds() != null && !filter.getIds().isEmpty()) {
            clauses.add("_id IN (" + makePlaceholders(filter.getIds().size()) + ")");
        }

        if (filter.getMinDate() != null) {
            clauses.add("date >= ?");
        }

        if (filter.getMaxDate() != null) {
            clauses.add("date <= ?");
        }

        if (!clauses.isEmpty()) {
            selection.append(String.join(" AND ", clauses));
        }

        return selection.toString();
    }

    private String[] buildMmsSelectionArgs(GetMessageFilterInput filter) {
        List<String> args = new ArrayList<>();

        if (filter.getIds() != null && !filter.getIds().isEmpty()) {
            args.addAll(filter.getIds());
        }

        if (filter.getMinDate() != null) {
            args.add(String.valueOf(filter.getMinDate() / 1000L)); // Convert milliseconds to seconds
        }

        if (filter.getMaxDate() != null) {
            args.add(String.valueOf(filter.getMaxDate() / 1000L)); // Convert milliseconds to seconds
        }

        return args.toArray(new String[0]);
    }

    private String makePlaceholders(int len) {
        if (len < 1) {
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    private Map<String, JSONArray> getAllMmsAddresses(Set<String> ids) {
        Map<String, JSONArray> addressesMap = new HashMap<>();

        Uri addrUri = Uri.parse("content://mms/addr");

        String[] projection = {"msg_id", "address", "type"};

        String selection = "msg_id IN (" + makePlaceholders(ids.size()) + ")";

        String[] selectionArgs = ids.toArray(new String[0]);

        try (Cursor cursor = context.getContentResolver().query(addrUri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                int msgIdIndex = cursor.getColumnIndex("msg_id");
                int addressIndex = cursor.getColumnIndex("address");
                int typeIndex = cursor.getColumnIndex("type");

                while (cursor.moveToNext()) {
                    String msgId = cursor.getString(msgIdIndex);
                    String address = cursor.getString(addressIndex);

                    // Skip placeholders
                    if (address != null && !address.equalsIgnoreCase("insert-address-token")) {
                        JSONObject addr = new JSONObject();
                        addr.put("sender", address);
                        addr.put("type", cursor.getInt(typeIndex));

                        JSONArray addresses = addressesMap.get(msgId);
                        if (addresses == null) {
                            addresses = new JSONArray();
                            addressesMap.put(msgId, addresses);
                        }
                        addresses.put(addr);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return addressesMap;
    }

    private Map<String, String> getAllMmsTexts(Set<String> ids) {
        Map<String, String> textsMap = new HashMap<>();

        Uri partUri = Uri.parse("content://mms/part");

        String[] projection = {"mid", "_id", "ct", "_data", "text"};

        String selection = "mid IN (" + makePlaceholders(ids.size()) + ")";

        String[] selectionArgs = ids.toArray(new String[0]);

        try (Cursor cursor = context.getContentResolver().query(partUri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                int midIndex = cursor.getColumnIndex("mid");
                int idIndex = cursor.getColumnIndex("_id");
                int ctIndex = cursor.getColumnIndex("ct");
                int dataIndex = cursor.getColumnIndex("_data");
                int textIndex = cursor.getColumnIndex("text");

                while (cursor.moveToNext()) {
                    String mid = cursor.getString(midIndex);
                    String partId = cursor.getString(idIndex);
                    String type = cursor.getString(ctIndex);

                    String data = cursor.getString(dataIndex);
                    String text = "";

                    if ("text/plain".equals(type) || "application/smil".equals(type)) {
                        if (data != null) {
                            text = getMmsPartText(partId);
                        } else {
                            text = cursor.getString(textIndex);
                        }

                        String existingText = textsMap.get(mid);
                        if (existingText == null) {
                            textsMap.put(mid, text);
                        } else {
                            textsMap.put(mid, existingText + text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return textsMap;
    }

    private String getMmsPartText(String partId) {
        Uri partUri = Uri.parse("content://mms/part/" + partId);
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(partUri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String temp;
            while ((temp = reader.readLine()) != null) {
                sb.append(temp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
