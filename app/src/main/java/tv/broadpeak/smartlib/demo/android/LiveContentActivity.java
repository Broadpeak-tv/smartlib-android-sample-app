package tv.broadpeak.smartlib.demo.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.broadpeak.smartlib.SmartLib;
import tv.broadpeak.smartlib.engine.manager.LoggerManager;
import tv.broadpeak.smartlib.session.streaming.StreamingSession;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionOptions;
import tv.broadpeak.smartlib.session.streaming.StreamingSessionResult;

public class LiveContentActivity extends AppCompatActivity {

    private ExoPlayer mPlayer;
    private PlayerView mPlayerView;

    private StreamingSession mSession;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final String TAG = "BpkTest";

    // BPK: TOGGLE USE_BPK_SMARTLIB TO TEST WITH OR WITHOUT SMARTLIB AND NANOCDN
    private final Boolean USE_BPK_SMARTLIB = true;

    private final String ANALYTICS_ADDRESS = "http://analytics-players.broadpeak.tv";
//    private final String ANALYTICS_ADDRESS = "";
    private final String NANOCDN_HOST = "127.0.0.1";

    // BPK: ADD YOUR STREAMS HERE
    private String[] streams = new String[] {
            "https://livesim2.dashif.org/livesim2/testpic_2s/Manifest.mpd",
            "https://livesim2.dashif.org/livesim2/mup_30/testpic_2s/Manifest.mpd"
    };

    private int streamIndex = 0;
    private Long zapStartTime = 0L;

    private boolean manifestReceived = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_content);

        findViewById(R.id.button_channel_down).setOnClickListener(view -> {
            channelDown();
        });
        findViewById(R.id.button_channel_up).setOnClickListener(view -> {
            channelUp();
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Create the player
        mPlayer = new ExoPlayer.Builder(this).build();

        // Set events
        mPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                mSession.stopStreamingSession();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "Media3 state: " + playbackState + " => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                if (!manifestReceived) {
                    // BPK: this event is triggered when the manifest has been received and parsed by media3
                    // Log the timestamp at which the manifest is received ths first time of the session
                    Log.d(TAG, "Media3 manifest received => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
                    manifestReceived = true;
                }
            }
        });

        // Get the player view
        mPlayerView = findViewById(R.id.playerView);

        // Bind the player to the view.
        mPlayerView.setPlayer(mPlayer);

        // Init SmartLib
        SmartLib.getInstance().init(getApplicationContext(), ANALYTICS_ADDRESS, NANOCDN_HOST, "");

        // BPK: DISABLE SMARTLIB LOGS WHICH CAN INCREASE PROCESSING TIME
//        LoggerManager.getInstance().setLogLevel(-1);

        // Launch at startup
        loadStream(streams[streamIndex]);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP) {
                channelUp();
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                channelDown();
            }
        }
        return true;
    }

    private void channelUp() {
        streamIndex = (streamIndex + 1) % streams.length;
        loadStream(streams[streamIndex]);
    }

    private void channelDown() {
        streamIndex = (streamIndex - 1) % streams.length;
        loadStream(streams[streamIndex]);
    }

    private void loadStream(String url) {
        zapStartTime = java.util.Calendar.getInstance().getTimeInMillis();
        stop();
        start(url);
    }

    private void stop() {

        if (mSession != null) {
            long stopStartTime = java.util.Calendar.getInstance().getTimeInMillis();
            Log.d(TAG, "stopStreamingSession");
            mSession.stopStreamingSession();
            Log.d(TAG, "stopStreamingSession OK " + (java.util.Calendar.getInstance().getTimeInMillis() - stopStartTime));
        }
        runOnUiThread(() -> {
            Log.d(TAG, "Media3 stop");
            mPlayer.stop();
        });
    }

    @OptIn(markerClass = UnstableApi.class) void start(String url) {

        if (USE_BPK_SMARTLIB) {
            // Create SmartLib session
            Log.d(TAG, "createStreamingSession");
            mSession = SmartLib.getInstance().createStreamingSession();
            // Attach the player on the same thread
            mSession.attachPlayer(mPlayer);
            Log.d(TAG, "createStreamingSession OK => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
        }

        // Run getURL in a thread
        mExecutor.submit(() -> {
            String finalUrl;

            if (USE_BPK_SMARTLIB) {
                // Start the session and retrieve the streaming URL
                Log.d(TAG, "getURL => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
                Long getUrlTime = java.util.Calendar.getInstance().getTimeInMillis();
                StreamingSessionResult result = mSession.getURL(url);
                Log.d(TAG, "redirectedURL => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime) + " (" + (java.util.Calendar.getInstance().getTimeInMillis() - getUrlTime) + ")");

                if (result.isError()) {
                    mSession.stopStreamingSession();
                    return;
                }

                finalUrl = result.getURL();
            } else {
                finalUrl = url;
            }

            // ExoPlayer requires main thread
            runOnUiThread(() -> {

                // Create a data source factory.
//                Log.d(TAG, "create data source => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
                DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();

                // Create a media source pointing SmartLib result URL
                MediaSource mediaSource = null;
                if (finalUrl.contains(".mpd")) {
                    mediaSource = new DashMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(finalUrl));
                } else if (finalUrl.contains(".m3u8")) {
                    mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(finalUrl));
                }

                // Attach media source events to the SmartLib session
                /*mediaSource.addEventListener(new Handler(Looper.getMainLooper()), (MediaSourceEventListener) mSession.getListener());
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
                });*/

                // Set the media item to be played
//                Log.d(TAG, "Media3 set media source => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
                mPlayer.setMediaSource(mediaSource);

                // Prepare the player
//                Log.d(TAG, "Media3 prepare => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
                mPlayer.prepare();

                // Start the playback
                mPlayer.setPlayWhenReady(true);
                manifestReceived = false;
                Log.d(TAG, "Media3 play => " + (java.util.Calendar.getInstance().getTimeInMillis() - zapStartTime));
                mPlayer.play();
            });
        });
    }

}