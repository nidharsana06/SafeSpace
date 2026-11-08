package com.example.safespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;

public class RecordingsActivity extends Activity {

    private LinearLayout listLayout;
    private TextView statusText;
    private MediaPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        int padding = Math.round(
                20 * getResources().getDisplayMetrics().density
        );

        listLayout.setPadding(padding, padding * 2, padding, padding);

        scrollView.addView(listLayout);
        setContentView(scrollView);

        refreshList();
    }

    private void refreshList() {
        listLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Saved recordings");
        title.setTextSize(25);
        listLayout.addView(title);

        statusText = new TextView(this);
        statusText.setText(
                "Stop recording before playing a file.\n" +
                "Tap a file to play. Long-press it to delete."
        );
        listLayout.addView(statusText);

        Button stopPlayback = new Button(this);
        stopPlayback.setText("Stop playback");
        listLayout.addView(stopPlayback);

        stopPlayback.setOnClickListener(view -> {
            releasePlayer();
            statusText.setText("Playback stopped.");
        });

        Button refresh = new Button(this);
        refresh.setText("Refresh list");
        listLayout.addView(refresh);

        refresh.setOnClickListener(view -> {
            releasePlayer();
            refreshList();
        });

        File directory = new File(getFilesDir(), "recordings");

        File[] files = directory.listFiles(
                (dir, name) -> name.endsWith(".m4a")
        );

        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("No recordings saved yet.");
            listLayout.addView(empty);
            return;
        }

        Arrays.sort(
                files,
                (first, second) ->
                        Long.compare(
                                second.lastModified(),
                                first.lastModified()
                        )
        );

        for (File file : files) {
            Button fileButton = new Button(this);

            fileButton.setText(
                    file.getName()
                            + "\n"
                            + Math.max(1, file.length() / 1024)
                            + " KB"
            );

            listLayout.addView(fileButton);

            fileButton.setOnClickListener(view -> playFile(file));

            fileButton.setOnLongClickListener(view -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete recording?")
                        .setMessage(file.getName())
                        .setPositiveButton("Delete", (dialog, which) -> {
                            releasePlayer();

                            if (!file.delete()) {
                                statusText.setText(
                                        "Could not delete the recording."
                                );
                            } else {
                                refreshList();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                return true;
            });
        }
    }

    private void playFile(File file) {
        releasePlayer();

        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());

            player.setOnCompletionListener(mediaPlayer -> {
                statusText.setText("Playback completed.");
                releasePlayer();
            });

            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                statusText.setText("Unable to play this recording.");
                releasePlayer();
                return true;
            });

            player.prepare();
            player.start();

            statusText.setText("Playing: " + file.getName());

        } catch (Exception exception) {
            releasePlayer();

            statusText.setText(
                    "Playback failed. Stop recording first. "
                            + exception.getMessage()
            );
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
    }
}