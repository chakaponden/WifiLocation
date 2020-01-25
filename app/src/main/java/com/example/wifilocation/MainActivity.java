package com.example.wifilocation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.microg.nlp.api.WiFiBackendHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity
        implements WiFiBackendHelper.Listener {
    String TAG = "WifiLocation";
    private static final long THIRTY_DAYS = 2592000000L;
    private final LocationRetriever retriever = new LocationRetriever();
    private WiFiBackendHelper backendHelper;
    private VerifyingWifiLocationCalculator calculator;
    private WifiLocationDatabase database;
    private Thread thread;
    private Set<String> toRetrieve;
    private Button buttonUpdate;
    private TextView textViewAltitude;
    private TextView textViewLongitude;
    private TextView textViewLatitude;
    private final Runnable retrieveAction = new Runnable() {
        @Override
        public void run() {
            while (toRetrieve != null && !toRetrieve.isEmpty()) {
                    Set<String> now = new HashSet<>();
                    for (String s : toRetrieve) {
                        now.add(s);
                        if (now.size() == 10) break;
                    }
                    Log.d(TAG, "Requesting Apple for " + now.size() + " locations");
                    try {
                        Collection<Location> response = retriever.retrieveLocations(now);
                        WifiLocationDatabase.Editor editor = database.edit();
                        for (Location location : response) {
                            editor.put(location);
                            toRetrieve.remove(location.getExtras().getString(LocationRetriever
                                    .EXTRA_MAC_ADDRESS));
                        }
                        for (String mac : now) {
                            if (toRetrieve.contains(mac)) {
                                Bundle extras = new Bundle();
                                extras.putString(LocationRetriever.EXTRA_MAC_ADDRESS, mac);
                                editor.put(org.microg.nlp.api.LocationHelper.create("unknown",
                                        System.currentTimeMillis(), extras));
                                toRetrieve.remove(mac);
                            }
                        }
                        editor.end();
                        // Forcing update, because new mapping data is available
                        report(calculate(backendHelper.getWiFis()));
                    } catch (Exception e) {
                        Log.w(TAG, e);
                    }
                Thread t = thread;
                if (t == null) break;
                synchronized (t) {
                    try {
                        t.wait(30000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            toRetrieve = null;
            thread = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = getApplicationContext();

        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        assert info != null;
        String[] permissions = info.requestedPermissions;
        ActivityCompat.requestPermissions(MainActivity.this,
                permissions, 1);

        backendHelper = new WiFiBackendHelper(this, this);
        buttonUpdate = (Button) findViewById(R.id.button_update);
        buttonUpdate.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                backendHelper.onUpdate();
            }
        });
        buttonUpdate.setEnabled(false);
        textViewAltitude = (TextView) findViewById(R.id.textView_altitude_value);
        textViewLongitude = (TextView) findViewById(R.id.textView_longitude_value);
        textViewLatitude = (TextView) findViewById(R.id.textView_latitude_value);
    }

    protected synchronized void onOpen() {
        Log.d(TAG, "onOpen");
        database = new WifiLocationDatabase(this);
        calculator = new VerifyingWifiLocationCalculator("apple", database);
        backendHelper.onOpen();
    }

    @Override
    protected synchronized void onDestroy() {
        super.onDestroy();
        onClose();
    }

    protected synchronized void onClose() {
        Log.d(TAG, "onClose");
        backendHelper.onClose();
        calculator = null;
        database.close();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        database = null;
    }

    private synchronized Location calculate(Set<WiFiBackendHelper.WiFi> wiFis) {
        Set<Location> locations = new HashSet<>();
        Set<String> unknown = new HashSet<>();
        for (WiFiBackendHelper.WiFi wifi : wiFis) {
            Location location = database.get(wifi.getBssid());
            if (location != null) {
                if ((location.getTime() + THIRTY_DAYS) < System.currentTimeMillis()) {
                    // Location is old, let's refresh it :)
                    unknown.add(wifi.getBssid());
                }
                location.getExtras().putInt(LocationRetriever.EXTRA_SIGNAL_LEVEL, wifi.getRssi());
                if (location.hasAccuracy() && location.getAccuracy() != -1) {
                    locations.add(location);
                }
            } else {
                unknown.add(wifi.getBssid());
            }
        }
        Log.d(TAG, "Found " + wiFis.size() + " wifis, of whom " + locations.size() + " with " +
                "location and " + unknown.size() + " unknown.");
        if (!unknown.isEmpty()) {
            if (toRetrieve == null) {
                toRetrieve = unknown;
            } else {
                toRetrieve.addAll(unknown);
            }
        }
        if (thread == null) {
            thread = new Thread(retrieveAction);
            thread.start();
        }
        return calculator.calculate(locations);
    }

    @Override
    public void onWiFisChanged(Set<WiFiBackendHelper.WiFi> wiFis) {
        report(calculate(wiFis));
    }

    private void report(final Location loc)
    {
        String location = loc != null ?
                loc.getAltitude() + " x " + loc.getLongitude() + " x " + loc.getLatitude() :
                "unknown";
        Log.w(TAG, "location: " + loc);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewAltitude.setText(loc != null ? String.valueOf(loc.getAltitude()) : "unknown");
                textViewLongitude.setText(loc != null ? String.valueOf(loc.getLongitude()) : "unknown");
                textViewLatitude.setText(loc != null ? String.valueOf(loc.getLatitude()) : "unknown");
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults)
    {
        for(int grantResult : grantResults)
        {
            if(grantResult != PackageManager.PERMISSION_GRANTED)
                return;
        }
        onOpen();
        buttonUpdate.setEnabled(true);
    }
}