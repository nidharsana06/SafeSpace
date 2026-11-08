package com.example.safespace;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import java.io.File;

public class BackgroundAudioRecordService extends Service {

    private static final String CHANNEL_ID = "safespace_recording_v2";
    private static final int NOTIFICATION_ID = 300;
    private static final String ACTION_STOP =
            "com.example.safespace.STOP_RECORDING";

    private MediaRecorder recorder;
    private File outputFile;
    private boolean recording = false;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SafeSpace audio recording",
                NotificationManager.IMPORTANCE_LOW
        );

        channel.setDescription("Visible notification while recording audio");
        channel.setSound(null, null);
        channel.enableVibration(false);

        NotificationManager manager = getSystemService(
                NotificationManager.class
        );

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null
                && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (recording) {
            return START_NOT_STICKY;
        }

        try {
            showForegroundNotification();
            beginRecording();
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Recording failed: " + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void showForegroundNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(
                this,
                BackgroundAudioRecordService.class
        );

        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Action stopAction =
                new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "Stop and save",
                        stopPendingIntent
                ).build();

        Notification notification =
                new Notification.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setContentTitle("SafeSpace is recording")
                        .setContentText("Tap Stop and save to finish")
                        .setContentIntent(openPendingIntent)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .addAction(stopAction)
                        .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void beginRecording() throws Exception {
        File directory = new File(getFilesDir(), "recordings");

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create recordings directory."
            );
        }

        outputFile = new File(
                directory,
                "SafeSpace_" + System.currentTimeMillis() + ".m4a"
        );

        if (Build.VERSION.SDK_INT >= 31) {
            recorder = new MediaRecorder(this);
        } else {
            recorder = new MediaRecorder();
        }

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(outputFile.getAbsolutePath());

        recorder.prepare();
        recorder.start();

        recording = true;

        Toast.makeText(
                this,
                "Audio recording started",
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    public void onDestroy() {
        boolean saved = false;

        if (recorder != null) {
            if (recording) {
                try {
                    recorder.stop();
                    saved = true;
                } catch (RuntimeException exception) {
                    // Very short or failed recordings may be invalid.
                    saved = false;
                }
            }

            try {
                recorder.release();
            } catch (Exception ignored) {
            }

            recorder = null;
        }

        recording = false;

        if (!saved && outputFile != null) {
            outputFile.delete();
        }

        if (saved && outputFile != null) {
            Toast.makeText(
                    this,
                    "Saved: " + outputFile.getName(),
                    Toast.LENGTH_LONG
            ).show();
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}