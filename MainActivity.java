package de.thu.musicplayer;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnPreparedListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    String artist = "", title = "", album = "", sUri;
    TextView artistName, songName, albumName, songPosition, songLocation;
    View playPauseButton;
    MediaPlayer mediaPlayer;
    private GoogleApiClient googleApiClient;
    private Geocoder geocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        artistName = findViewById(R.id.artistName);
        songName = findViewById(R.id.songName);
        albumName = findViewById(R.id.albumName);
        songPosition = findViewById(R.id.songPosition);
        songLocation = findViewById(R.id.songLocation);

        // Make this text view multi line
        songLocation.setElegantTextHeight(true);
        songLocation.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        songLocation.setSingleLine(false);

        playPauseButton = findViewById(R.id.playPauseButton);
        playPauseButton.setEnabled(false); // Disable play/pause button until we are ready to use it

        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

        // Retrieve value from preferences
        artistName.setText(prefs.getString("Store Artist", ""));
        songName.setText(prefs.getString("Store Title", ""));
        albumName.setText(prefs.getString("Store Album", ""));
        sUri = prefs.getString("Store Uri", "");

        mediaPlayer = new MediaPlayer();

        if (sUri != null) {
            prepareStream();
        }

        // Build client object with info on listeners and required APIs
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
        }

        geocoder = new Geocoder(this, Locale.getDefault());
    }

    public void selectSongButton (View view) {

        // Access to all content providers via a content resolver that can be queried
        ContentResolver cr = getContentResolver();

        // Query all audios   ----------- Could also be EXTERNAL_CONTENT_URI
        Cursor cursor = cr.query(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, null, null, null, null);

        if (cursor.getCount() > 0) {
            Random random = new Random();

            // Generates a random number between 0 and the audio list size
            int songNmbr = random.nextInt(cursor.getCount()) - 1;

            // Move cursor to the randomly chosen song
            cursor.moveToPosition(songNmbr);

            // Get information from chosen song
            artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
            title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
            album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
            sUri = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));

            // Set text views
            artistName.setText(artist);
            songName.setText(title);
            albumName.setText(album);

            prepareStream();
        }
        cursor.close();
    }

    private void prepareStream() {
        Uri myUri = Uri.parse(sUri);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC); // Hint media type

        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(getApplicationContext(), myUri);
        } catch (IOException e) {
            Log.e("Music Player", "Failed to Open Stream!");
            e.printStackTrace();
            return;
        }

        // onPrepared method will be called when prepared
        mediaPlayer.setOnPreparedListener(this);

        // Prepare and buffer asynchronously
        mediaPlayer.prepareAsync();
    }

    public void playPauseButton (View view) {
        if(!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        } else {
            mediaPlayer.pause();
        }
    }

    // Called when ready --> enable button
    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        playPauseButton.setEnabled(true);
        mediaPlayer.setLooping(true);
    }

    // Saves state/texts/decisions made recently for the next use of the app
    @Override
    protected void onPause() {
        super.onPause();

        // Obtain preferences - Alternative: getSharedPreferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

        // Get editing access
        SharedPreferences.Editor editor = prefs.edit();

        // Store song's information
        String storeArtist = artistName.getText().toString();
        String storeTitle = songName.getText().toString();
        String storeAlbum = albumName.getText().toString();

        // Store key-value-pair
        editor.putString("Store Artist", storeArtist);
        editor.putString("Store Title", storeTitle);
        editor.putString("Store Album", storeAlbum);
        editor.putString("Store Uri", sUri);

        // Persist data in XML file
        editor.apply();
    }

    // Retrieves state/texts/decisions from the last use of the app
    @Override
    protected void onResume() {
        super.onResume();

    }

    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    protected void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    // Called when connected to Play services
    public void onConnected(@Nullable Bundle bundle) throws SecurityException{
        Log.d("WhereAmI", "Connected");

        // Get last location. Can be null, if no last location exists.
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if(lastLocation != null) {
            // Get values from Location object
            songPosition.setText("Position: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
            songLocation.setText(getAddressForLocation(lastLocation));
        } else {
            Log.d("WhereAmI", "No last location");
        }
    }

    // Called when connection to Play services was lost
    @Override
    public void onConnectionSuspended(int i){

    }

    // Called if connection failed
    @Override
    public void onConnectionFailed(@Nullable ConnectionResult connectionResult) {
        Log.d("WhereAmI", "Connection Failed!");
    }

    // Get address for a given location (called when location is changed)
    private String getAddressForLocation(Location location) {
        List<Address> addresses;

        try{
            // Ask geocoder for address information
            addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
        } catch (IOException e){
            e.printStackTrace();
            return "<IO error when connecting to geocoder>";
        }

        if(addresses == null || addresses.size() == 0) {
            return "<No info on location>";
        }

        Address a = addresses.get(0);
        String result = "";

        // Address is usually made up of several components (street, city,...)
        // We collect them and build a string to return
        for(int i = 0; i <= a.getMaxAddressLineIndex(); i++) {
            result += a.getAddressLine(i) + "\n";
        }

        return result;
    }

}

// https://developer.android.com/guide/topics/ui/layout/cardview
// https://developer.android.com/guide/topics/ui/look-and-feel/themes
// https://stackoverflow.com/questions/30733929/how-to-make-a-textview-multiline-programmatically-in-android-java/41948573
// https://stackoverflow.com/questions/6674578/multiline-textview-in-android