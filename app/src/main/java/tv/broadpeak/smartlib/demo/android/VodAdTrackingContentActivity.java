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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import tv.broadpeak.smartlib.SmartLib;
import tv.broadpeak.smartlib.ad.AdBreakData;
import tv.broadpeak.smartlib.ad.AdData;
import tv.broadpeak.smartlib.ad.AdManager;
import tv.broadpeak.smartlib.engine.manager.LoggerManager;
import tv.broadpeak.smartlib.session.streaming.StreamingSession;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionResult;

public class VodAdTrackingContentActivity extends AppCompatActivity {

    private final String TAG = VodAdTrackingContentActivity.class.getSimpleName();

    private ExoPlayer mPlayer;
    private StyledPlayerView mPlayerView;

    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Button mAdClickButton;
    private Button mAdSkipButton;

    private TextView mAdBreakTextView;

    private TextView mAdTextView;

    private ProgressBar mAdBreakProgressBar;

    private ProgressBar mAdProgressBar;

    private CountDownTimer adBreakTimer;

    private CountDownTimer adTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_ad_tracking_content);

        mAdClickButton = findViewById(R.id.adClickButton);
        mAdSkipButton = findViewById(R.id.adSkipButton);
        mAdBreakTextView = findViewById(R.id.adBreakTextView);
        mAdTextView = findViewById(R.id.adTextView);
        mAdBreakProgressBar = findViewById(R.id.adBreakProgressBar);
        mAdProgressBar = findViewById(R.id.adProgressBar);


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
            public void onAdBreakBegin(AdBreakData adBreakData) {
                runOnUiThread(() -> {
                    // Lock player controls
                    mPlayerView.setUseController(false);
                    mPlayerView.hideController();

                    long playerPosition = getPlayerPosition();
                    long adBreakEnd = adBreakData.getStartPosition() + adBreakData.getDuration();
                    long adBreakRemainingTime = adBreakEnd - playerPosition;

                    // Show ad break info
                    showAdBreakInfo();
                    mAdBreakTextView.setText("Ad break: " + getDate(adBreakData.getStartPosition()) + " to " + getDate(adBreakEnd));

                    adBreakTimer = new CountDownTimer(adBreakRemainingTime, 100) {
                        public void onTick(long millisUntilFinished) {
                            long playerPosition = getPlayerPosition();
                            int progress = (int) (((float)(playerPosition - adBreakData.getStartPosition()) / adBreakData.getDuration())*100);
                            mAdBreakProgressBar.setProgress(progress);
                        }

                        public void onFinish() {

                        }
                    }.start();
                });
            }

            @Override
            public void onAdBegin(AdData adData, AdBreakData adBreakData) {
                runOnUiThread(() -> {
                    long playerPosition = getPlayerPosition();
                    long adEnd = adData.getStartPosition() + adData.getDuration();
                    long adRemainingTime = adEnd - playerPosition;

                    showAdInfo();
                    mAdTextView.setText("Ad " + (adData.getIndex()+1) + "/" + adBreakData.getAdCount());
                    mAdTextView.append(": " + getDate(adData.getStartPosition()) + " to " + getDate(adEnd));

                    adTimer = new CountDownTimer(adRemainingTime, 100) {
                        public void onTick(long millisUntilFinished) {
                            long playerPosition = getPlayerPosition();
                            int progress = (int) (((float)(playerPosition - adData.getStartPosition()) / adData.getDuration())*100);
                            mAdProgressBar.setProgress(progress);
                        }

                        public void onFinish() {

                        }
                    }.start();

                    // Show ad link button if needed
                    if (adData.getClickURL().length() > 0) {
                        showAdClick(adData.getClickURL());
                    }
                });
            }

            @Override
            public void onAdSkippable(AdData adData, AdBreakData adBreakData, long adSkipBegin, long adEnd, long adBreakEnd) {
                // Show the skip message/button "skip ad in x seconds"
                runOnUiThread(() -> {
                    long remainingTime = adSkipBegin - getPlayerPosition();

                    new CountDownTimer(remainingTime, 100) {
                        public void onTick(long millisUntilFinished) {
                            mAdSkipButton.setText("SKIP AD IN " + (millisUntilFinished / 1000) + "s");
                        }

                        public void onFinish() {
                            showAdSkip(adEnd);
                        }
                    }.start();
                });
            }

            @Override
            public void onAdEnd(AdData adData, AdBreakData adBreakData) {
                runOnUiThread(() -> {
                    // Hide ad link, hide ad skip button
                    hideAdClick();
                    hideAdSkip();
                });
            }

            @Override
            public void onAdBreakEnd(AdBreakData adBreakData) {
                runOnUiThread(() -> {
                    // Unlock player controls, hide ad link, hide ad skip button
                    resetUI();
                });
            }
        });

        mSession.setAdDataListener(adList -> Log.v(TAG, adList.toString()));

        // Reset UI
        resetUI();

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

    private void showAdSkip(long adEnd) {
        mAdSkipButton.setEnabled(true);
        mAdSkipButton.setText("SKIP AD");
        mAdSkipButton.setOnClickListener((click) -> {
            long adRemainingTime = adEnd - getPlayerPosition();
            mPlayer.seekTo(mPlayer.getCurrentPosition() + adRemainingTime);
        });
    }


    private void hideAdClick() {
        mAdClickButton.setEnabled(false);
    }

    private void hideAdSkip() {
        mAdSkipButton.setEnabled(false);
    }

    private void showAdBreakInfo() {
        mAdBreakTextView.setTextColor(Color.BLACK);
        mAdBreakProgressBar.setVisibility(View.VISIBLE);
    }

    private void showAdInfo() {
        mAdProgressBar.setVisibility(View.VISIBLE);
    }

    private void resetUI() {
        mPlayerView.setUseController(true);
        mPlayerView.setControllerShowTimeoutMs(2000);
        mAdBreakTextView.setText("Waiting for next ad break...");
        mAdBreakTextView.setTextColor(Color.GRAY);
        mAdTextView.setText("");
        mAdBreakProgressBar.setProgress(0);
        mAdBreakProgressBar.setVisibility(View.GONE);
        mAdProgressBar.setProgress(0);
        mAdProgressBar.setVisibility(View.INVISIBLE);

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

        // stop ad timers
        if (adBreakTimer != null) {
            adBreakTimer.cancel();
        }

        if(adTimer != null) {
            adTimer.cancel();
        }

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

    private String getDate(long time) {
        if (time < 1262300400000L) {
            time += 1708470000000L; // to handle VOD with position starting to 0 instead of timestamp
        }

        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time);
        return DateFormat.format("HH:mm:ss", cal).toString();
    }

    private long getPlayerPosition() {
        long playerPosition = mPlayer.getCurrentPosition();

        // logPosition();

        if (mPlayer.isCurrentMediaItemLive()) {
            Timeline currentTimeline = mPlayer.getCurrentTimeline();

            if (!currentTimeline.isEmpty()) {
                // PDT configured
                Timeline.Window window = currentTimeline.getWindow(mPlayer.getCurrentMediaItemIndex(), new Timeline.Window());
                if (window != null) {
                    if (window.windowStartTimeMs != C.TIME_UNSET) {
                        return window.windowStartTimeMs + mPlayer.getCurrentPosition();
                    }
                }

                // PDT not configured
                playerPosition -= currentTimeline.getPeriod(mPlayer.getCurrentPeriodIndex(), new Timeline.Period())
                        .getPositionInWindowMs();
            }
        }

        return playerPosition;
    }
}