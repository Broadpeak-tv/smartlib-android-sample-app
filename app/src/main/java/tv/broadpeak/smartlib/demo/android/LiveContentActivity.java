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
import tv.broadpeak.smartlib.session.streaming.StreamingSession;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionResult;

public class LiveContentActivity extends AppCompatActivity {

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

        // Run getURL in a thread
        mExecutor.submit(() -> {
            // Start the session and retrieve the streaming URL
            StreamingSessionResult result = mSession.getURL("https://pf7.broadpeak-vcdn.com/bpk-tv/tvr/default/index.m3u8");

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

        // Release Bitmovin Player and PlayerView
        /*if (mPlayer != null) {
            mPlayer.pause();
            mPlayer.destroy(); // fully release native resources
        }*/
        if (mPlayerView != null) {
            mPlayerView.onDestroy(); // release view resources
            // mPlayerView.setPlayer(null);
        }

        /*mSession = null;
        mPlayer = null;
        mPlayerView = null;*/

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
