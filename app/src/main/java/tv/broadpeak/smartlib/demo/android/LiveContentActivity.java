/*
package tv.broadpeak.smartlib.demo.android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.broadpeak.smartlib.SmartLib;
import tv.broadpeak.smartlib.engine.manager.LoggerManager;
import tv.broadpeak.smartlib.session.streaming.StreamingSession;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionResult;

public class LiveContentActivity extends AppCompatActivity {

    private ExoPlayer mPlayer;
    private PlayerView mPlayerView;

    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_content);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

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
            StreamingSessionResult result = mSession.getURL("https://stream.broadpeak.io/98dce83da57b03956f8ea3c5b949919a/scte35/bpk-tv/jumping/default/index.m3u8");

            // ExoPlayer requires main thread
            runOnUiThread(() -> {
                if (!result.isError()) {
                    // Create a data source factory.
                    DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();

                    // Create a media source pointing SmartLib result URL
                    MediaSource mediaSource = null;
                    if (result.getURL().contains(".mpd")) {
                        mediaSource = new DashMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(result.getURL()));
                    } else if (result.getURL().contains(".m3u8")) {
                        mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(result.getURL()));
                    }

                    // Attach media source events to the SmartLib session
                    mediaSource.addEventListener(new Handler(Looper.getMainLooper()), (MediaSourceEventListener) mSession.getListener());
                    mediaSource.addEventListener(new Handler(), new MediaSourceEventListener() {
                        @Override
                        public void onLoadCanceled(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                            LoggerManager.getInstance().printErrorLogs("ExoPlayer", "onLoadCanceled");
                        }

                        @Override
                        public void onLoadError(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
                            LoggerManager.getInstance().printErrorLogs("ExoPlayer", "onLoadError wasCanceled:" + wasCanceled);
                            LoggerManager.getInstance().printErrorLogs("ExoPlayer", "onLoadError uri:" + loadEventInfo.uri);
                            error.printStackTrace();
                        }
                    });

                    // On non-recoverable error, stop the current session
                    mPlayer.addListener(new Player.Listener() {
                        @Override
                        public void onPlayerError(PlaybackException error) {
                            mSession.stopStreamingSession();
                        }
                    });

                    // Set the media item to be played
                    mPlayer.setMediaSource(mediaSource);

                    // Prepare the player
                    mPlayer.prepare();

                    // Start the playback
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
            mPlayer.stop();
            mPlayer.release();
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
}*/
