package tv.broadpeak.smartlib.demo.android;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Timeline;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.PlayerBuilder;
import com.bitmovin.player.api.PlayerConfig;
import com.bitmovin.player.api.source.Source;
import com.bitmovin.player.api.source.SourceBuilder;
import com.bitmovin.player.api.source.SourceConfig;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.broadpeak.smartlib.SmartLib;
import tv.broadpeak.smartlib.ad.AdBreakData;
import tv.broadpeak.smartlib.ad.AdData;
import tv.broadpeak.smartlib.ad.AdManager;
import tv.broadpeak.smartlib.engine.manager.LoggerManager;
import tv.broadpeak.smartlib.session.streaming.StreamingSession;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionResult;

public class VodAdTrackingContentActivity extends AppCompatActivity {

    private final String TAG = VodAdTrackingContentActivity.class.getSimpleName();

    private Player mPlayer;

    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_ad_tracking_content);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Create the player
        mPlayer = new PlayerBuilder(this)
                // .configureAnalytics(analyticsConfig)
                .setPlayerConfig(new PlayerConfig("<your-licence-key>"))
                .build();

        // Get the player view
        PlayerView mPlayerView = findViewById(R.id.playerView);
        mPlayerView.setPlayer(mPlayer);
        // mPlayerView.setControllerAutoShow(false);

        // Create SmartLib session
        mSession = SmartLib.getInstance().createStreamingSession();

        // Attach the player on the same thread
        mSession.attachPlayer(mPlayer);

        // Activate advertising
        mSession.activateAdvertising();

        // Listen to ad events
        mSession.setAdEventsListener(new AdManager.AdEventsListener() {
            @Override
            public void onAdBreakBegin(AdBreakData adBreakData) {
                LoggerManager.getInstance().printErrorLogs("BPKAD", "onAdBreakBegin");
            }

            @Override
            public void onAdBegin(AdData adData, AdBreakData adBreakData) {
                LoggerManager.getInstance().printErrorLogs("BPKAD", "onAdBegin");
            }

            @Override
            public void onAdSkippable(AdData adData, AdBreakData adBreakData, long adSkipBegin, long adEnd, long adBreakEnd) {

            }

            @Override
            public void onAdEnd(AdData adData, AdBreakData adBreakData) {
                LoggerManager.getInstance().printErrorLogs("BPKAD", "onAdEnd");
            }

            @Override
            public void onAdBreakEnd(AdBreakData adBreakData) {
                LoggerManager.getInstance().printErrorLogs("BPKAD", "onAdBreakEnd");
            }
        });

        // Run getURL in a thread
        mExecutor.submit(() -> {
            // Start the session and retrieve the streaming URL
            StreamingSessionResult result = mSession.getURL("https://d3m98thyxwxtvo.cloudfront.net/9bf31c7ff062936a71193233516a9969/bpk-vod/voddemo/default/unity/adam/index.mpd?category=adult");

            // ExoPlayer requires main thread
            runOnUiThread(() -> {
                if (!result.isError()) {
                    // Create a data source factory.
                    SourceConfig sourceConfig = SourceConfig.fromUrl(result.getURL());
                    Source source = new SourceBuilder(sourceConfig).build();
                    mPlayer.load(source);
                    mPlayer.play();
                } else {
                    // Stop the session if error
                    mSession.stopStreamingSession();
                }
            });
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();


        // Stop the session when closing the UI
        if (mSession != null) {
            mSession.stopStreamingSession();
            mPlayer.pause();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
