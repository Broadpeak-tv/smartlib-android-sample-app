package tv.broadpeak.smartlib.demo.android;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.PlayerBuilder;
import com.bitmovin.player.api.PlayerConfig;
import com.bitmovin.player.api.source.Source;
import com.bitmovin.player.api.source.SourceBuilder;
import com.bitmovin.player.api.source.SourceConfig;

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

    private Player mPlayer;
    private PlayerView mPlayerView;
    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_ad_tracking_content);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Create the player
        mPlayer = new PlayerBuilder(this)
                .disableAnalytics()
                .setPlayerConfig(new PlayerConfig("<your-licence-key>"))
                .build();

        mPlayerView = findViewById(R.id.playerView);
        mPlayerView.setPlayer(mPlayer);

        // Create SmartLib session
        mSession = SmartLib.getInstance().createStreamingSession();

        // Attach the player
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

            runOnUiThread(() -> {
                if (!result.isError()) {
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
        // Stop SmartLib session and detach player
        if (mSession != null) {
            mSession.stopStreamingSession();
        }

        if (mPlayerView != null) {
            mPlayerView.onDestroy(); // release view resources
        }

        super.onDestroy();
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