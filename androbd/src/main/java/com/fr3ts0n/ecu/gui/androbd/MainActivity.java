/*
 * (C) Copyright 2015 by fr3ts0n <erwin.scheuch-heilig@gmx.at>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

package com.fr3ts0n.ecu.gui.androbd;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.fr3ts0n.androbd.plugin.Plugin;
import com.fr3ts0n.androbd.plugin.mgr.PluginManager;
import com.fr3ts0n.ecu.EcuCodeItem;
import com.fr3ts0n.ecu.EcuDataItem;
import com.fr3ts0n.ecu.EcuDataItems;
import com.fr3ts0n.ecu.EcuDataPv;
import com.fr3ts0n.ecu.prot.obd.ElmProt;
import com.fr3ts0n.ecu.prot.obd.ObdProt;
import com.fr3ts0n.pvs.ProcessVar;
import com.fr3ts0n.pvs.PvChangeEvent;
import com.fr3ts0n.pvs.PvChangeListener;
import com.fr3ts0n.pvs.PvList;

import org.json.JSONException;
import org.json.JSONObject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Main Activity for AndrOBD app
 */
public class MainActivity extends PluginManager
        implements PvChangeListener,
        AdapterView.OnItemLongClickListener,
        PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        AbsListView.MultiChoiceModeListener
{
    /**
     * Key names for preferences
     */
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static final String PREF_AUTOHIDE = "autohide_toolbar";
    public static final String PREF_FULLSCREEN = "full_screen";
    public static final String PREF_AUTOHIDE_DELAY = "autohide_delay";
    /**
     * Message types sent from the BluetoothChatService Handler
     */
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_FILE_READ = 2;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_UPDATE_VIEW = 7;
    public static final int MESSAGE_TOOLBAR_VISIBLE = 12;
    /**
     * Mapping list from Plugin.CsvField to EcuDataPv.key
     */
    static final Object[] csvFidMap =
    {
        EcuDataPv.FID_MNEMONIC,
        EcuDataPv.FIELDS[EcuDataPv.FID_DESCRIPT],
        EcuDataPv.FID_MIN,
        EcuDataPv.FID_MAX,
        EcuDataPv.FIELDS[EcuDataPv.FID_UNITS]
    };
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String DEVICE_PORT = "device_port";
    private static final String MEASURE_SYSTEM = "measure_system";
    private static final String NIGHT_MODE = "night_mode";
    private static final String ELM_ADAPTIVE_TIMING = "adaptive_timing_mode";
    private static final String ELM_RESET_ON_NRC = "elm_reset_on_nrc";
    private static final String PREF_USE_LAST = "USE_LAST_SETTINGS";
    private static final String PREF_OVERLAY = "toolbar_overlay";
    private static final String PREF_DATA_DISABLE_MAX = "data_disable_max";
    private static final int MESSAGE_FILE_WRITTEN = 3;
    private static final int MESSAGE_DATA_ITEMS_CHANGED = 6;
    private static final int MESSAGE_OBD_STATE_CHANGED = 8;
    private static final int MESSAGE_OBD_NUMCODES = 9;
    private static final int MESSAGE_OBD_ECUS = 10;
    private static final int MESSAGE_OBD_NRC = 11;
    private static final String TAG = "AndrOBD";
    /**
     * internal Intent request codes
     */
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_SELECT_FILE = 4;
    private static final int REQUEST_SETTINGS = 5;
    private static final int REQUEST_CONNECT_DEVICE_USB = 6;
    private static final int REQUEST_GRAPH_DISPLAY_DONE = 7;
    /**
     * app exit parameters
     */
    private static final int EXIT_TIMEOUT = 2500;
    /**
     * time between display updates to represent data changes
     */
    private static final int DISPLAY_UPDATE_TIME = 250;
    private static final String LOG_MASTER = "log_master";
    private static final String KEEP_SCREEN_ON = "keep_screen_on";
    private static final String ELM_CUSTOM_INIT_CMDS = "elm_custom_init_cmds";
    /**
     * Logging
     */
    private static final Logger rootLogger = Logger.getLogger("");
    private static final Logger log = Logger.getLogger(TAG);
    /**
     * Timer for display updates
     */
    private static Timer updateTimer;
    /**
     * empty string set as default parameter
     */
    private static final Set<String> emptyStringSet = new HashSet<>();
    /**
     * Container for Plugin-provided data
     */
    public static PvList mPluginPvs = new PvList();
    /**
     * current status of night mode
     */
    public static boolean nightMode = false;
    /**
     * app preferences ...
     */
    static SharedPreferences prefs;
    /**
     * dialog builder
     */
    private static AlertDialog.Builder dlgBuilder;
    /**
     * Local Bluetooth adapter
     */
    private static BluetoothAdapter mBluetoothAdapter = null;
    /**
     * Name of the connected BT device
     */
    private static String mConnectedDeviceName = null;
    /**
     * menu object
     */
    private static Menu menu;
    /**
     * Data list adapters
     */
    private static ObdItemAdapter mPidAdapter;
    private static VidItemAdapter mVidAdapter;
    private static TidItemAdapter mTidAdapter;
    private static DfcItemAdapter mDfcAdapter;
    private static PluginDataAdapter mPluginDataAdapter;
    private static ObdItemAdapter currDataAdapter;
    /**
     * initial state of bluetooth adapter
     */
    private static boolean initialBtStateEnabled = false;
    /**
     * last time of back key pressed
     */
    private static long lastBackPressTime = 0;
    /**
     * toast for showing exit message
     */
    private static Toast exitToast = null;

    private View mListView;

    private Button goToLoginButton;

    private Button log_out_button;

    private RelativeLayout relativeLayoutUser;
    private TextView usernameTextView;

    /**
     * Flag to temporarily ignore NRCs
     * This flag ist used to temporarily allow negative OBD responses without issuing an error message.
     * i.e. un-supported mode 0x0A for DFC reading
     */
    private static boolean ignoreNrcs = false;

    /**
     * handler for freeze frame selection
     */
    private final AdapterView.OnItemSelectedListener ff_selected = new AdapterView.OnItemSelectedListener()
    {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
            CommService.elm.setFreezeFrame_Id(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent)
        {

        }
    };
    /**
     * Member object for the BT comm services
     */
    private CommService mCommService = null;
    /**
     * file helper
     */
    private FileHelper fileHelper;
    /**
     * the local list view
     */
    /**
     * current data view mode
     */
    private DATA_VIEW_MODE dataViewMode = DATA_VIEW_MODE.LIST;
    /**
     * AutoHider for the toolbar
     */
    private AutoHider toolbarAutoHider;
    /**
     * log file handler
     */
    private FileHandler logFileHandler;
    /**
     * current OBD service
     */
    private int obdService = ElmProt.OBD_SVC_NONE;
    /**
     * current operating mode
     */
    private MODE mode = MODE.OFFLINE;
    /**
     * Handle message requests
     */
    @SuppressLint("HandlerLeak")
    private transient final Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            try
            {
                PropertyChangeEvent evt;

                // log trace message for received handler notification event
                log.log(Level.FINEST, String.format("Handler notification: %s", msg.toString()));

                switch (msg.what)
                {
                    case MESSAGE_STATE_CHANGE:
                        // log trace message for received handler notification event
                        log.log(Level.FINEST, String.format("State change: %s", msg.toString()));
                        switch ((CommService.STATE) msg.obj)
                        {
                            case CONNECTED:
                                onConnect();
                                break;

                            case CONNECTING:
                                setStatus(R.string.title_connecting);
                                break;

                            default:
                                onDisconnect();
                                break;
                        }
                        break;

                    case MESSAGE_FILE_WRITTEN:
                        break;

                    // data has been read - finish up
                    case MESSAGE_FILE_READ:
                        // set listeners for data structure changes
                        setDataListeners();
                        // set adapters data source to loaded list instances
                        mPidAdapter.setPvList(ObdProt.PidPvs);
                        mVidAdapter.setPvList(ObdProt.VidPvs);
                        mTidAdapter.setPvList(ObdProt.VidPvs);
                        mDfcAdapter.setPvList(ObdProt.tCodes);
                        // set OBD data mode to the one selected by input file
                        setObdService(CommService.elm.getService(), getString(R.string.saved_data));
                        // Check if last data selection shall be restored
                        if (obdService == ObdProt.OBD_SVC_DATA)
                        {
                            checkToRestoreLastDataSelection();
                            checkToRestoreLastViewMode();
                        }
                        break;

                    case MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.connected_to) + mConnectedDeviceName,
                                Toast.LENGTH_SHORT).show();
                        break;

                    case MESSAGE_TOAST:
                        Toast.makeText(getApplicationContext(),
                                msg.getData().getString(TOAST),
                                Toast.LENGTH_SHORT).show();
                        break;

                    case MESSAGE_DATA_ITEMS_CHANGED:
                        PvChangeEvent event = (PvChangeEvent) msg.obj;
                        switch (event.getType())
                        {
                            case PvChangeEvent.PV_ADDED:
                                currDataAdapter.setPvList(currDataAdapter.pvs);
                                try
                                {
                                    if (event.getSource() == ObdProt.PidPvs)
                                    {
                                        // append plugin measurements to data list
                                        currDataAdapter.addAll(mPluginPvs.values());
                                        // Check if last data selection shall be restored
                                        checkToRestoreLastDataSelection();
                                        checkToRestoreLastViewMode();
                                    }
                                } catch (Exception e)
                                {
                                    log.log(Level.FINER, "Error adding PV", e);
                                }
                                break;

                            case PvChangeEvent.PV_CLEARED:
                                currDataAdapter.clear();
                                break;
                        }
                        break;

                    case MESSAGE_UPDATE_VIEW:
                        getListView().invalidateViews();
                        break;

                    // handle state change in OBD protocol
                    case MESSAGE_OBD_STATE_CHANGED:
                        evt = (PropertyChangeEvent) msg.obj;
                        ElmProt.STAT state = (ElmProt.STAT) evt.getNewValue();
                        /* Show ELM status only in ONLINE mode */
                        if (getMode() != MODE.DEMO)
                        {
                            setStatus(getResources().getStringArray(R.array.elmcomm_states)[state
                                    .ordinal()]);
                        }
                        // if last selection shall be restored ...
                        if (istRestoreWanted(PRESELECT.LAST_SERVICE))
                        {
                            if (state == ElmProt.STAT.ECU_DETECTED)
                            {
                                setObdService(prefs.getInt(PRESELECT.LAST_SERVICE.toString(), 0),
                                        null);
                            }
                        }
                        break;

                    // handle change in number of fault codes
                    case MESSAGE_OBD_NUMCODES:
                        evt = (PropertyChangeEvent) msg.obj;
                        setNumCodes((Integer) evt.getNewValue());
                        break;

                    // handle ECU detection event
                    case MESSAGE_OBD_ECUS:
                        evt = (PropertyChangeEvent) msg.obj;
                        selectEcu((Set<Integer>) evt.getNewValue());
                        break;

                    // handle negative result code from OBD protocol
                    case MESSAGE_OBD_NRC:
                        // show error dialog ...
                        if(! ignoreNrcs)
                        {
                            evt = (PropertyChangeEvent) msg.obj;
                            ObdProt.NRC nrc = (ObdProt.NRC) evt.getOldValue();
                            String nrcMsg = (String) evt.getNewValue();
                            switch (nrc.disp)
                            {
                                case ERROR:
                                    dlgBuilder
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setTitle(R.string.obd_error)
                                        .setMessage(nrcMsg)
                                        .setPositiveButton(null, null)
                                        .show();
                                    break;
                                // Display warning (with confirmation)
                                case WARN:
                                    dlgBuilder
                                        .setIcon(android.R.drawable.ic_dialog_info)
                                        .setTitle(R.string.obd_error)
                                        .setMessage(nrcMsg)
                                        .setPositiveButton(null, null)
                                        .show();
                                    break;
                                // Display notification (no confirmation)
                                case NOTIFY:
                                    Toast.makeText(getApplicationContext(),
                                        nrcMsg,
                                        Toast.LENGTH_SHORT).show();
                                    break;

                                case HIDE:
                                default:
                                    // intentionally ignore
                            }
                        }
                        break;

                    // set toolbar visibility
                    case MESSAGE_TOOLBAR_VISIBLE:
                        Boolean visible = (Boolean) msg.obj;
                        // log action
                        log.fine(String.format("ActionBar: %s", visible ? "show" : "hide"));
                        // set action bar visibility
                        ActionBar ab = getActionBar();
                        if (ab != null)
                        {
                            if (visible)
                            {
                                ab.show();
                            } else
                            {
                                ab.hide();
                            }
                        }
                        break;
                }
            } catch (Exception ex)
            {
                log.log(Level.SEVERE, "Error in mHandler", ex);
            }
        }
    };

    /**
     * Set fixed PIDs for protocol to specified list of PIDs
     *
     * @param pidNumbers List of PIDs
     */
    public static void setFixedPids(Set<Integer> pidNumbers)
    {
        int[] pids = new int[pidNumbers.size()];
        int i = 0;
        for (Integer pidNum : pidNumbers)
        {
            pids[i++] = pidNum;
        }
        Arrays.sort(pids);
        // set protocol fixed PIDs
        ObdProt.setFixedPid(pids);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Instantiate superclass
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_PROGRESS);

        // Get additional permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Storage Permissions
            final int REQUEST_EXTERNAL_STORAGE = 1;
            final String[] PERMISSIONS_STORAGE = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            // Workaround for FileUriExposedException in Android >= M
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        // Set content view
        checkLoginStatusAndSetLayout();

        dlgBuilder = new AlertDialog.Builder(this);

        // Get preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Overlay feature setup
        if (prefs.getBoolean(PREF_AUTOHIDE, false) && prefs.getBoolean(PREF_OVERLAY, false)) {
            getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        }

        // Set up all data adapters
        mPidAdapter = new ObdItemAdapter(this, R.layout.obd_item, ObdProt.PidPvs);
        mVidAdapter = new VidItemAdapter(this, R.layout.obd_item, ObdProt.VidPvs);
        mTidAdapter = new TidItemAdapter(this, R.layout.obd_item, ObdProt.VidPvs);
        mDfcAdapter = new DfcItemAdapter(this, R.layout.obd_item, ObdProt.tCodes);
        mPluginDataAdapter = new PluginDataAdapter(this, R.layout.obd_item, mPluginPvs);
        currDataAdapter = mPidAdapter;

        // Get list view
        mListView = getWindow().getLayoutInflater().inflate(R.layout.obd_list, null);

        // Update all settings from preferences
        onSharedPreferenceChanged(prefs, null);

        // Set up logging system
        setupLoggers();

        // Log program startup
        log.info(String.format("%s %s starting", getString(R.string.app_name), getString(R.string.app_version)));

        // Create file helper instance
        fileHelper = new FileHelper(this);
        setDataListeners(); // Set listeners for data structure changes
        CommService.elm.addPropertyChangeListener(this); // Automate ELM status display

        // Set up action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
        }
        setAutoHider(prefs.getBoolean(PREF_AUTOHIDE, false)); // Start automatic toolbar hider

        // Override comm medium with USB connect intent
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(getIntent().getAction())) {
            CommService.medium = CommService.MEDIUM.USB;
        }

        // Handle communication medium
        switch (CommService.medium) {
            case BLUETOOTH:
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                log.fine("Adapter: " + mBluetoothAdapter);
                if (getMode() != MODE.DEMO && mBluetoothAdapter != null) {
                    initialBtStateEnabled = mBluetoothAdapter.isEnabled();
                    if (!initialBtStateEnabled) {
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    } else if (istRestoreWanted(PRESELECT.LAST_DEV_ADDRESS)) {
                        setMode(MODE.ONLINE); // Auto-connect last device
                    }
                }
                break;

            case USB:
            case NETWORK:
                setMode(MODE.ONLINE);
                break;
        }
    }

    public void onLogoutClick(View view) {
        // Show Toast when the logout button is clicked
//        Toast.makeText(MainActivity.this, "onLogoutClick...", Toast.LENGTH_SHORT).show();

        ListView listView = findViewById(android.R.id.list); // Get ListView
        BaseAdapter adapter = (BaseAdapter) listView.getAdapter(); // Get the adapter

        // Check if the adapter is not null
        if (adapter != null) {
            StringBuilder toastMessage = new StringBuilder();
            collectAdapterItems(adapter, toastMessage); // Collect all items from the adapter

            // Get the JWT token from SharedPreferences
            String jwtToken = getJwtToken();
            if (jwtToken != null) {
                processUserDetails(jwtToken, toastMessage.toString()); // Process user details and proceed with the logic
            } else {
                Toast.makeText(MainActivity.this, "Login first", Toast.LENGTH_SHORT).show();
                // Show login screen
            }
        } else {
            Log.d("ListViewItems", "Adapter is null");
            Toast.makeText(this, "No data available in the list.", Toast.LENGTH_SHORT).show();
        }
    }

    private void collectAdapterItems(BaseAdapter adapter, StringBuilder toastMessage) {
        // Iterate over all items in the adapter and append to the StringBuilder
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            Log.d("ListViewItems", "Item " + i + ": " + item.toString());
            toastMessage.append(item.toString());
        }
    }

    private String getJwtToken() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        return sharedPreferences.getString("accessToken", null);
    }

    private void processUserDetails(String jwtToken, String faultCodes) {
        try {
            JSONObject userJson = new JSONObject(jwtToken);
            String username = userJson.getString("username");
            String email = userJson.getString("email");
            String phone = userJson.getString("phone");
            String userId = userJson.getString("id");

            // Show user details and fault codes
            String messageToShow = faultCodes.isEmpty() ? "No items found." : faultCodes;
//            Toast.makeText(MainActivity.this, "Username: " + username + "\nEmail: " + email + "\n" + messageToShow, Toast.LENGTH_SHORT).show();

            if (!faultCodes.isEmpty()) {
                showCarDetailsDialog(username, email, userId, phone, messageToShow);
            } else {
                Toast.makeText(MainActivity.this, "No fault codes", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error parsing user details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showCarDetailsDialog(String username, String email, String userId, String phone, String messageToShow) {
        // Create and show an AlertDialog to enter car details
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Car Details");

        final EditText modelInput = new EditText(this);
        modelInput.setHint("Car Model");

        final EditText yearInput = new EditText(this);
        yearInput.setHint("Car Year");

        final EditText carMakeInput = new EditText(this);
        carMakeInput.setHint("Car Make");

        final EditText engineTypeInput = new EditText(this);
        engineTypeInput.setHint("Car Engine Type");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(carMakeInput);
        layout.addView(modelInput);
        layout.addView(yearInput);
        layout.addView(engineTypeInput);
        builder.setView(layout);

        builder.setPositiveButton("Submit", null); // Set to null for custom handling
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> handleCarDetailsSubmission(username, email, phone, userId, messageToShow, modelInput, yearInput, carMakeInput, engineTypeInput, dialog));
        });

        dialog.show();
    }

    private void handleCarDetailsSubmission(String username, String email, String phone, String userId, String messageToShow,
                                            EditText modelInput, EditText yearInput, EditText carMakeInput, EditText engineTypeInput, AlertDialog dialog) {

        String carBrand = modelInput.getText().toString().trim();
        String carMake = carMakeInput.getText().toString().trim();
        String carYear = yearInput.getText().toString().trim();
        String carEngineType = engineTypeInput.getText().toString().trim();
        // Ensure JWT token is valid and safely parsed
        String jwtToken = getJwtToken();
        String userToken = null;
        try {
            JSONObject userJson = new JSONObject(jwtToken);
            userToken = userJson.getString("accessToken");
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error parsing JWT token", Toast.LENGTH_SHORT).show();
            return;  // Exit the method if JWT parsing fails
        }
        // Validate inputs: check if fields are not empty
        if (carBrand.isEmpty() || carMake.isEmpty() || carYear.isEmpty() || carEngineType.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return; // Exit early if validation fails
        }
        // Optionally validate carYear (if needed, e.g., numeric and reasonable)
        try {
            int year = Integer.parseInt(carYear);
            if (year < 1900 || year > 2100) {
                Toast.makeText(MainActivity.this, "Please enter a valid car year", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this, "Invalid year format", Toast.LENGTH_SHORT).show();
            return;
        }
        // If all validations pass, save car details and proceed
        saveCarDetailsToSharedPreferences(carBrand, carMake, carYear, carEngineType, messageToShow, userToken);
        new SendCarDetailsTask(username, email, phone, carMake, carBrand, carYear, carEngineType, messageToShow, userId).execute();

        // Open the WebView and dismiss the dialog
        openWebView();
        dialog.dismiss();
    }

    private void saveCarDetailsToSharedPreferences(String carBrand, String carMake, String carYear, String carEngineType, String faultCode, String userToken) {
        SharedPreferences sharedPref = getSharedPreferences("carDetails", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("carMake", carMake);
        editor.putString("carBrand", carBrand);
        editor.putString("carYear", carYear);
        editor.putString("carEngineType", carEngineType);
        editor.putString("faultCode", faultCode);
        editor.putString("userToken", userToken);
        editor.apply();
    }

    private class SendCarDetailsTask extends AsyncTask<Void, Void, String> {
        private String username, email, phone, carMake, carBrand, carYear, carEngineType, faultCode, userId;

        SendCarDetailsTask(String username, String email, String phone, String carMake, String carBrand, String carYear, String carEngineType, String faultCode, String userId) {
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.carMake = carMake;
            this.carBrand = carBrand;
            this.carYear = carYear;
            this.carEngineType = carEngineType;
            this.faultCode = faultCode;
            this.userId = userId;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                // Create the JSON body with all necessary parameters
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("car_make", carMake);
                jsonBody.put("car_model", carBrand);
                jsonBody.put("car_year", carYear);
                jsonBody.put("fault_code", faultCode);
                jsonBody.put("user_name", username);
                jsonBody.put("user_email", email);
                jsonBody.put("user_phone", phone);
                jsonBody.put("user_id", userId);

                // Log the JSON body for debugging purposes
                Log.d("RequestData", "JSON Body: " + jsonBody.toString());

                // Set up the URL and open the connection
                URL url = new URL("https://aimechanic.asoroautomotive.com/api/createDiagnostics");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Set timeouts for connection and reading the response
                connection.setConnectTimeout(15000);  // 15 seconds for connection
                connection.setReadTimeout(15000);     // 15 seconds for reading the response

                // Write the JSON data to the output stream
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes("UTF-8"));
                    os.flush();
                }

                // Get the response code from the server
                int responseCode = connection.getResponseCode();
                Log.d("Response", "Response Code: " + responseCode);

                // Handle the response from the server
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return "Request Successful";
                } else {
                    // Capture and log the error response body
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                        String inputLine;
                        StringBuilder errorResponse = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            errorResponse.append(inputLine);
                        }
                        Log.e("Response", "Error response: " + errorResponse.toString());
                    }
                    return "Failed to send request. Response Code: " + responseCode;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Error sending data: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            // Handle the result here, such as showing a Toast
            Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
        }
    }



    private void openWebView() {
        String url = "https://gobd-users.onrender.com/";
        Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    public void logoutUser(View view) {
        // Clear the access token from SharedPreferences (UserPrefs)
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("accessToken");  // Remove the access token
        editor.apply();  // Apply the changes asynchronously

        // Optionally show a Toast message confirming logout
        Toast.makeText(MainActivity.this, "Logged out successfully!", Toast.LENGTH_SHORT).show();

        // Navigate back to the MainActivity (or another screen)
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        startActivity(intent);
        // Finish the current activity so the user cannot navigate back
//        finish();
    }

    /**
     * Handler for application start event
     */
    @Override
    public void onStart()
    {
        super.onStart();
        // If the adapter is null, then Bluetooth is not supported
        if (CommService.medium == CommService.MEDIUM.BLUETOOTH && mBluetoothAdapter == null)
        {
            // start ELM protocol demo loop
            setMode(MODE.DEMO);
        }
    }

    @Override protected void onPause()
    {
        super.onPause();

        // stop data display update timer
        updateTimer.cancel();
    }

    @Override protected void onResume()
    {
        // set up data display update timer
        updateTimer = new Timer();
        final TimerTask updateTask = new TimerTask()
        {
            @Override
            public void run()
            {
                /* forward message to update the view */
                Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_UPDATE_VIEW);
                mHandler.sendMessage(msg);
            }
        };
        updateTimer.schedule(updateTask, 0, DISPLAY_UPDATE_TIME);

        super.onResume();
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy()
    {
        // Stop toolbar hider thread
        setAutoHider(false);

        try
        {
            // Reduce ELM power consumption by setting it to sleep
            CommService.elm.goToSleep();
            // wait until message is out ...
            Thread.sleep(100, 0);
        } catch (InterruptedException e)
        {
            // do nothing
            log.log(Level.FINER, e.getLocalizedMessage());
        }

        /* don't listen to ELM data changes any more */
        removeDataListeners();
        // don't listen to ELM property changes any more
        CommService.elm.removePropertyChangeListener(this);

        // stop demo service if it was started
        setMode(MODE.OFFLINE);

        // stop communication service
        if (mCommService != null)
        {
            mCommService.stop();
        }

        // if bluetooth adapter was switched OFF before ...
        if (mBluetoothAdapter != null && !initialBtStateEnabled)
        {
            // ... turn it OFF again
            mBluetoothAdapter.disable();
        }

        log.info(String.format("%s %s finished",
                getString(R.string.app_name),
                getString(R.string.app_version)));

        /* remove log file handler, if available (file access was granted) */
        if (logFileHandler != null) logFileHandler.close();
        Logger.getLogger("").removeHandler(logFileHandler);

        super.onDestroy();
    }

    @Override
    public void setContentView(int layoutResID)
    {
        setContentView(getLayoutInflater().inflate(layoutResID, null));
    }

    @Override
    public void setContentView(View view)
    {
        super.setContentView(view);
        getListView().setOnTouchListener(toolbarAutoHider);
    }

    /**
     * handle pressing of the BACK-KEY
     */
    @Override
    public void onBackPressed()
    {
        if (getListAdapter() == pluginHandler)
        {
            setObdService(obdService, null);
        } else
        {
            if (CommService.elm.getService() != ObdProt.OBD_SVC_NONE)
            {
                if (dataViewMode != DATA_VIEW_MODE.LIST)
                {
                    setDataViewMode(DATA_VIEW_MODE.LIST);
                    checkToRestoreLastDataSelection();
                } else
                {
                    setObdService(ObdProt.OBD_SVC_NONE, null);
                }
            } else
            {
                if (lastBackPressTime < System.currentTimeMillis() - EXIT_TIMEOUT)
                {
                    exitToast =
                            Toast.makeText(this, R.string.back_again_to_exit, Toast.LENGTH_SHORT);
                    exitToast.show();
                    lastBackPressTime = System.currentTimeMillis();
                } else
                {
                    if (exitToast != null)
                    {
                        exitToast.cancel();
                    }
                    super.onBackPressed();
                }
            }
        }
    }

    /**
     * Handler for options menu creation event
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        getMenuInflater().inflate(R.menu.obd_services, menu.findItem(R.id.obd_services).getSubMenu());
        MainActivity.menu = menu;
        // update menu item status for current conversion
        setConversionSystem(EcuDataItem.cnvSystem);
        return true;
    }

    /**
     * Handler for Options menu selection
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.day_night_mode:
                // toggle night mode setting
                prefs.edit().putBoolean(NIGHT_MODE, !nightMode).apply();
                return true;

            case R.id.secure_connect_scan:
                setMode(MODE.ONLINE);
                return true;

            case R.id.reset_preselections:
                clearPreselections();
                recreate();
                return true;

            case R.id.disconnect:
                // stop communication service
                if (mCommService != null)
                {
                    mCommService.stop();
                }
                setMode(MODE.OFFLINE);
                return true;

            case R.id.settings:
                // Launch the BtDeviceListActivity to see devices and do scan
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivityForResult(settingsIntent, REQUEST_SETTINGS);
                return true;

            case R.id.plugin_manager:
                setManagerView();
                return true;

            case R.id.save:
                // save recorded data (threaded)
                fileHelper.saveDataThreaded();
                return true;

            case R.id.load:
                setMode(MODE.FILE);
                return true;

            case R.id.service_none:
                setObdService(ObdProt.OBD_SVC_NONE, item.getTitle());
                return true;

            case R.id.service_data:
                setObdService(ObdProt.OBD_SVC_DATA, item.getTitle());
                return true;

            case R.id.service_vid_data:
                setObdService(ObdProt.OBD_SVC_VEH_INFO, item.getTitle());
                return true;

            case R.id.service_freezeframes:
                setObdService(ObdProt.OBD_SVC_FREEZEFRAME, item.getTitle());
                return true;

            case R.id.service_testcontrol:
                setObdService(ObdProt.OBD_SVC_CTRL_MODE, item.getTitle());
                return true;

            case R.id.service_codes:
                setObdService(ObdProt.OBD_SVC_READ_CODES, item.getTitle());
                return true;

            case R.id.service_clearcodes:
                clearObdFaultCodes();
                setObdService(ObdProt.OBD_SVC_READ_CODES, item.getTitle());
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked)
    {
        // Intentionally do nothing
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu)
    {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.context_graph, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu)
    {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.chart_selected:
                setDataViewMode(DATA_VIEW_MODE.CHART);
                return true;

            case R.id.hud_selected:
                setDataViewMode(DATA_VIEW_MODE.HEADUP);
                return true;

            case R.id.dashboard_selected:
                setDataViewMode(DATA_VIEW_MODE.DASHBOARD);
                return true;

            case R.id.filter_selected:
                setDataViewMode(DATA_VIEW_MODE.FILTERED);
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode)
    {

    }

    /**
     * Handler for result messages from other activities
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        boolean secureConnection = false;

        switch (requestCode)
        {
            // device is connected
            case REQUEST_CONNECT_DEVICE_SECURE:
                secureConnection = true;
                // no break here ...
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When BtDeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK)
                {
                    // Get the device MAC address
                    String address = Objects.requireNonNull(data.getExtras()).getString(
                            BtDeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // save reported address as last setting
                    prefs.edit().putString(PRESELECT.LAST_DEV_ADDRESS.toString(), address).apply();
                    connectBtDevice(address, secureConnection);
                } else
                {
                    setMode(MODE.OFFLINE);
                }
                break;

            // USB device selected
            case REQUEST_CONNECT_DEVICE_USB:
                // DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK)
                {
                    mCommService = new UsbCommService(this, mHandler);
                    mCommService.connect(UsbDeviceListActivity.selectedPort, true);
                } else
                {
                    setMode(MODE.OFFLINE);
                }
                break;

            // bluetooth enabled
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK)
                {
                    // Start online mode
                    setMode(MODE.ONLINE);
                } else
                {
                    // Start demo service Thread
                    setMode(MODE.DEMO);
                }
                break;

            // file selected
            case REQUEST_SELECT_FILE:
                if (resultCode == RESULT_OK)
                {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    log.info("Load content: " + uri);
                    // load data ...
                    fileHelper.loadDataThreaded(uri, mHandler);
                    // don't allow saving it again
                    setMenuItemEnable(R.id.save, false);
                    setMenuItemEnable(R.id.obd_services, true);
                }
                break;

            // settings finished
            case REQUEST_SETTINGS:
            {
                // change handling done by callbacks
            }
            break;

            // graphical data view finished
            case REQUEST_GRAPH_DISPLAY_DONE:
                // let context know that we are in list mode again ...
                dataViewMode = DATA_VIEW_MODE.LIST;
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        // keep main display on?
        if (key == null || KEEP_SCREEN_ON.equals(key))
        {
            getWindow().addFlags(prefs.getBoolean(KEEP_SCREEN_ON, false)
                    ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    : 0);
        }

        // FULL SCREEN operation based on preference settings
        if (key == null || PREF_FULLSCREEN.equals(key))
        {
            getWindow().setFlags(prefs.getBoolean(PREF_FULLSCREEN, true)
                            ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // night mode
        if (key == null || NIGHT_MODE.equals(key))
        {
            setNightMode(prefs.getBoolean(NIGHT_MODE, false));
        }

        // set default comm medium
        if (key == null || SettingsActivity.KEY_COMM_MEDIUM.equals(key))
        {
            CommService.medium =
                    CommService.MEDIUM.values()[
                            getPrefsInt(SettingsActivity.KEY_COMM_MEDIUM, 0)];
        }

        // enable/disable ELM adaptive timing
        if (key == null || ELM_ADAPTIVE_TIMING.equals(key))
        {
            CommService.elm.mAdaptiveTiming.setMode(
                    ElmProt.AdaptTimingMode.valueOf(
                            prefs.getString(ELM_ADAPTIVE_TIMING,
                                    ElmProt.AdaptTimingMode.OFF.toString())));
        }

        // set protocol flag to initiate immediate reset on NRC reception
        if (key == null || ELM_RESET_ON_NRC.equals(key))
        {
            CommService.elm.setResetOnNrc(prefs.getBoolean(ELM_RESET_ON_NRC, false));
        }

        // set custom ELM init commands
        if (key == null || ELM_CUSTOM_INIT_CMDS.equals(key))
        {
            String value = prefs.getString(ELM_CUSTOM_INIT_CMDS, null);
            if (value != null && value.length() > 0)
            {
                CommService.elm.setCustomInitCommands(value.split("\n"));
            }
        }

        // ELM timeout
        if (key == null || SettingsActivity.ELM_MIN_TIMEOUT.equals(key))
        {
            CommService.elm.mAdaptiveTiming.setElmTimeoutMin(
                    getPrefsInt(SettingsActivity.ELM_MIN_TIMEOUT,
                            CommService.elm.mAdaptiveTiming.getElmTimeoutMin()));
        }

        // ... measurement system
        if (key == null || MEASURE_SYSTEM.equals(key))
        {
            setConversionSystem(getPrefsInt(MEASURE_SYSTEM, EcuDataItem.SYSTEM_METRIC));
        }

        // ... preferred protocol
        if (key == null || SettingsActivity.KEY_PROT_SELECT.equals(key))
        {
            ElmProt.setPreferredProtocol(getPrefsInt(SettingsActivity.KEY_PROT_SELECT, 0));
        }

        // log levels
        if (key == null || LOG_MASTER.equals(key))
        {
            setLogLevels();
        }

        // update from protocol extensions
        if (key == null || key.startsWith("ext_file-"))
        {
            loadPreferredExtensions();
        }

        // set disabled ELM commands
        if (key == null || SettingsActivity.ELM_CMD_DISABLE.equals(key))
        {
            ElmProt.disableCommands(prefs.getStringSet(SettingsActivity.ELM_CMD_DISABLE, null));
        }

        // AutoHide ToolBar
        if (key == null || PREF_AUTOHIDE.equals(key) || PREF_AUTOHIDE_DELAY.equals(key))
        {
            setAutoHider(prefs.getBoolean(PREF_AUTOHIDE, false));
        }

        // Max. data disabling debounce counter
        if (key == null || PREF_DATA_DISABLE_MAX.equals(key))
        {
            EcuDataItem.MAX_ERROR_COUNT = getPrefsInt(PREF_DATA_DISABLE_MAX, 3);
        }

        // Customized PID display color preference
        if (key != null)
        {
            // specific key -> update single
            updatePidColor(key);
            updatePidDisplayRange(key);
            updatePidUpdatePeriod(key);
        }
        else
        {
            // loop through all keys
            for (String currKey : prefs.getAll().keySet())
            {
                // update by key
                updatePidColor(currKey);
                updatePidDisplayRange(currKey);
                updatePidUpdatePeriod(currKey);
            }
        }
    }

    /**
     * Update PID PV display color from preference
     * @param key Preference key
     */
    private void updatePidColor(String key)
    {
        int pos = key.indexOf("/".concat(EcuDataPv.FID_COLOR));
        if(pos >= 0)
        {
            String mnemonic = key.substring(0, pos);
            EcuDataItem itm = EcuDataItems.byMnemonic.get(mnemonic);
            // Default BLACK is to detect key removal
            Integer color = prefs.getInt(key, Color.BLACK);
            if(Color.BLACK != color)
            {
                itm.pv.put(EcuDataPv.FID_COLOR, color);
                log.info(String.format("PID pref %s=#%08x", key, color));
            }
        }
    }

    /**
     * Update PID PV display color from preference
     * @param key Preference key
     */
    private void updatePidDisplayRange(String key)
    {
        final String[] rangeFields = new String[]
        {
            EcuDataPv.FID_MIN,
            EcuDataPv.FID_MAX
        };
        // Loop through <MIN/MAX>> fields
        for (String field : rangeFields)
        {
            // If preference key matches PID/<MIN/MAX>
            int pos = key.indexOf("/".concat(field));
            if (pos >= 0)
            {
                // Default MAX_VALUE is to detect key removal
                Number value = prefs.getFloat(key, Float.MAX_VALUE);
                if (Float.MAX_VALUE != value.floatValue())
                {
                    // Find corresponding data item
                    String mnemonic = key.substring(0, pos);
                    EcuDataItem itm = EcuDataItems.byMnemonic.get(mnemonic);
                    // update display range limit in data item
                    itm.pv.put(field, value);

                    log.info(String.format("PID pref %s=%f", key, value));
                }
            }
        }
    }

    /**
     * Update customized PID display update period from preference
     * @param key Preference key
     */
    private void updatePidUpdatePeriod(String key)
    {
            // If preference key matches PID/<MIN/MAX>
            int pos = key.indexOf("/".concat(EcuDataPv.FID_UPDT_PERIOD));
            if (pos >= 0)
            {
                // Default MAX_VALUE is to detect key removal
                long value = prefs.getLong(key, 0);
                if (0 != value)
                {
                    // Find corresponding data item
                    String mnemonic = key.substring(0, pos);
                    EcuDataItem itm = EcuDataItems.byMnemonic.get(mnemonic);
                    // update display range limit in data item
                    itm.updatePeriod_ms = value;

                    log.info(String.format("PID pref %s=%f", key, value));
                }
            }
    }

    /**
     * Handle long licks on OBD data list items
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
//        Intent intent;
        EcuDataPv pv;

        switch (CommService.elm.getService()) {
            case ObdProt.OBD_SVC_READ_CODES:
            case ObdProt.OBD_SVC_PERMACODES:
            case ObdProt.OBD_SVC_PENDINGCODES:

                String jwtToken = getJwtToken();
                if (jwtToken != null) {
                    EcuCodeItem dfc = (EcuCodeItem) getListAdapter().getItem(position);
                    String faultCode = String.valueOf(dfc.get(EcuCodeItem.FID_CODE));
                    processUserDetails(jwtToken, faultCode); // Process user details and proceed with the logic
                } else {
                    Toast.makeText(MainActivity.this, "Login first", Toast.LENGTH_SHORT).show();
                    // Show login screen
                }
                break;

            case ObdProt.OBD_SVC_VEH_INFO:
                pv = (EcuDataPv) getListAdapter().getItem(position);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT)),
                        String.valueOf(pv.get(EcuDataPv.FID_VALUE)));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                break;

            case ObdProt.OBD_SVC_CTRL_MODE:
                pv = (EcuDataPv) getListAdapter().getItem(position);
                confirmObdTestControl(pv.get(EcuDataPv.FID_DESCRIPT).toString(),
                        ObdProt.OBD_SVC_CTRL_MODE,
                        pv.getAsInt(EcuDataPv.FID_PID));
                break;
        }
        return true;
    }


    /**
     * Handler for PV change events This handler just forwards the PV change
     * events to the android handler, since all adapter / GUI actions have to be
     * performed from the main handler
     *
     * @param event PvChangeEvent which is reported
     */
    @Override
    public synchronized void pvChanged(PvChangeEvent event)
    {
        // forward PV change to the UI Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DATA_ITEMS_CHANGED);
        if (!event.isChildEvent())
        {
            msg.obj = event;
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Check if restore of specified preselection is wanted from settings
     *
     * @param preselect specified preselect
     * @return flag if preselection shall be restored
     */
    private boolean istRestoreWanted(PRESELECT preselect)
    {
        return prefs.getStringSet(PREF_USE_LAST, emptyStringSet).contains(preselect.toString());
    }

    /**
     * Check if last data selection shall be restored
     * <p>
     * If previously selected items shall be re-selected, then re-select them
     */
    private void checkToRestoreLastDataSelection()
    {
        // if last data items shall be restored
        if (istRestoreWanted(PRESELECT.LAST_ITEMS))
        {
            // get preference for last seleted items
            int[] lastSelectedItems =
                    toIntArray(prefs.getString(PRESELECT.LAST_ITEMS.toString(), ""));
            // select last selected items
            if (lastSelectedItems.length > 0)
            {
                if (!selectDataItems(lastSelectedItems))
                {
                    // if items could not be applied
                    // remove invalid preselection
                    prefs.edit().remove(PRESELECT.LAST_ITEMS.toString()).apply();
                    log.warning(String.format("Invalid preselection: %s",
                            Arrays.toString(lastSelectedItems)));
                }
            }
        }
    }

    /**
     * Check if last view mode shall be restored
     * <p>
     * If last view mode shall be restored by user settings,
     * then restore the last selected view mode
     */
    private void checkToRestoreLastViewMode()
    {
        // if last view mode shall be restored
        if (istRestoreWanted(PRESELECT.LAST_VIEW_MODE))
        {
            // set last data view mode
            DATA_VIEW_MODE lastMode =
                    DATA_VIEW_MODE.valueOf(prefs.getString(PRESELECT.LAST_VIEW_MODE.toString(),
                            DATA_VIEW_MODE.LIST.toString()));
            setDataViewMode(lastMode);
        }
    }

    /**
     * convert result of Arrays.toString(int[]) back into int[]
     *
     * @param input String of array
     * @return int[] of String value
     */
    private int[] toIntArray(String input)
    {
        int[] result = {};
        int numValidEntries = 0;
        try
        {
            String beforeSplit = input.replaceAll("\\[|]|\\s", "");
            String[] split = beforeSplit.split(",");
            int[] ints = new int[split.length];
            for (String s : split)
            {
                if (s.length() > 0)
                {
                    ints[numValidEntries++] = Integer.parseInt(s);
                }
            }
            result = Arrays.copyOf(ints, numValidEntries);
        } catch (Exception ex)
        {
            log.severe(ex.toString());
        }

        return result;
    }

    /**
     * Prompt for selection of a single ECU from list of available ECUs
     *
     * @param ecuAdresses List of available ECUs
     */
    private void selectEcu(final Set<Integer> ecuAdresses)
    {
        // if more than one ECUs available ...
        if (ecuAdresses.size() > 1)
        {
            int preferredAddress = prefs.getInt(PRESELECT.LAST_ECU_ADDRESS.toString(), 0);
            // check if last preferred address matches any of the reported addresses
            if (istRestoreWanted(PRESELECT.LAST_ECU_ADDRESS)
                    && ecuAdresses.contains(preferredAddress))
            {
                // set address
                CommService.elm.setEcuAddress(preferredAddress);
            } else
            {
                // NO match with preference -> allow selection

                // .. allow selection of single ECU address ...
                final CharSequence[] entries = new CharSequence[ecuAdresses.size()];
                // create list of entries
                int i = 0;
                for (Integer addr : ecuAdresses)
                {
                    entries[i++] = String.format("0x%X", addr);
                }
                // show dialog ...
                dlgBuilder
                        .setTitle(R.string.select_ecu_addr)
                        .setItems(entries, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                int address =
                                        Integer.parseInt(entries[which].toString().substring(2), 16);
                                // set address
                                CommService.elm.setEcuAddress(address);
                                // set this as preference (preference change will trigger ELM command)
                                prefs.edit().putInt(PRESELECT.LAST_ECU_ADDRESS.toString(), address)
                                        .apply();
                            }
                        })
                        .show();
            }
        }
    }

    /**
     * OnClick handler - Browse URL from content description
     *
     * @param view view source of click event
     */
    public void browseClickedUrl(View view)
    {
        String url = view.getContentDescription().toString();
        startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)));
    }

    /**
     * Unhide action bar
     */
    private void unHideActionBar()
    {
        if (toolbarAutoHider != null)
        {
            toolbarAutoHider.showComponent();
        }
    }

    protected void setNightMode(boolean nightMode)
    {
        // store last mode selection
        MainActivity.nightMode = nightMode;

        // Set display theme based on specified mode
        setTheme(nightMode ? R.style.AppTheme_Dark : R.style.AppTheme);
        getWindow().getDecorView().setBackgroundColor(nightMode ? Color.BLACK : Color.WHITE);

        // Trigger screen update to get immediate reaction
        setObdService(obdService, null);
    }

    private void setNumCodes(int newNumCodes)
    {
        // set list background based on MIL status
        View list = findViewById(R.id.obd_list);
        if (list != null)
        {
            list.setBackgroundResource((newNumCodes & 0x80) != 0
                    ? R.drawable.mil_on
                    : R.drawable.mil_off);
        }
        // enable / disable freeze frames based on number of codes
        setMenuItemEnable(R.id.service_freezeframes, (newNumCodes != 0));
    }

    /**
     * Set enabled state for a specified menu item
     * * this includes shading disabled items to visualize state
     *
     * @param id      ID of menu item
     * @param enabled flag if to be enabled/disabled
     */
    private void setMenuItemEnable(int id, boolean enabled)
    {
        if (menu != null)
        {
            MenuItem item = menu.findItem(id);
            if (item != null)
            {
                item.setEnabled(enabled);

                // if menu item has icon ...
                Drawable icon = item.getIcon();
                if (icon != null)
                {
                    // set it's shading
                    icon.setAlpha(enabled ? 255 : 127);
                }
            }
        }
    }

    /**
     * Set enabled state for a specified menu item
     * * this includes shading disabled items to visualize state
     *
     * @param id      ID of menu item
     * @param enabled flag if to be visible/invisible
     */
    private void setMenuItemVisible(int id, boolean enabled)
    {
        if (menu != null)
        {
            MenuItem item = menu.findItem(id);
            if (item != null)
            {
                item.setVisible(enabled);
            }
        }
    }

    /**
     * start/stop the autmatic toolbar hider
     */
    private void setAutoHider(boolean active)
    {
        // disable existing hider
        if (toolbarAutoHider != null)
        {
            // cancel auto hider
            toolbarAutoHider.cancel();
            // forget about it
            toolbarAutoHider = null;
        }

        // if new hider shall be activated
        if (active)
        {
            int timeout = getPrefsInt(MainActivity.PREF_AUTOHIDE_DELAY, 15);
            toolbarAutoHider = new AutoHider(this,
                    mHandler,
                    timeout * 1000);
            // start with update resolution of 1 second
            toolbarAutoHider.start(1000);
        }
    }

    /**
     * Get preference int value
     *
     * @param key          preference key name
     * @param defaultValue numeric default value
     * @return preference int value
     */
    @SuppressLint("DefaultLocale")
    private int getPrefsInt(String key, int defaultValue)
    {
        int result = defaultValue;

        try
        {
            result = Integer.valueOf(prefs.getString(key, String.valueOf(defaultValue)));
        } catch (Exception ex)
        {
            // log error message
            log.severe(String.format("Preference '%s'(%d): %s", key, result, ex.toString()));
        }

        return result;
    }

    /**
     * set listeners for data structure changes
     */
    private void setDataListeners()
    {
        // add pv change listeners to trigger model updates
        ObdProt.PidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.VidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.tCodes.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
        mPluginPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
    }

    /**
     * set listeners for data structure changes
     */
    private void removeDataListeners()
    {
        // remove pv change listeners
        ObdProt.PidPvs.removePvChangeListener(this);
        ObdProt.VidPvs.removePvChangeListener(this);
        ObdProt.tCodes.removePvChangeListener(this);
        mPluginPvs.removePvChangeListener(this);
    }

    /**
     * get current operating mode
     */
    private MODE getMode()
    {
        return mode;
    }

    /**
     * set new operating mode
     *
     * @param mode new mode
     */
    private void setMode(MODE mode)
    {
        // if this is a mode change, or file reload ...
        if (mode != this.mode || mode == MODE.FILE)
        {
            if (mode != MODE.DEMO)
            {
                stopDemoService();
            }

            // Disable data updates in FILE mode
            ObdItemAdapter.allowDataUpdates = (mode != MODE.FILE);

            switch (mode)
            {
                case OFFLINE:
                    // update menu item states
                    setMenuItemVisible(R.id.disconnect, false);
                    setMenuItemVisible(R.id.secure_connect_scan, true);
                    setMenuItemEnable(R.id.obd_services, false);
                    break;

                case ONLINE:
                    switch (CommService.medium)
                    {
                        case BLUETOOTH:
                            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
                            {
                                Toast.makeText(this, getString(R.string.none_found), Toast.LENGTH_SHORT).show();
                                mode = MODE.OFFLINE;
                            } else
                            {
                                // if pre-settings shall be used ...
                                String address = prefs.getString(PRESELECT.LAST_DEV_ADDRESS.toString(), null);
                                if (istRestoreWanted(PRESELECT.LAST_DEV_ADDRESS)
                                        && address != null)
                                {
                                    // ... connect with previously connected device
                                    connectBtDevice(address, prefs.getBoolean("bt_secure_connection", false));
                                } else
                                {
                                    // ... otherwise launch the BtDeviceListActivity to see devices and do scan
                                    Intent serverIntent = new Intent(this, BtDeviceListActivity.class);
                                    startActivityForResult(serverIntent,
                                            prefs.getBoolean("bt_secure_connection", false)
                                                    ? REQUEST_CONNECT_DEVICE_SECURE
                                                    : REQUEST_CONNECT_DEVICE_INSECURE);
                                }
                            }
                            break;

                        case USB:
                            Intent enableIntent = new Intent(this, UsbDeviceListActivity.class);
                            startActivityForResult(enableIntent, REQUEST_CONNECT_DEVICE_USB);
                            break;

                        case NETWORK:
                            connectNetworkDevice(prefs.getString(DEVICE_ADDRESS, null),
                                    getPrefsInt(DEVICE_PORT, 23));
                            break;
                    }
                    break;

                case DEMO:
                    startDemoService();
                    break;

                case FILE:
                    setStatus(R.string.saved_data);
                    selectFileToLoad();
                    break;

            }
            // remember previous mode
            // set new mode
            this.mode = mode;
            setStatus(mode.toString());
        }
    }

    /**
     * set mesaurement conversion system to metric/imperial
     *
     * @param cnvId ID for metric/imperial conversion
     */
    private void setConversionSystem(int cnvId)
    {
        log.info("Conversion: " + getResources().getStringArray(R.array.measure_options)[cnvId]);
        if (EcuDataItem.cnvSystem != cnvId)
        {
            // set coversion system
            EcuDataItem.cnvSystem = cnvId;
        }
    }

    /**
     * Set up loggers
     */
    private void setupLoggers()
    {
        // set file handler for log file output
        String logFileName = FileHelper.getPath(this).concat(File.separator).concat("log");
        try
        {
            // ensure log directory is available
            //noinspection ResultOfMethodCallIgnored
            new File(logFileName).mkdirs();
            // Create new log file handler (max. 250 MB, 5 files rotated, non appending)
            logFileHandler = new FileHandler(logFileName.concat("/GOBD.log.%g.txt"),
                    250 * 1024 * 1024,
                    5,
                    false);
            // Set log message formatter
            logFileHandler.setFormatter(new SimpleFormatter()
            {
                final String format = "%1$tF\t%1$tT.%1$tL\t%4$s\t%3$s\t%5$s%n";

                @SuppressLint("DefaultLocale")
                @Override
                public synchronized String format(LogRecord lr)
                {
                    return String.format(format,
                            new Date(lr.getMillis()),
                            lr.getSourceClassName(),
                            lr.getLoggerName(),
                            lr.getLevel().getName(),
                            lr.getMessage()
                    );
                }
            });
            // add file logging ...
            rootLogger.addHandler(logFileHandler);
            // set
            setLogLevels();
        } catch (IOException e)
        {
            // try to log error (at least with system logging)
            log.log(Level.SEVERE, logFileName, e);
        }
    }

    /**
     * Set logging levels from shared preferences
     */
    private void setLogLevels()
    {
        // get level from preferences
        Level level;
        try
        {
            level = Level.parse(prefs.getString(LOG_MASTER, "INFO"));
        } catch (Exception e)
        {
            level = Level.INFO;
        }

        // set logger main level
        MainActivity.rootLogger.setLevel(level);
    }

    /**
     * Load optional extension files which may have
     * been defined in preferences
     */
    private void loadPreferredExtensions()
    {
        String errors = "";

        // custom conversions
        try
        {
            String filePath = prefs.getString(SettingsActivity.extKeys[0], null);
            if (filePath != null)
            {
                log.info("Load ext. conversions: " + filePath);
                Uri uri = Uri.parse(filePath);
                InputStream inStr = getContentResolver().openInputStream(uri);
                EcuDataItems.cnv.loadFromStream(inStr);
            }
        } catch (Exception e)
        {
            log.log(Level.SEVERE, "Load ext. conversions: ", e);
            e.printStackTrace();
            errors += e.getLocalizedMessage() + "\n";
        }

        // custom PIDs
        try
        {
            String filePath = prefs.getString(SettingsActivity.extKeys[1], null);
            if (filePath != null)
            {
                log.info("Load ext. conversions: " + filePath);
                Uri uri = Uri.parse(filePath);
                InputStream inStr = getContentResolver().openInputStream(uri);
                ObdProt.dataItems.loadFromStream(inStr);
            }
        } catch (Exception e)
        {
            log.log(Level.SEVERE, "Load ext. PIDs: ", e);
            e.printStackTrace();
            errors += e.getLocalizedMessage() + "\n";
        }

        if (errors.length() != 0)
        {
            dlgBuilder
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.extension_loading)
                    .setMessage(getString(R.string.check_cust_settings) + errors)
                    .show();
        }
    }

    /**
     * Stop demo mode Thread
     */
    private void stopDemoService()
    {
        if (getMode() == MODE.DEMO)
        {
            ElmProt.runDemo = false;
            Toast.makeText(this, getString(R.string.demo_stopped), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Start demo mode Thread
     */
    private void startDemoService()
    {
        if (getMode() != MODE.DEMO)
        {
            setStatus(getString(R.string.demo));
            Toast.makeText(this, getString(R.string.demo_started), Toast.LENGTH_SHORT).show();

            boolean allowConnect = mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
            setMenuItemVisible(R.id.secure_connect_scan, allowConnect);
            setMenuItemVisible(R.id.disconnect, !allowConnect);

            setMenuItemEnable(R.id.obd_services, true);
            /* The Thread object for processing the demo mode loop */
            Thread demoThread = new Thread(CommService.elm);
            demoThread.start();
        }
    }

    /**
     * set status message in status bar
     *
     * @param resId Resource ID of the text to be displayed
     */
    private void setStatus(int resId)
    {
        setStatus(getString(resId));
    }

    /**
     * set status message in status bar
     *
     * @param subTitle status text to be set
     */
    private void setStatus(CharSequence subTitle)
    {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null)
        {
            actionBar.setSubtitle(subTitle);
            // show action bar to make state change visible
            unHideActionBar();
        }
    }

    /**
     * Select file to be loaded
     */
    private void selectFileToLoad()
    {
        File file = new File(FileHelper.getPath(this));
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName()+".provider", file);
        String type = "*/*";
        intent.setDataAndType(uri, type);
        startActivityForResult(intent, REQUEST_SELECT_FILE);
    }

    /**
     * clear all preselections
     */
    private void clearPreselections()
    {
        for (PRESELECT selection : PRESELECT.values())
        {
            prefs.edit().remove(selection.toString()).apply();
        }
    }

    /**
     * Initiate a connect to the selected bluetooth device
     *
     * @param address bluetooth device address
     * @param secure  flag to indicate if the connection shall be secure, or not
     */
    private void connectBtDevice(String address, boolean secure)
    {
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mCommService = new BtCommService(this, mHandler);
        mCommService.connect(device, secure);
    }

    /**
     * Initiate a connect to the selected network device
     *
     * @param address IP device address
     * @param port    IP port to connect to
     */
    private void connectNetworkDevice(String address, int port)
    {
        // Attempt to connect to the device
        mCommService = new NetworkCommService(this, mHandler);
        ((NetworkCommService) mCommService).connect(address, port);
    }

    /**
     * Activate desired OBD service
     *
     * @param newObdService OBD service ID to be activated
     */
    private void setObdService(int newObdService, CharSequence menuTitle)
    {
        // remember this as current OBD service
        obdService = newObdService;
        ignoreNrcs = false;

        // set list view
        setContentView(mListView);
        getListView().setOnItemLongClickListener(this);
        getListView().setMultiChoiceModeListener(this);
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // set title
        ActionBar ab = getActionBar();
        if (ab != null)
        {
            // title specified ... show it
            if (menuTitle != null)
            {
                ab.setTitle(menuTitle);
            } else
            {
                // no title specified, set to app name if no service set
                if (newObdService == ElmProt.OBD_SVC_NONE)
                {
                    ab.setTitle(getString(R.string.app_name));
                }
            }
        }
        log_out_button = findViewById(R.id.log_out_button);
        // set protocol service
        CommService.elm.setService(newObdService, (getMode() != MODE.FILE && getMode() != MODE.OFFLINE));
        // show / hide freeze frame selector */
        Spinner ff_selector = findViewById(R.id.ff_selector);
        ff_selector.setOnItemSelectedListener(ff_selected);
        ff_selector.setAdapter(mDfcAdapter);
        ff_selector.setVisibility(
                newObdService == ObdProt.OBD_SVC_FREEZEFRAME ? View.VISIBLE : View.GONE);
        // set corresponding list adapter
        switch (newObdService)
        {
            case ObdProt.OBD_SVC_DATA:
                log_out_button.setVisibility(View.INVISIBLE);
                getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                // no break here
            case ObdProt.OBD_SVC_FREEZEFRAME:
                log_out_button.setVisibility(View.INVISIBLE);
                currDataAdapter = mPidAdapter;
                break;

            case ObdProt.OBD_SVC_PENDINGCODES:
            case ObdProt.OBD_SVC_PERMACODES:
            case ObdProt.OBD_SVC_READ_CODES:
                // NOT all DFC modes are supported by all vehicles, disable NRC handling for this request
                log_out_button.setVisibility(View.VISIBLE);
                ignoreNrcs = true;
                currDataAdapter = mDfcAdapter;
//                Toast.makeText(this, getString(R.string.long_press_dfc_hint), Toast.LENGTH_LONG).show();
                break;

            case ObdProt.OBD_SVC_CTRL_MODE:
                log_out_button.setVisibility(View.INVISIBLE);
                currDataAdapter = mTidAdapter;
                break;

            case ObdProt.OBD_SVC_NONE:
                checkLoginStatusAndSetLayout();
                // intentionally no break to initialize adapter
            case ObdProt.OBD_SVC_VEH_INFO:
                log_out_button.setVisibility(View.INVISIBLE);
                currDataAdapter = mVidAdapter;
                break;
        }

        // un-filter display
        setFiltered(false);

        setListAdapter(currDataAdapter);

        // remember this as last selected service
        if (newObdService > ObdProt.OBD_SVC_NONE)
        {
            prefs.edit().putInt(PRESELECT.LAST_SERVICE.toString(), newObdService).apply();
        }
    }


    /**
     * Filter display items to just the selected ones
     */
    private void setFiltered(boolean filtered)
    {
        if (filtered)
        {
            TreeSet<Integer> selPids = new TreeSet<>();
            int[] selectedPositions = getSelectedPositions();
            for (int pos : selectedPositions)
            {
                EcuDataPv pv = (EcuDataPv) currDataAdapter.getItem(pos);
                selPids.add(pv != null ? pv.getAsInt(EcuDataPv.FID_PID) : 0);
            }
            currDataAdapter.filterPositions(selectedPositions);

            if (currDataAdapter == mPidAdapter)
                setFixedPids(selPids);
        } else
        {
            if (currDataAdapter == mPidAdapter)
                ObdProt.resetFixedPid();

            /* Return to original PV list */
            if (currDataAdapter == mPidAdapter)
            {
                currDataAdapter.setPvList(ObdProt.PidPvs);
                // append plugin measurements to data list
                currDataAdapter.addAll(mPluginPvs.values());
            } else if (currDataAdapter == mVidAdapter)
                currDataAdapter.setPvList(ObdProt.VidPvs);
            else if (currDataAdapter == mDfcAdapter)
                currDataAdapter.setPvList(ObdProt.tCodes);
            else if (currDataAdapter == mPluginDataAdapter)
                currDataAdapter.setPvList(mPluginPvs);

        }
    }

    private void checkLoginStatusAndSetLayout() {
        // Get the access token from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String jwtToken = sharedPreferences.getString("accessToken", null);  // Check for the access token

        // Check if the user is logged in based on the token
        if (jwtToken != null) {
            // User is logged in, show the user-specific startup layout
            setContentView(R.layout.startup_layout);  // Set the user-specific startup layout
            relativeLayoutUser = findViewById(R.id.user_details);
            usernameTextView = findViewById(R.id.username);

            relativeLayoutUser.setVisibility(View.VISIBLE);
            try {
                JSONObject jsonObject = new JSONObject(jwtToken);
                String username = jsonObject.getString("username");
                String email = jsonObject.getString("email");

                usernameTextView.setText(username);
//                Toast.makeText(MainActivity.this, "Username: " + username + "\nEmail: " + email, Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error parsing JSON: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            // User is not logged in, show the regular startup layout
            Toast.makeText(MainActivity.this, "Please log in", Toast.LENGTH_SHORT).show();
            setContentView(R.layout.startup_layout);  // Set the regular startup layout

            // Handle splash screen logic only when the startup layout is visible
            goToLoginButton = findViewById(R.id.go_to_login_button);

            // Initially hide the button while the splash screen is visible
            goToLoginButton.setVisibility(View.INVISIBLE);

            // Use a Handler to delay for a few seconds before enabling the button
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // After a brief delay, show the button
                    goToLoginButton.setVisibility(View.VISIBLE);
                }
            }, 2);  //

            // Set up a click listener for the button to go to the login page
            goToLoginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Start the LoginActivity
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();  // Finish MainActivity so it's removed from the stack
                }
            });
        }
    }

    /**
     * get the Position in model of the selected items
     *
     * @return Array of selected item positions
     */
    private int[] getSelectedPositions()
    {
        int[] selectedPositions;
        // SparseBoolArray - what a garbage data type to return ...
        final SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        // get number of items
        int checkedItemsCount = getListView().getCheckedItemCount();
        // dimension array
        selectedPositions = new int[checkedItemsCount];
        if (checkedItemsCount > 0)
        {
            int j = 0;
            // loop through findings
            for (int i = 0; i < checkedItems.size(); i++)
            {
                // Item position in adapter
                if (checkedItems.valueAt(i))
                {
                    selectedPositions[j++] = checkedItems.keyAt(i);
                }
            }
            // trim to really detected value (workaround for invalid length reported)
            selectedPositions = Arrays.copyOf(selectedPositions, j);
        }
        String strPreselect = Arrays.toString(selectedPositions);
        log.fine("Preselection: '" + strPreselect + "'");
        // save this as last seleted positions
        prefs.edit().putString(PRESELECT.LAST_ITEMS.toString(), strPreselect)
                .apply();
        return selectedPositions;
    }

    /**
     * Set selection status on specified list item positions
     *
     * @param positions list of positions to be set
     * @return flag if selections could be applied
     */
    private boolean selectDataItems(int[] positions)
    {
        int count;
        int max;
        boolean positionsValid;

        Arrays.sort(positions);
        max = positions.length > 0 ? positions[positions.length - 1] : 0;
        count = getListAdapter().getCount();
        positionsValid = (max < count);
        // if all positions are valid for current list ...
        if (positionsValid)
        {
            // set list items as selected
            for (int i : positions)
            {
                getListView().setItemChecked(i, true);
            }
        }

        // return validity of positions
        return positionsValid;
    }

    /**
     * Handle bluetooth connection established ...
     */
    @SuppressLint("StringFormatInvalid")
    private void onConnect()
    {
        stopDemoService();

        mode = MODE.ONLINE;
        // handle further initialisations
        setMenuItemVisible(R.id.secure_connect_scan, false);
        setMenuItemVisible(R.id.disconnect, true);

        setMenuItemEnable(R.id.obd_services, true);
        // display connection status
        setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
        // send RESET to Elm adapter
        CommService.elm.reset();
    }

    /**
     * Handle bluetooth connection lost ...
     */
    private void onDisconnect()
    {
        // handle further initialisations
        setMode(MODE.OFFLINE);
    }

    /**
     * Property change listener to ELM-Protocol
     *
     * @param evt the property change event to be handled
     */
    public void propertyChange(PropertyChangeEvent evt)
    {
        /* handle protocol status changes */
        if (ElmProt.PROP_STATUS.equals(evt.getPropertyName()))
        {
            // forward property change to the UI Activity
            Message msg = mHandler.obtainMessage(MESSAGE_OBD_STATE_CHANGED);
            msg.obj = evt;
            mHandler.sendMessage(msg);
        } else
        {
            if (ElmProt.PROP_NUM_CODES.equals(evt.getPropertyName()))
            {
                // forward property change to the UI Activity
                Message msg = mHandler.obtainMessage(MESSAGE_OBD_NUMCODES);
                msg.obj = evt;
                mHandler.sendMessage(msg);
            } else
            {
                if (ElmProt.PROP_ECU_ADDRESS.equals(evt.getPropertyName()))
                {
                    // forward property change to the UI Activity
                    Message msg = mHandler.obtainMessage(MESSAGE_OBD_ECUS);
                    msg.obj = evt;
                    mHandler.sendMessage(msg);
                } else
                {
                    if (ObdProt.PROP_NRC.equals(evt.getPropertyName()))
                    {
                        // forward property change to the UI Activity
                        Message msg = mHandler.obtainMessage(MESSAGE_OBD_NRC);
                        msg.obj = evt;
                        mHandler.sendMessage(msg);
                    }
                }
            }
        }
    }

    /**
     * clear OBD fault codes after a warning
     * confirmation dialog is shown and the operation is confirmed
     */
    private void clearObdFaultCodes()
    {
        dlgBuilder
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.obd_clearcodes)
                .setMessage(R.string.obd_clear_info)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                // set service CLEAR_CODES to clear the codes
                                CommService.elm.setService(ObdProt.OBD_SVC_CLEAR_CODES);
                                // set service READ_CODES to re-read the codes
                                CommService.elm.setService(ObdProt.OBD_SVC_READ_CODES);
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /**
     * confirm OBD test control
     * confirmation dialog is shown and the operation is confirmed
     */
    private void confirmObdTestControl(String testControlName, int service, int tid)
    {
        dlgBuilder
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(testControlName)
                .setMessage(R.string.obd_test_confirm)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                runObdTestControl(testControlName, service, tid);
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /**
     * perform OBD test control
     * confirmation dialog is shown and the operation is confirmed
     */
    private void runObdTestControl(String testControlName, int service, int tid)
    {
        // start desired test TID
        char emptyBuffer[] = {};
        CommService.elm.writeTelegram(emptyBuffer, service, tid);

        // Show test progress message
        dlgBuilder
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(testControlName)
                .setMessage(R.string.obd_test_progress)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                            }
                        })
                .setNegativeButton(null, null)
                .show();
    }

    /**
     * Set new data view mode
     *
     * @param dataViewMode new data view mode
     */
    private void setDataViewMode(DATA_VIEW_MODE dataViewMode)
    {
        // if this is a real change ...
        if (dataViewMode != this.dataViewMode)
        {
            log.info(String.format("Set view mode: %s -> %s", this.dataViewMode, dataViewMode));

            switch (dataViewMode)
            {
                case LIST:
                    setFiltered(false);
                    getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    this.dataViewMode = dataViewMode;
                    break;

                case FILTERED:
                    if (getListView().getCheckedItemCount() > 0)
                    {
                        setFiltered(true);
                        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                        this.dataViewMode = dataViewMode;
                    }
                    break;

                case HEADUP:
                case DASHBOARD:
                    if (getListView().getCheckedItemCount() > 0)
                    {
                        DashBoardActivity.setAdapter(getListAdapter());
                        Intent intent = new Intent(this, DashBoardActivity.class);
                        intent.putExtra(DashBoardActivity.POSITIONS, getSelectedPositions());
                        intent.putExtra(DashBoardActivity.RES_ID,
                                dataViewMode == DATA_VIEW_MODE.DASHBOARD
                                        ? R.layout.dashboard
                                        : R.layout.head_up);
                        startActivityForResult(intent, REQUEST_GRAPH_DISPLAY_DONE);
                        this.dataViewMode = dataViewMode;
                    }
                    break;

                case CHART:
                    if (getListView().getCheckedItemCount() > 0)
                    {
                        ChartActivity.setAdapter(getListAdapter());
                        Intent intent = new Intent(this, ChartActivity.class);
                        intent.putExtra(ChartActivity.POSITIONS, getSelectedPositions());
                        startActivityForResult(intent, REQUEST_GRAPH_DISPLAY_DONE);
                        this.dataViewMode = dataViewMode;
                    }
                    break;
            }

            // remember this as the last data view mode (if not regular list)
            if (dataViewMode != DATA_VIEW_MODE.LIST)
            {
                prefs.edit().putString(PRESELECT.LAST_VIEW_MODE.toString(), dataViewMode.toString())
                        .apply();
            }
        }
    }

    @Override
    public void onDataListUpdate(String csvString)
    {
        log.log(Level.FINE, "PluginDataList: " + csvString);
        // append unknown items to list of known items
        synchronized (mPluginPvs)
        {
            for (String csvLine : csvString.split("\n"))
            {
                String[] fields = csvLine.split(";");
                if (fields.length >= Plugin.CsvField.values().length)
                {
                    // check if PV already is known ...
                    PluginDataPv pv = (PluginDataPv) mPluginPvs.get(fields[Plugin.CsvField.MNEMONIC.ordinal()]);
                    // if not, create a new one
                    if (pv == null)
                    {
                        pv = new PluginDataPv();
                    }
                    // fill field content
                    for (Plugin.CsvField fld : Plugin.CsvField.values())
                    {
                        try
                        {
                            // if content is numeric, set numeric value
                            Double value = Double.valueOf(fields[fld.ordinal()]);
                            pv.put(csvFidMap[fld.ordinal()], value);
                        } catch (Exception ex)
                        {
                            pv.put(csvFidMap[fld.ordinal()], fields[fld.ordinal()]);
                        }
                    }
                    // add/update into pv list
                    mPluginPvs.put(pv.getKeyValue(), pv);
                }
            }
        }
    }

    @Override
    public void onDataUpdate(String key, String value)
    {
        log.log(Level.FINE, "PluginData: " + key + "=" + value);
        // Update value of plugin data item
        synchronized (mPluginPvs)
        {
            ProcessVar pv = (ProcessVar) mPluginPvs.get(key);
            if (pv != null)
            {
                try
                {
                    // if content is numeric, set numeric value
                    Double numVal = Double.valueOf(value);
                    pv.put(EcuDataPv.FIELDS[EcuDataPv.FID_VALUE], numVal);
                } catch (Exception ex)
                {
                    pv.put(EcuDataPv.FIELDS[EcuDataPv.FID_VALUE], value);
                }
            }
        }
    }

    /*
     * Implementations of PluginManager data interface callbacks
     */

    /**
     * operating modes
     */
    public enum MODE
    {
        OFFLINE,//< OFFLINE mode
        ONLINE,    //< ONLINE mode
        DEMO,    //< DEMO mode
        FILE,   //< FILE mode
    }

    /**
     * data view modes
     */
    public enum DATA_VIEW_MODE
    {
        LIST,       //< data list (un-filtered)
        FILTERED,   //< data list (filtered)
        DASHBOARD,  //< dashboard
        HEADUP,     //< Head up display
        CHART,        //< Chart display
    }

    /**
     * Preselection types
     */
    public enum PRESELECT
    {
        LAST_DEV_ADDRESS,
        LAST_ECU_ADDRESS,
        LAST_SERVICE,
        LAST_ITEMS,
        LAST_VIEW_MODE,
    }
}
