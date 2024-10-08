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
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MessageReader {

    private Context context;
    private ExecutorService executorService;

    public MessageReader(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool(); // Use cached thread pool for better resource management
    }

    public JSONArray getMessages(GetMessageFilterInput filter) {
        JSONArray messages = new JSONArray();

        // If filter is null, create an empty filter
        final GetMessageFilterInput finalFilter = (filter == null) ? new GetMessageFilterInput() : filter;

        Future<JSONArray> smsFuture = executorService.submit(() -> readSMS(finalFilter));
        Future<JSONArray> mmsFuture = executorService.submit(() -> readMMS(finalFilter));

        try {
            JSONArray smsMessages = smsFuture.get();
            JSONArray mmsMessages = mmsFuture.get();

            // Combine SMS and MMS messages
            for (int i = 0; i < smsMessages.length(); i++) {
                messages.put(smsMessages.getJSONObject(i));
            }
            for (int i = 0; i < mmsMessages.length(); i++) {
                messages.put(mmsMessages.getJSONObject(i));
            }

            // No need to sort if messages are fetched in order
            // Apply indexFrom, indexTo, and limit filters
            return applyPaginationFilters(messages, finalFilter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new JSONArray(); // Return an empty array if there's an exception
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
        Uri smsUri = Telephony.Sms.CONTENT_URI;
        ContentResolver cr = context.getContentResolver();

        List<String> projectionList = new ArrayList<>();
        projectionList.add(Telephony.Sms._ID);
        projectionList.add(Telephony.Sms.ADDRESS);
        projectionList.add(Telephony.Sms.BODY);
        projectionList.add(Telephony.Sms.DATE);

        String[] projection = projectionList.toArray(new String[0]);

        String selection = buildSmsSelection(filter);
        String[] selectionArgs = buildSmsSelectionArgs(filter);

        String sortOrder = Telephony.Sms.DATE + " DESC";
        if (filter.getLimit() != null) {
            sortOrder += " LIMIT " + filter.getLimit();
            if (filter.getIndexFrom() != null) {
                sortOrder += " OFFSET " + filter.getIndexFrom();
            }
        }

        try (Cursor cursor = cr.query(smsUri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject sms = new JSONObject();
                    String id = cursor.getString(cursor.getColumnIndex(Telephony.Sms._ID));
                    String body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
                    long date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));

                    sms.put("id", id);
                    sms.put("sender", cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)));
                    sms.put("body", body);
                    sms.put("date", date);
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
        Uri mmsUri = Uri.parse("content://mms");
        ContentResolver cr = context.getContentResolver();

        String[] projection = {
                "_id",
                "date"
        };

        String selection = buildMmsSelection(filter);
        String[] selectionArgs = buildMmsSelectionArgs(filter);

        String sortOrder = "date DESC";
        if (filter.getLimit() != null) {
            sortOrder += " LIMIT " + filter.getLimit();
            if (filter.getIndexFrom() != null) {
                sortOrder += " OFFSET " + filter.getIndexFrom();
            }
        }

        try (Cursor cursor = cr.query(mmsUri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex("_id"));
                    long date = cursor.getLong(cursor.getColumnIndex("date")) * 1000L; // Convert to milliseconds

                    String mmsBody = getMmsText(id);
                    JSONArray senders = getMmsAddresses(id);

                    // Apply sender and body filters manually
                    boolean matchesSenderFilter = matchesSenderFilter(filter, senders);
                    boolean matchesBodyFilter = matchesBodyFilter(filter, mmsBody);

                    if (matchesSenderFilter && matchesBodyFilter) {
                        JSONObject mms = new JSONObject();
                        mms.put("id", id);
                        mms.put("date", date);

                        if (senders.length() > 0) {
                            mms.put("sender", senders.getJSONObject(0).getString("sender"));
                        } else {
                            mms.put("sender", "");
                        }

                        mms.put("body", mmsBody.isEmpty() ? "[No text content]" : mmsBody);
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

        if (!filter.getIds().isEmpty()) {
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

        args.addAll(filter.getIds());

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

        if (!filter.getIds().isEmpty()) {
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

        args.addAll(filter.getIds());

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

    private JSONArray getMmsAddresses(String id) {
        JSONArray senders = new JSONArray();
        Uri addrUri = Uri.parse("content://mms/" + id + "/addr");
        String[] projection = {"address", "type"};
        try (Cursor cursor = context.getContentResolver().query(addrUri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String sender = cursor.getString(cursor.getColumnIndex("address"));
                    // Skip senders like "insert-address-token" which are placeholders
                    if (sender != null && !sender.equalsIgnoreCase("insert-address-token")) {
                        JSONObject addr = new JSONObject();
                        addr.put("sender", sender);
                        addr.put("type", cursor.getInt(cursor.getColumnIndex("type")));
                        senders.put(addr);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return senders;
    }

    private String getMmsText(String id) {
        StringBuilder body = new StringBuilder();
        Uri partUri = Uri.parse("content://mms/part");
        String selection = "mid=?";
        String[] selectionArgs = {id};
        try (Cursor cursor = context.getContentResolver().query(partUri, null, selection, selectionArgs, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String partId = cursor.getString(cursor.getColumnIndex("_id"));
                    String type = cursor.getString(cursor.getColumnIndex("ct"));
                    if ("text/plain".equals(type) || "application/smil".equals(type)) {
                        String data = cursor.getString(cursor.getColumnIndex("_data"));
                        String text;
                        if (data != null) {
                            text = getMmsPartText(partId);
                        } else {
                            text = cursor.getString(cursor.getColumnIndex("text"));
                        }
                        body.append(text);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return body.toString();
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