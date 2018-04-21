package edu.uoc.android.contacts;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

import edu.uoc.android.contacts.manager.FirebaseContactManager;
import edu.uoc.android.contacts.model.Contact;

public class ContactMapActivity extends AppCompatActivity implements OnMapReadyCallback,
        OnSuccessListener, GoogleMap.OnCameraMoveListener {

    private final int MY_LOCATION_REQUEST_CODE = 101;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private float mStoredZoomValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_map);

        // Creates map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        // Launch the retrieve map async task. The result of this task will be handled
        // by onMapReady listener.
        mapFragment.getMapAsync(this);

        // Creates an instance of the Fused Location Provider Client
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the last zoom value stored in shared preferences. It uses a default value
        // stored in dimens.xml resources file. This default value will be used if no zoom value
        // defined in shared preferences.
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        TypedValue defaultZoomValue = new TypedValue();
        getResources().getValue(R.dimen.default_map_zoom, defaultZoomValue, true);
        mStoredZoomValue = sharedPref.getFloat(getString(R.string.stored_map_zoom), defaultZoomValue.getFloat());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.setOnCameraMoveListener(this);
        loadAllContacts();

        // If has permissions to get location then the app enables the own location in map
        // and set a listener method to handle the location reception
        if (hasPermissionsToLocation()) {
            mMap.setMyLocationEnabled(true);
            mFusedLocationClient.getLastLocation().addOnSuccessListener(this);
        } else {
            // If the app does no have permissions, then user will be prompted to accept fine and
            // coarse location permissions
            ActivityCompat.requestPermissions(this, new String[]
                            {Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_LOCATION_REQUEST_CODE);

        }
    }

    @Override
    public void onCameraMove() {
        // Each time user moves the map camera, the zoom value will be stored to be used
        // next time the user starts the application.
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat(getString(R.string.stored_map_zoom), mMap.getCameraPosition().zoom);
        editor.commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // Check for result of location permissions prompt. If user grant requested permissions
        // then the app enables the own location in map and set a listener method to handle
        // the location reception.
        if (requestCode == MY_LOCATION_REQUEST_CODE) {
            if (permissions.length == 2 &&
                    (permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION) &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED) ||
                    (permissions[1].equals(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                            grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                mMap.setMyLocationEnabled(true);
                mFusedLocationClient.getLastLocation().addOnSuccessListener(this);
            } else {
                // TODO: Permission was denied. Display an error message.
                showMeesage(getString(R.string.permissions_error));
            }
        }
    }

    @Override
    public void onSuccess(Object object) {
        // This method is called as a result of getLastLocation call. If the received result is
        // a valid location, then moves the map camera to center on own position with the
        // defined zoom value.
        if (object.getClass().equals(Location.class)) {
            Location location = ((Location) object);
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                setCameraToPosition(location);
            }
        }
    }

    /**
     * This method center the map camera in the defined location with the defined zoom value.
     * @param location the location to center the map camera.
     */
    private void setCameraToPosition(Location location) {
        if (location != null) {
            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraUpdate update =
                    CameraUpdateFactory.newLatLngZoom(currentLatLng, mStoredZoomValue);
            mMap.moveCamera(update);
        }
    }

    /**
     * This method retrieves all contacts stored in database and creates a marker in map
     * for each one of them.
     */
    private void loadAllContacts() {
        List<Contact> contactList = FirebaseContactManager.getInstance().getAllContacts();

        for (Contact c : contactList) {
            // The marker will be defined as:
            // position: the contact position
            // title: the contact name
            // icon: ic_person_pin_circle icon defined in drawable resources.
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(c.getAddress().getLatitude(), c.getAddress().getLongitude()))
                    .title(c.getName())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_person_pin_circle)));
        }
    }

    /**
     * This method tells whether the application have permissions to access the device position.
     * @return <code>true</code> if the application have fine location or coarse location
     * permissions, <code>false</code> otherwise.
     */
    private boolean hasPermissionsToLocation() {
        return ((ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) ||
                (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED));
    }

    /**
     * This method shows a toast with the received message
     * @param text message to show in toast.
     */
    private void showMeesage(String text) {
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(getApplicationContext(), text, duration);
        toast.show();
    }

}
