package tv.broadpeak.smartlib.demo.android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.broadpeak.smartlib.SmartLib;
import tv.broadpeak.smartlib.session.streaming.StreamingSession;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionResult;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer mPlayer;
    private StyledPlayerView mPlayerView;

    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Create the player
        mPlayer = new ExoPlayer.Builder(this).build();

        // Get the player view
        mPlayerView = findViewById(R.id.playerView);

        // Bind the player to the view.
        mPlayerView.setPlayer(mPlayer);

        // Create SmartLib session
        mSession = SmartLib.getInstance().createStreamingSession();

        // Attach the player on the same thread
        mSession.attachPlayer(mPlayer);

        // Run getURL in a thread
        mExecutor.submit(() -> {
            // Start the session and retrieve the streaming URL
            StreamingSessionResult result = mSession.getURL("https://pf6.broadpeak-vcdn.com/bpk-tv/Arte/default/index.m3u8");

            // ExoPlayer requires main thread
            runOnUiThread(() -> {
                if (!result.isError()) {
                    // Create a data source factory.
                    DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();

                    // Create a HLS media source pointing SmartLib result URL
                    HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(result.getURL()));

                    // Attach media source events to the SmartLib session
                    hlsMediaSource.addEventListener(new Handler(Looper.getMainLooper()), (MediaSourceEventListener) mSession.getListener());

                    // On non-recoverable error, stop the current session
                    mPlayer.addListener(new Player.Listener() {
                        @Override
                        public void onPlayerError(PlaybackException error) {
                            mSession.stopStreamingSession();
                        }
                    });

                    // Set the media item to be played.
                    mPlayer.setMediaSource(hlsMediaSource);

                    // Prepare the player.
                    mPlayer.prepare();

                    // Start the playback.
                    mPlayer.play();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mSession != null) {
            mSession.stopStreamingSession();
            mPlayer.stop();
            mPlayer.release();
        }
    }
}