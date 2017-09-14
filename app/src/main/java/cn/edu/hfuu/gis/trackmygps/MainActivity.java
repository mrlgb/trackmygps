package cn.edu.hfuu.gis.trackmygps;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import cn.edu.hfuu.gis.trackmygps.android.Log;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";


    private TextView tvUsername;

    private ImageButton btnTracker;
    private ImageView ivGpsFixStatus;
    private TextView tvGpsFixStatus;

    private LinearLayout layoutGPSStatus;
    private LinearLayout layoutGPSDetails;

    private TextView tvGPSCounter;
    private TextView tvGPSLatitude;
    private TextView tvGPSLongitude;
    private TextView tvGPSAltitude;
    private TextView tvGPSBearing;
    private TextView tvGPSSpeed;
    private TextView tvGPSDateTime;
    private TextView tvGPSAccuracy;
    private TextView tvGPSProvider;
    private TextView tvGPSTotalSatellites;

    private TextView tvTimeInterval;
    private TextView tvTimeIntervalTitle;

    private GoogleMap gmap;
    private boolean firstFix = true;
    private Marker marker;

    private int mapLayer;
    private float currentZoom; //tracks the current zoom of the map
    private boolean isZoomBasedOnSpeed;

    private GpsLoggerApplication gpsApp;
    private GpsManager gpsManager;
    private GpsConnectionStatusReceiver mGpsNetworkStatusReceiver;

    private WifiStatusReceiver mWifiStatusReceiver;
    private TimeIntervalChangeReceiver mTimeIntervalReceiver;

    /**
     * When WIFI is disconnected, starts the location service.
     * When WIFI is connected and the service is set to ON, poll every 5 minutes
     */
    public class WifiStatusReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (ApplicationConstants.DEBUG) {
                if (gpsApp.isWiFiConnected())
                    gpsApp.showToast("WIFI in range");
                else
                    gpsApp.showToast("WIFI not in range");
            }

            if (gpsApp.isON()) {
                gpsManager.stopLocationProviders();
                gpsManager.startLocationProviders();
            }
        }
    }

    public class TimeIntervalChangeReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            Log.d("Time Interval Changed", "onReceived");
            String strInterval = intent.getStringExtra("TIME_INTERVAL");
            tvTimeInterval.setText(strInterval);
        }
    }

    public class GpsConnectionStatusReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            Log.d("GpsConnectionStatusReceiver", "onReceived");
            updateGpsFixConnectionStatus();
        }
    }

    private BroadcastReceiver mLocationReceiver = new LocationReceiver() {

        @Override
        protected void onLocationReceived(Context context, Location loc, int ctr) {
            displayGPSDetails(loc, ctr);

            if (gmap != null) {
                LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                float maxZoom = gmap.getMaxZoomLevel();

                if (marker == null) {
                    MarkerOptions markerOptions = new MarkerOptions().position(pos);
                    marker = gmap.addMarker(markerOptions);
                }

                marker.setPosition(pos);

                // If this is the first fix, zoom to the new position
                if (firstFix) {
                    firstFix = false;
                    currentZoom = maxZoom / 2.0f;
                    gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, currentZoom));
                } else {
                    // Dynamic zoom based on speed
                    if (isZoomBasedOnSpeed) {
                        int speed = (int) (loc.getSpeed() * GpsManager.KPH);

                        boolean isMoving = speed > 1;
                        boolean isSpeedSlow = isBetween(speed, 10, 40);
                        boolean isSpeedModerate = isBetween(speed, 41, 60);
                        boolean isSpeedQuiteFast = isBetween(speed, 61, 80);
                        boolean isSpeedFast = speed > 81;

                        if (isSpeedSlow) {
                            currentZoom = maxZoom - 3.0f; //zoom = 18
                        } else if (isSpeedModerate) {
                            currentZoom = maxZoom - 4.0f; //zoom = 17
                        } else if (isSpeedQuiteFast) {
                            currentZoom = maxZoom - 5.0f; //zoom = 16
                        } else if (isSpeedFast) {
                            currentZoom = maxZoom - 6.0f; //zoom = 15
                        } else { //crawling
                            currentZoom = maxZoom - 2.0f; //zoom = 19
                        }

                        if (isMoving) {
                            gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, currentZoom));
                        } else {
                            if (MapUtils.isLatLngNotVisible(pos, gmap)) {
                                gmap.moveCamera(CameraUpdateFactory.newLatLng(pos));
                                return;
                            }
                        }
                    }

                    if (MapUtils.isLatLngNotVisible(pos, gmap)) {
                        gmap.moveCamera(CameraUpdateFactory.newLatLng(pos));
                        return;
                    }

                }
            }
        }

        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            this.gpsApp = (GpsLoggerApplication) getApplication();
            this.gpsManager = gpsApp.getGpsManager();
            if (!gpsManager.isLocationAccessEnabled()) {
                displayLocationAccessDialog();
            }

            tvUsername = (TextView) findViewById(R.id.tv_username);
            tvTimeInterval = (TextView) findViewById(R.id.tv_time_interval);
            tvTimeIntervalTitle = (TextView) findViewById(R.id.tv_time_interval_title);

            btnTracker = (ImageButton) findViewById(R.id.btn_tracker);
            ivGpsFixStatus = (ImageView) findViewById(R.id.iv_gps_status);
            tvGpsFixStatus = (TextView) findViewById(R.id.tv_gps_fix_status);

            //find the view layouts
            layoutGPSStatus = (LinearLayout) findViewById(R.id.layout_gps_status);
            layoutGPSDetails = (LinearLayout) findViewById(R.id.layout_gps_details);

            //find the textviews
            tvGPSCounter = (TextView) findViewById(R.id.tv_gps_counter);
            tvGPSLatitude = (TextView) findViewById(R.id.tv_gps_latitude);
            tvGPSLongitude = (TextView) findViewById(R.id.tv_gps_longitude);
            tvGPSAltitude = (TextView) findViewById(R.id.tv_gps_altitude);
            tvGPSBearing = (TextView) findViewById(R.id.tv_gps_bearing);
            tvGPSSpeed = (TextView) findViewById(R.id.tv_gps_speed);
            tvGPSDateTime = (TextView) findViewById(R.id.tv_gps_timestamp);
            tvGPSAccuracy = (TextView) findViewById(R.id.tv_gps_accuracy);
            tvGPSProvider = (TextView) findViewById(R.id.tv_gps_provider);
            tvGPSTotalSatellites = (TextView) findViewById(R.id.tv_gps_fix_total_satellites);

            ActionBar actionBar = getActionBar();
            actionBar.show();

//        gmap = ((MapFragment) getFragmentManager().findFragmentById(R.id.mapview)).getMap();

            mGpsNetworkStatusReceiver = new GpsConnectionStatusReceiver();

            showGPSStatus(false);

            checkUserInPreferences();
            updateButtonTrackerStatus();

            this.mWifiStatusReceiver = new WifiStatusReceiver();
            final IntentFilter wifiFilters = new IntentFilter();
            wifiFilters.addAction("android.net.wifi.STATE_CHANGE");
            this.registerReceiver(mWifiStatusReceiver, wifiFilters);

            mTimeIntervalReceiver = new TimeIntervalChangeReceiver();
            displayTimeInterval(false);

            Context context = getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            getDefaultMapSettings(prefs);
        }

        private void checkUserInPreferences() {
            if (gpsApp.isLoggedIn()) {
                tvUsername.setText(gpsApp.getUsername());
                btnTracker.setEnabled(true);
            } else {
                tvUsername.setText("User not logged in.");
                btnTracker.setEnabled(false);
            }
        }

        private void getDefaultMapSettings(SharedPreferences prefs) {
            // we add 1 since GoogleMap.MAP_TYPE_NORMAL starts at 1
            mapLayer = prefs.getInt(SettingsActivity.PREF_MAP_LAYER_INDEX, 0) + 1;
            isZoomBasedOnSpeed = prefs.getBoolean(SettingsActivity.PREF_ZOOM_BASED_ON_SPEED, true);

//        if(gmap != null){
//            gmap.setMapType(mapLayer);
//        }
        }

        private void updateFromSettingsPreferences() {
            Context context = getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            getDefaultMapSettings(prefs);

            int minTimeInSeconds = prefs.getInt(SettingsActivity.PREF_TIME_INTERVAL_IN_SECONDS,
                    SettingsActivity.DEFAULT_TIME_INTERVAL_IN_SECONDS);
            gpsManager.updateFromSettings(minTimeInSeconds);
        }

        public void updateButtonTrackerStatus() {
            if (gpsApp.isON())
                btnTracker.setImageResource(R.drawable.track_on);
            else
                btnTracker.setImageResource(R.drawable.track_off);
        }

        public void buttonTrackClicked(View view) {
            if (gpsApp.isON()) {
                Log.i(TAG, "buttonStopPressed");
                showGPSStatus(false);
                displayTimeInterval(false);

                gpsApp.showToast("Tracker Turned OFF");
                btnTracker.setImageResource(R.drawable.track_off);
                stopService(new Intent(this, GpsLoggerService.class));
            } else {
                Log.i(TAG, "buttonStartPressed");
                showGPSStatus(true);
                displayTimeInterval(true);

                gpsApp.showToast("Tracker Turned ON");
                btnTracker.setImageResource(R.drawable.track_on);
                startService(new Intent(this, GpsLoggerService.class));
            }
        }

        private void displayTimeInterval(boolean f) {
            if (f) {
                tvTimeIntervalTitle.setVisibility(View.VISIBLE);
                tvTimeInterval.setVisibility(View.VISIBLE);
            } else {
                tvTimeIntervalTitle.setVisibility(View.INVISIBLE);
                tvTimeInterval.setVisibility(View.INVISIBLE);
            }
        }

        private void showGPSStatus(boolean f) {
            if (f) {
                layoutGPSDetails.setVisibility(View.VISIBLE);
                ivGpsFixStatus.setVisibility(View.VISIBLE);
                layoutGPSStatus.setVisibility(View.VISIBLE);
            } else {
                layoutGPSDetails.setVisibility(View.INVISIBLE);
                ivGpsFixStatus.setVisibility(View.INVISIBLE);
                layoutGPSStatus.setVisibility(View.INVISIBLE);
            }
        }

        private void updateGpsFixConnectionStatus() {
            GpsFix status = gpsManager.connectionStatus();
            ivGpsFixStatus.setImageResource(status.icon());
            tvGpsFixStatus.setText(status.toString());
        }

        @Override
        public void onStart() {
            super.onStart();
            this.registerReceiver(mLocationReceiver, new IntentFilter(IntentCodes.ACTION_LOCATION));
            this.registerReceiver(mGpsNetworkStatusReceiver, new IntentFilter(IntentCodes.ACTION_GPS_NETWORK_STATUS));
            this.registerReceiver(mTimeIntervalReceiver, new IntentFilter(IntentCodes.ACTION_TIME_INTERVAL_CHANGE));
        }

        @Override
        public void onStop() {
            this.unregisterReceiver(mLocationReceiver);
            this.unregisterReceiver(mGpsNetworkStatusReceiver);
            this.unregisterReceiver(mTimeIntervalReceiver);

            super.onStop();
        }

        private void displayGPSDetails(Location location, int ctr) {
            if (location != null) {
                tvGPSCounter.setText(Integer.toString(ctr));
                tvGPSLatitude.setText(Double.toString(location.getLatitude()));
                tvGPSLongitude.setText(Double.toString(location.getLongitude()));
                tvGPSAltitude.setText(Double.toString(location.getAltitude()));
                tvGPSBearing.setText(Float.toString(location.getBearing()));
                tvGPSSpeed.setText(Float.toString(location.getSpeed() * GpsManager.KPH));
                tvGPSAccuracy.setText(Float.toString(location.getAccuracy()));
                tvGPSProvider.setText(location.getProvider());

                String gpsDateTime = CustomDateUtils.formatDateTimestamp(location.getTime());
                tvGPSDateTime.setText(gpsDateTime);

                // location.getExtras() is provider specific
                Bundle locBundle = location.getExtras();
                int satellitesWithFix = 0;
                if (locBundle != null && locBundle.containsKey("satellites")) {
                    satellitesWithFix = location.getExtras().getInt("satellites");
                }

                int satellitesTotal = gpsManager.getTotalSatellites();
                String s = Integer.toString(satellitesWithFix) + "/" + satellitesTotal;
                tvGPSTotalSatellites.setText(s);
            }
        }

        public void onPause() {
            super.onPause(); // Always call the superclass method first
            Log.d(TAG, "paused");
        }

        public void onResume() {
            super.onResume();  // Always call the superclass method first

            Log.d(TAG, "resume");
            updateButtonTrackerStatus();

            // Update the counter and location details from last currentLocation when
            // the app resumes from background
            if (gpsApp.isON()) {
                showGPSStatus(true);
                displayGPSDetails(gpsManager.getCurrentLocation(), gpsManager.getCounter());
                displayTimeInterval(true);
                gpsManager.displayCurrentTimeInterval();
            } else {
                displayTimeInterval(false);
            }

        }

        public void onDestroy() {
            try {
                if (mWifiStatusReceiver != null) {
                    this.unregisterReceiver(mWifiStatusReceiver);
                }
            } catch (IllegalStateException ex) {
                Log.d(TAG, ex.toString());
            }

            super.onDestroy();
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            // Inflate the menu items for use in the action bar
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_activity_actions, menu);
            return super.onCreateOptionsMenu(menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            // Handle presses on the action bar items
            switch (item.getItemId()) {
                case R.id.action_share:
                    buttonSharePressed();
                    return true;
                case R.id.action_configure:
                    buttonSettingsPressed();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        private void displayLocationAccessDialog() {
            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
            dialog.setMessage(R.string.gps_network_not_enabled);
            dialog.setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            dialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                }
            });
            dialog.show();
        }

        private boolean isBetween(int x, int lower, int upper) {
            return lower <= x && x <= upper;
        }

        @Override
        public void onBackPressed() {
            //Ensures that we don't go back to previous activity
        }

        private void buttonSettingsPressed() {
            Log.d(TAG, "buttonSettingsPressed");
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, IntentCodes.SETTINGS);
        }

        private void buttonSharePressed() {
            Log.d(TAG, "buttonSharePressed");
            Intent intent = new Intent(this, ShareActivity.class);
            startActivity(intent);
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == IntentCodes.SETTINGS && resultCode == Activity.RESULT_OK) {
                updateFromSettingsPreferences();
            }
        }
    }
