package me.gypsy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import me.gypsy.service.Constants;
import me.gypsy.service.FetchAddressIntentService;

public class MainActivity extends AppCompatActivity {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private FusedLocationProviderClient fusedLocationClient;

    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    private final int REQUEST_CHECK_SETTINGS = 1;

    private Location lastLocation;

    private LocationRequest locationRequest;

    private LocationCallback locationCallback;

    private boolean isReceivingUpdates;

    private boolean hasSystemRequestedLocationToUser;

    private SettingsClient mSettingsClient;

    private LocationSettingsRequest mLocationSettingsRequest;

    private AddressResultReceiver mResultReceiver;

    private String mAddressOutput;

    private boolean hasPermission = false;

    private TextView currentLocationTextView;

    @Override
    public void onStart() {
        super.onStart();

        if (!checkPermissions()) {
            requestPermissions();
        } else {
            hasPermission = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hasSystemRequestedLocationToUser = false;

        locationRequest = createLocationRequest();

        buildLocationSettingsRequest();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                lastLocation = locationResult.getLastLocation();

                startIntentService();

                if (lastLocation != null) {
                    Log.i(LOG_TAG, String.format("Lo: %f, La: %f", lastLocation.getLongitude(), lastLocation.getLatitude()));
                } else {
                    Log.i(LOG_TAG, "Nope.");
                }
            };
        };

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);
        mResultReceiver = new AddressResultReceiver(new Handler());

        currentLocationTextView = (TextView) findViewById(R.id.currentLocation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermission && !isReceivingUpdates && !hasSystemRequestedLocationToUser) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isReceivingUpdates) {
            hasSystemRequestedLocationToUser = false;
            stopLocationUpdates();
        }
    }

    private void stopLocationUpdates() {
        isReceivingUpdates = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        isReceivingUpdates = true;

        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                @Override
                public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                    Log.i(LOG_TAG, "All location settings are on.");
                    hasSystemRequestedLocationToUser = true;

                    fusedLocationClient.requestLocationUpdates(locationRequest,
                            locationCallback, null /* Looper */);
                }
            })
            .addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    int statusCode = ((ApiException) e).getStatusCode();

                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            Log.i(LOG_TAG, "Attempt to upgrade location settings.");

                            try {
                                ResolvableApiException rae = (ResolvableApiException) e;
                                rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException sie) {
                                Log.i(LOG_TAG, "Unable to execute request to upgrade location settings.");
                            }
                            break;

                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String errorMessage = "Location settings are inadequate and cannot be fixed here. Fix it in Settings.";

                            Log.e(LOG_TAG, errorMessage);
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            isReceivingUpdates = false;
                    }
                }
            });
    }

    public void sendMessage(View view) {
        final Activity a = this;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                hasSystemRequestedLocationToUser = true;

                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(LOG_TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(LOG_TAG, "User chose not to make required location settings changes.");
                        isReceivingUpdates = false;
                        break;
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (!hasPermission) {
                        hasPermission = true;
                        startLocationUpdates();
                    }

                } else {
                    Log.i(LOG_TAG, "ERROU!");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            // TODO: Implement UI to ask
            startLocationPermissionRequest();
        } else {
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            startLocationPermissionRequest();
        }
    }

    private void startLocationPermissionRequest() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_PERMISSIONS_REQUEST_CODE);
    }

    @SuppressWarnings("MissingPermission")
    private void getLastLocation(OnSuccessListener<Location> callback) {
        if (callback == null) {
            callback = new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location == null) return;

                    lastLocation = location;
                }
            };
        }

        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, callback);
    }

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        return mLocationRequest;
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        mLocationSettingsRequest = builder.build();
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, lastLocation);
        startService(intent);
    }

    private void displayAddressOutput() {
        currentLocationTextView.setText(mAddressOutput);
    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string
            // or an error message sent from the intent service.
            String result = resultData.getString(Constants.RESULT_DATA_KEY);

            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT) {
                mAddressOutput = String.format(getString(R.string.current_location), result);
                Toast.makeText(MainActivity.this, getString(R.string.address_found), Toast.LENGTH_SHORT).show();
            } else {
                mAddressOutput = result;
            }

            displayAddressOutput();
        }
    }
}
