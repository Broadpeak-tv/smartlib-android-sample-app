package tv.broadpeak.smartlib.demo.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

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

public class VodAdTrackingContentActivity extends AppCompatActivity {

    private ExoPlayer mPlayer;
    private StyledPlayerView mPlayerView;

    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Button mAdClickButton;
    private Button mAdSkipButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_ad_tracking_content);

        mAdClickButton = findViewById(R.id.adClickButton);
        mAdSkipButton = findViewById(R.id.adSkipButton);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Create the player
        mPlayer = new ExoPlayer.Builder(this).build();

        // Get the player view
        mPlayerView = findViewById(R.id.playerView);
        // mPlayerView.setControllerAutoShow(false);

        // Bind the player to the view.
        mPlayerView.setPlayer(mPlayer);

        // Create SmartLib session
        mSession = SmartLib.getInstance().createStreamingSession();

        // Attach the player on the same thread
        mSession.attachPlayer(mPlayer);

        // Activate advertising
        mSession.activateAdvertising();

        // Listen to ad events
        mSession.setAdEventsListener(new AdManager.AdEventsListener() {
            @Override
            public void onAdBreakBegin(long position, long duration) {
                runOnUiThread(() -> {
                    // Lock player controls
                    mPlayerView.setUseController(false);
                });
            }

            @Override
            public void onAdBegin(long position, long duration, String clickURL) {
                runOnUiThread(() -> {
                    // Show ad link button if needed
                    if (clickURL.length() > 0) {
                        showAdClick(clickURL);
                    }
                });
            }

            @Override
            public void onAdSkippable(long offset, long limit) {
                // Show the skip message/button "skip ad in x seconds"
                new Thread(() -> {
                    try {
                        Thread.sleep(offset - mPlayer.getCurrentPosition());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    runOnUiThread(() -> {
                        showAdSkip(limit);
                    });
                }).start();
            }

            @Override
            public void onAdEnd() {
                runOnUiThread(() -> {
                    // Hide ad link, hide ad skip button
                    hideAdClick();
                    hideAdSkip();
                });
            }

            @Override
            public void onAdBreakEnd() {
                runOnUiThread(() -> {
                    // Unlock player controls, hide ad link, hide ad skip button
                    resetUI();
                });
            }
        });

        // Reset UI
        resetUI();

        // Run getURL in a thread
        mExecutor.submit(() -> {
            // Start the session and retrieve the streaming URL
            StreamingSessionResult result = mSession.getURL("http://bpk67.broadpeak-vcdn.com:80/bpk-vod/vod20/output01/City/City/index.mpd");

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

    private void showAdSkip(long limit) {
        mAdSkipButton.setEnabled(true);
        mAdSkipButton.setOnClickListener((click) -> {
            mPlayer.seekTo(limit);
        });
    }


    private void hideAdClick() {
        mAdClickButton.setEnabled(false);
    }

    private void hideAdSkip() {
        mAdSkipButton.setEnabled(false);
    }

    private void resetUI() {
        mPlayerView.setUseController(true);
        mPlayerView.showController();

        hideAdClick();
        hideAdSkip();
    }

    private void showAdClick(String clickURL) {
        mAdClickButton.setEnabled(true);
        mAdClickButton.setOnClickListener(view -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(clickURL)));
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
}