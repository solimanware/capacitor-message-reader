package ai.soliman.plugins.messagereader;

import android.Manifest;
import android.content.pm.PackageManager;

import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * This plugin provides functionality to read SMS messages on Android devices.
 */
@CapacitorPlugin(
    name = "MessageReader",
    permissions = {
        @Permission(strings = {Manifest.permission.READ_SMS}, alias = "readSms"),
        @Permission(strings = {Manifest.permission.READ_CONTACTS}, alias = "readContacts")
    }
)
public class MessageReaderPlugin extends Plugin {

    private MessageReader messageReader;

    @Override
    public void load() {
        messageReader = new MessageReader(getContext());
    }

    /**
     * Retrieves SMS messages based on the provided filter options.
     * 
     * @param call The plugin call containing filter options.
     */
    @PluginMethod
    public void getMessages(PluginCall call) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            requestPermissionForAlias("readSms", call, "fetchMessages");
        } else {
            fetchMessages(call);
        }
    }

    /**
     * Fetches messages after permissions have been granted.
     * 
     * @param call The plugin call containing filter options.
     */
    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    private void fetchMessages(PluginCall call) {
        try {
            GetMessageFilterInput filter = createFilterFromCall(call);
            JSONArray messages = messageReader.getMessages(filter);
            JSObject ret = new JSObject();
            ret.put("messages", messages);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to retrieve messages", e);
        }
    }

    private GetMessageFilterInput createFilterFromCall(PluginCall call) throws JSONException {
        GetMessageFilterInput filter = new GetMessageFilterInput();

        if (call.hasOption("ids")) {
            filter.setIds(call.getArray("ids").toList());
        }
        if (call.hasOption("body")) {
            filter.setBody(call.getString("body"));
        }
        if (call.hasOption("sender")) {
            filter.setSender(call.getString("sender"));
        }
        if (call.hasOption("minDate")) {
            filter.setMinDate(call.getLong("minDate"));
        }
        if (call.hasOption("maxDate")) {
            filter.setMaxDate(call.getLong("maxDate"));
        }
        if (call.hasOption("indexFrom")) {
            filter.setIndexFrom(call.getInt("indexFrom"));
        }
       
        if (call.hasOption("limit")) {
            filter.setLimit(call.getInt("limit"));
        }

        return filter;
    }

    /**
     * Checks the current permission status for reading SMS messages.
     * 
     * @param call The plugin call.
     */
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject permissionStatus = new JSObject();
        permissionStatus.put("messages", getPermissionState("readSms").toString());
        call.resolve(permissionStatus);
    }

    /**
     * Requests permission to read SMS messages.
     * 
     * @param call The plugin call.
     */
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        requestPermissionForAlias("readSms", call, "permissionsCallback");
    }

    private void permissionsCallback(PluginCall call) {
        if (getPermissionState("readSms") == PermissionState.GRANTED) {
            fetchMessages(call);
        } else {
            call.reject("Permissions not granted");
        }
    }
}