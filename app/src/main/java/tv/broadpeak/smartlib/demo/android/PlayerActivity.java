package tv.broadpeak.smartlib.demo.android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.broadpeak.smartlib.SmartLib;
import tv.broadpeak.smartlib.ad.AdManager;
import tv.broadpeak.smartlib.engine.manager.LoggerManager;
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

        // Activate advertising
        mSession.activateAdvertising();

        // Ad events
        mSession.setAdEventsListener(new AdManager.AdEventsListener() {
            /**
             * Triggered when ad break begin
             *
             * @param position Ad break begin position in millis
             * @param duration Ad break duration in millis
             */
            @Override
            public void onAdBreakBegin(long position, long duration) {
                // Lock player controls
            }

            /**
             * Triggered when an ad begin
             *
             * @param position Ad begin position in millis
             * @param duration Ad duration in millis
             * @param clickURL Ad click URL, empty string if unset
             */
            @Override
            public void onAdBegin(long position, long duration, String clickURL) {
                // Show ad link button if needed
            }

            /**
             * Triggered when an ad is skippable
             *
             * @param offset position in the complete content where skip become allowed (in milliseconds from complete playlist)
             * @param limit position in milliseconds where seek need to be done
             */
            @Override
            public void onAdSkippable(long offset, long limit) {
                // Show the skip message/button "skip ad in x seconds"
            }

            /**
             * Triggered when the ad is ended, not called if skipped
             */
            @Override
            public void onAdEnd() {
                // Unlock player controls, hide ad link, hide ad skip button
            }

            /**
             * Triggered when ad break ended, even in case of skipping
             */
            @Override
            public void onAdBreakEnd() {
                // Unlock player controls, hide ad link, hide ad skip button
            }
        });

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

                    // Set the media item to be played.
                    mPlayer.setMediaSource(mediaSource);

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