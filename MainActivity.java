package com.example.safespace;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST = 101;
    private static final int VOICE_REQUEST = 102;

    private LinearLayout mainLayout;
    private LinearLayout contactsLayout;
    private EditText contactInput;
    private TextView statusText;

    private final ArrayList<String> contacts = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Runnable locationTimeout;

    private boolean sosPending = false;
    private ArrayList<String> pendingRecipients;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        contacts.addAll(
                getSharedPreferences("SafeSpacePrefs", MODE_PRIVATE)
                        .getStringSet("contacts", new HashSet<>())
        );

        createInterface();
        showContacts();
    }

    private void createInterface() {
        ScrollView scrollView = new ScrollView(this);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        mainLayout.setPadding(padding, dp(40), padding, padding);

        scrollView.setFillViewport(true);
        scrollView.addView(mainLayout);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("SafeSpace");
        title.setTextSize(30);
        mainLayout.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "Emergency alerts, voice activation and audio recording.\n" +
                "Use consenting contacts when testing."
        );
        mainLayout.addView(description);

        statusText = new TextView(this);
        statusText.setPadding(0, dp(12), 0, dp(12));
        statusText.setText("Ready");
        mainLayout.addView(statusText);

        addButton("Grant / check permissions", this::requestAppPermissions);

        addButton("PANIC — send SOS and record", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Send emergency alert?")
                        .setMessage(
                                "This sends real SMS messages to your saved " +
                                "contacts. Carrier charges may apply."
                        )
                        .setPositiveButton("Send SOS", (dialog, which) ->
                                triggerPanic())
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        addButton("Voice panic — tap and speak", this::startVoiceRecognition);

        addButton("Start audio recording", this::startRecording);

        addButton("Stop audio recording", () -> {
            boolean stopped = stopService(
                    new Intent(this, BackgroundAudioRecordService.class)
            );

            statusText.setText(
                    stopped
                            ? "Stop requested. Open Saved Recordings after saving."
                            : "No recording service was running."
            );
        });

        addButton("Saved recordings", () ->
                startActivity(new Intent(this, RecordingsActivity.class))
        );

        TextView contactsTitle = new TextView(this);
        contactsTitle.setText("\nEmergency contacts");
        contactsTitle.setTextSize(20);
        mainLayout.addView(contactsTitle);

        contactInput = new EditText(this);
        contactInput.setHint("Phone number with country code");
        contactInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        mainLayout.addView(contactInput);

        addButton("Save contact", this::saveNewContact);

        contactsLayout = new LinearLayout(this);
        contactsLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(contactsLayout);

        addButton("Emergency helpline — dial 112 (India)", () -> {
            Intent intent = new Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:112")
            );

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException exception) {
                toast("No dialer application available.");
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addButton(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);

        mainLayout.addView(
                button,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        button.setOnClickListener(view -> action.run());
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAppPermissions() {
        ArrayList<String> permissions = new ArrayList<>(Arrays.asList(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        ));

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        requestPermissions(
                permissions.toArray(new String[0]),
                PERMISSION_REQUEST
        );
    }

    private void saveNewContact() {
        String number = contactInput.getText()
                .toString()
                .trim()
                .replaceAll("[\\s()\\-]", "");

        if (!number.matches("\\+?[0-9]{7,15}")) {
            toast("Enter a valid phone number.");
            return;
        }

        if (contacts.contains(number)) {
            toast("This contact is already saved.");
            return;
        }

        contacts.add(number);
        persistContacts();
        showContacts();
        contactInput.setText("");

        toast("Contact saved.");
    }

    private void persistContacts() {
        getSharedPreferences("SafeSpacePrefs", MODE_PRIVATE)
                .edit()
                .putStringSet("contacts", new HashSet<>(contacts))
                .apply();
    }

    private void showContacts() {
        contactsLayout.removeAllViews();

        if (contacts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No emergency contacts saved.");
            contactsLayout.addView(empty);
            return;
        }

        for (String number : new ArrayList<>(contacts)) {
            Button contactButton = new Button(this);
            contactButton.setText(number + " — tap to remove");
            contactsLayout.addView(contactButton);

            contactButton.setOnClickListener(view ->
                    new AlertDialog.Builder(this)
                            .setTitle("Remove contact?")
                            .setMessage(number)
                            .setPositiveButton("Remove", (dialog, which) -> {
                                contacts.remove(number);
                                persistContacts();
                                showContacts();
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        }
    }

    private void startRecording() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST
            );

            toast("Grant microphone permission, then tap Record again.");
            return;
        }

        try {
            Intent intent = new Intent(
                    this,
                    BackgroundAudioRecordService.class
            );

            startForegroundService(intent);
            statusText.setText(
                    "Recording requested. Check the recording notification."
            );
        } catch (Exception exception) {
            statusText.setText(
                    "Recording could not start: " + exception.getMessage()
            );
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Say help, panic, or emergency"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-US"
        );

        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException exception) {
            toast("No compatible speech recognition application installed.");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != VOICE_REQUEST
                || resultCode != RESULT_OK
                || data == null) {
            return;
        }

        ArrayList<String> results = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
        );

        if (results == null || results.isEmpty()) {
            toast("No speech detected.");
            return;
        }

        String spokenText = results.get(0).toLowerCase(Locale.ROOT);

        statusText.setText("Recognized: " + spokenText);

        if (spokenText.matches(".*\\b(help|panic|emergency)\\b.*")) {
            triggerPanic();
        } else {
            toast("No emergency keyword detected.");
        }
    }

    private void triggerPanic() {
        if (sosPending) {
            toast("An SOS is already being prepared.");
            return;
        }

        if (contacts.isEmpty()) {
            toast("Save at least one emergency contact first.");
            return;
        }

        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            requestPermissions(
                    new String[]{Manifest.permission.SEND_SMS},
                    PERMISSION_REQUEST
            );

            toast("Grant SMS permission, then trigger SOS again.");
            return;
        }

        // Recording failure does not prevent SMS preparation.
        startRecording();

        sosPending = true;
        pendingRecipients = new ArrayList<>(contacts);

        statusText.setText(
                "Preparing SOS. Waiting up to 10 seconds for location."
        );

        boolean locationAllowed =
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        || hasPermission(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        );

        if (!locationAllowed) {
            finishSOS(null);
            return;
        }

        locationManager = (LocationManager) getSystemService(
                LOCATION_SERVICE
        );

        if (locationManager == null) {
            finishSOS(null);
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                finishSOS(location);
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onStatusChanged(
                    String provider,
                    int status,
                    Bundle extras
            ) {
            }
        };

        locationTimeout = () -> finishSOS(null);
        handler.postDelayed(locationTimeout, 10_000);

        boolean requested = false;

        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requested |= requestProvider(LocationManager.GPS_PROVIDER);
        }

        requested |= requestProvider(LocationManager.NETWORK_PROVIDER);

        if (!requested) {
            finishSOS(null);
        }
    }

    private boolean requestProvider(String provider) {
        try {
            if (!locationManager.isProviderEnabled(provider)) {
                return false;
            }

            locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
            );

            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void clearLocationRequest() {
        if (locationTimeout != null) {
            handler.removeCallbacks(locationTimeout);
            locationTimeout = null;
        }

        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
        }

        locationListener = null;
    }

    private void finishSOS(Location location) {
        if (!sosPending) {
            return;
        }

        sosPending = false;
        clearLocationRequest();

        String message =
                "SOS! I need help. Please contact me immediately.";

        if (location != null) {
            message += "\nLocation: https://maps.google.com/?q="
                    + location.getLatitude()
                    + ","
                    + location.getLongitude();
        } else {
            message += "\nLocation unavailable.";
        }

        int submitted = 0;

        for (String number : pendingRecipients) {
            try {
                SmsManager smsManager = SmsManager.getDefault();

                ArrayList<String> parts = smsManager.divideMessage(message);

                smsManager.sendMultipartTextMessage(
                        number,
                        null,
                        parts,
                        null,
                        null
                );

                submitted++;
            } catch (Exception exception) {
                toast(
                        "SMS request failed for "
                                + number
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        pendingRecipients = null;

        statusText.setText(
                "SMS requests submitted for "
                        + submitted
                        + " contacts. Delivery is not confirmed."
        );
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST) {
            statusText.setText(
                    "Permissions updated. Tap the required action again. " +
                    "If permission was permanently denied, enable it in Settings."
            );
        }
    }

    @Override
    protected void onDestroy() {
        // Location preparation is tied to this Activity.
        sosPending = false;
        clearLocationRequest();
        super.onDestroy();
    }
}