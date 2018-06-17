package vn.sbd.android.video;

import android.content.res.Configuration;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Created by letanthang on 3/28/18.
 */

public class SBDAnalyzer implements Player.EventListener, VideoRendererEventListener {
    private final String sdkName = "AndroidSDK";
    private final String sdkVersion = "0.1";
    private static SBDAnalyzer instance;
    private CustomInfo customInfo;
    private WebSocketClient ws;
    private SimpleExoPlayer player;
    private VideoInfo videoInfo = new VideoInfo();
    private HashMap<String, CallBack> callbacks = new HashMap<>();
    private String session;
    private Boolean sessionReady = false;
    private Context context;

    @Override
    public void onDroppedFrames(int count, long elapsedMs) {
        Log.d("SBDAnalyzer", "onDroppedFrames " + count + " " + elapsedMs);
    }

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
        Log.d("SBDAnalyzer", "onVideoEnabled");
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        Log.d("SBDAnalyzer", "onVideoDecoderInitialized");
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
        Log.d("SBDAnalyzer", "onVideoInputFormatChanged " + format.bitrate);
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
        Log.d("SBDAnalyzer", "onRenderedFirstFrame");
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
        Log.d("SBDAnalyzer", "onVideoDisabled");
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.d("SBDAnalyzer", "onVideoSizeChanged " + width + " " + height);
        changeSize(width, height);
    }

    public interface CallBack {
        void work(JsonObject resp);
    }
    public static SBDAnalyzer getInstance(Context context) {
        if (instance == null) {
            instance = new SBDAnalyzer(context);
        }
        return instance;
    }
    public static SBDAnalyzer getInstance() {
        if (instance == null) {
            instance = new SBDAnalyzer(null);
        }
        return instance;
    }

    private SBDAnalyzer(Context context) {
        this.context = context;
        try {
            String osVersion = Build.VERSION.RELEASE;
            String device = Build.MANUFACTURER + " " + Build.MODEL;
            String deviceType = isTablet(context) ? "Tablet" : "Smartphone";
            String os = "Android";
            String appName = getApplicationName(context);
            String appVersion = getApplicationVersion(context);
            String userAgent = sdkName + "/" + sdkVersion + " " + os + "/" + osVersion + " "
                    + appName + "/" + appVersion + " " + deviceType + "/" + device;
            Map<String, String> httpHeaders = new HashMap<>();
            httpHeaders.put("user-agent", userAgent);
            Log.d("SBDAnalyzer","user-agent=" + userAgent);

            ws = new WebSocketClient( new URI( "ws://ws.stag-sa.sbd.vn:10080" ), httpHeaders ) {
                @Override
                public void onMessage( String message ) {
                    Log.d("SBDAnalyzer","onMessage: got: " + message + "\n");
                    // aWjeif-opfdsdf-lkj::{status:ok}
                    String[] data = message.split("::");
                    String cbKey = data[0];
                    String respString = data[1];
                    Gson gson = new Gson();
                    JsonElement resp = gson.fromJson(respString, JsonElement.class);
                    Log.d("SBDAnalyzer","callbacks length:  " + callbacks.size() + "\n");
                    Log.d("SBDAnalyzer","callback key exist:  " + (callbacks.get(cbKey) != null) + "\n");
                    callbacks.get(cbKey).work(resp.getAsJsonObject());
                    callbacks.remove(cbKey);
                }

                @Override
                public void onOpen( ServerHandshake handshake ) {
                    Log.d("SBDAnalyzer", "onOpen: You are connected to Server: " + getURI() + "\n" );

                    initWS();
                    initView();
                    startSendWorker();
                }

                @Override
                public void onClose( int code, String reason, boolean remote ) {
                    Log.d("SBDAnalyzer", "onClose: You have been disconnected from: " + getURI() + "; Code: " + code + " " + reason + "\n" );

                }

                @Override
                public void onError( Exception ex ) {
                    Log.d("SBDAnalyzer", "Exception occured ...\n" + ex + "\n" );
                    ex.printStackTrace();
                }
            };
            ws.connect();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    public void setCustomInfo(CustomInfo customInfo) {
        this.customInfo = customInfo;
    }
    public void setPlayer(SimpleExoPlayer player) {
        Log.d("SBDAnalyzer", "setPlayer!!!!!!!!!!!!!!!!!" );
        this.player = player;
        this.lastPlayWhenReady = player.getPlayWhenReady();
        videoInfo = new VideoInfo();
        this.player.addListener(this);
        this.player.addVideoDebugListener(this);
        loadPlayer();
        startSendWorker();
    }
    public void stopTimer() {
        sendTimer.cancel();
    }

    public boolean send(JsonObject json, CallBack cb) {
        String key = UUID.randomUUID().toString();
        json.addProperty("callback", key);
        callbacks.put(key, cb);
        if (this.send(json)) {
            return true;
        }
        callbacks.remove(key);
        return false;
    }

    JsonArray queue = new JsonArray();

    public boolean send(JsonObject json) {
        if (json.get("type").getAsString() == "initWS") {
            String jsonString = "[" + json.toString() + "]";
            Log.d("SBDAnalyzer","send initWS " + jsonString);
            ws.send(jsonString);
        } else { //should implement queue for not init event
            Log.d("SBDAnalyzer","add to queue " + json.get("type").getAsString() + " " + json.getAsJsonObject("data").toString());
            JsonObject data = json.getAsJsonObject("data");
            json.remove("data");
            json.addProperty("data", data.toString());
            queue.add(json);
        }
        return true;
    }

    JsonArray afterInitQueue = new JsonArray();

    public boolean sendViewEvent(JsonObject data) {
        try {
            Log.d("SBDAnalyzer", "sendViewEvent " + data.get("eventName").getAsString());
            if (data.get("date") == null) {
                data.addProperty("date", getUTCDate());
            }
            if (data.get("playPosition") == null) {
                data.addProperty("playPosition", player.getCurrentPosition());
            }

            JsonObject json = new JsonObject();
            json.addProperty("type", "event");
            json.add("data", data);
            if (videoInfo.viewId != null) {
                data.addProperty("viewId", videoInfo.viewId);
                send(json);
            } else {
                Log.d("SBDAnalyzer", "add to afterQueue " + data.get("eventName").getAsString());
                afterInitQueue.add(json);
            }
        } catch (Exception ex) {
            Log.d("SBDAnalyzer", ex.getMessage());
        }

        return true;
    }

    private String getUTCDate() {
        final Date date = new Date();
        final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        final SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT);
        final TimeZone utc = TimeZone.getTimeZone("UTC");
        sdf.setTimeZone(utc);
        return sdf.format(date);
    }

    static Timer timer;
    public void startView() {

        if (timer == null) {
            Log.d("SBDAnalyzer","timer null");

            if (this.initView()){
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        timer = null;
                    }
                }, 100);
            }

        } else {
            Log.d("SBDAnalyzer","timer not null");
        }

    }

    Timer sendTimer;
    public void startSendWorker() {
        sendTimer = new Timer();
        sendTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendWorker();
            }
        }, 0, 500);
    }

    public void sendWorker() {
//        Log.d("SBDAnalyzer","send worker");
        if (!sessionReady || !ws.isOpen() || queue.size() == 0) {
            if (queue.size() == 0) {
//                Log.d("SBDAnalyzer","queue has nothing to send");
            } else {
//                Log.d("SBDAnalyzer","not send yet, ws or session not ready");
            }

            return;
        }
        Log.d("SBDAnalyzer","send worker data");
        sendArray(queue);
        queue = new JsonArray();

    }

    private void sendArray(JsonArray arr) {
        String jsonString = arr.toString();
        Log.d("SBDAnalyzer","sendArray " + jsonString);
        ws.send(jsonString);
    }
    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }
    public static String getApplicationVersion(Context context) {
        String versionName = "0.0";
        try {
            versionName = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }
    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public void initWS() {
        Log.d("SBDAnalyzer","initWS");


        JsonObject json = new JsonObject();
        json.addProperty("type", "initWS");
        json.addProperty("session", session);
//        json.add("data", data);


        SBDAnalyzer.this.send(json, new CallBack() {
            @Override
            public void work(JsonObject resp) {
                if (resp.get("status").getAsString().equals("OK")) {
                    Log.d("SBDAnalyzer","initWS success");
                    session = resp.getAsJsonArray("data").get(0).getAsString();
                    sessionReady = true;
                }
            }
        });
    }
    boolean viewInited = false;
    public boolean initView() {
        if (viewInited) return false;
        viewInited = true;
        Log.d("SBDAnalyzer","initView");
        JsonObject data = new JsonObject();
        data.addProperty("envKey", customInfo.envKey);
        data.addProperty("viewerId", customInfo.viewerId);
        data.addProperty("playUrl", customInfo.videoUrl);
        data.add("video", customInfo.getVideoJson());

        JsonObject json = new JsonObject();
        json.addProperty("type", "initView");
        json.add("data", data);

        return this.send(json, new CallBack() {
            @Override
            public void work(JsonObject resp) {
                if (resp.get("status").getAsString().equals("OK")) {
                    viewInited = false;
                    synchronized (videoInfo) {
                        videoInfo.viewId = resp.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
                    }

                    Log.d("SBDAnalyzer","initView success");
                    if (afterInitQueue.size() > 0) {
                        Log.d("SBDAnalyzer","send after queue ");
                        sendAfterInitQueue();
                    }
                }
            }
        });

    }

    protected void sendAfterInitQueue() {
        for(int i= 0; i < afterInitQueue.size(); i++) {
            JsonObject data = afterInitQueue.get(i).getAsJsonObject().getAsJsonObject("data");
            data.addProperty("viewId", videoInfo.viewId);
            afterInitQueue.get(i).getAsJsonObject().remove("data");
            afterInitQueue.get(i).getAsJsonObject().addProperty("data", data.toString());

        }
        sendArray(afterInitQueue);
        afterInitQueue = new JsonArray();
    }

    protected void loadPlayer() {
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PLAYER_LOAD");
        sendViewEvent(data);
    }

    private boolean lastPlayWhenReady;
    private int lastPlaybackState = Player.STATE_ENDED;

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        Log.d("SBDAnalyzer", "onTimelineChanged");
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d("SBDAnalyzer", "onTracksChanged");
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        Log.d("SBDAnalyzer", "onLoadingChanged " + isLoading);

        try {
            if (player != null) {
                int audioBitrate = player.getAudioFormat().bitrate;
                int videoBitrate = player.getVideoFormat().bitrate;
                Log.d("SBDAnalyzer", "current " + isLoading + "audio bitrate: " + audioBitrate + " | video bitrate: " + videoBitrate);
            }
        } catch (Exception error) {
            Log.d("SBDAnalyzer", error.getLocalizedMessage());
        }


    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d("SBDAnalyzer","onPlayerStateChanged " + playWhenReady + " " + playbackState);

        if (playWhenReady != lastPlayWhenReady) {
            if (playWhenReady == false) {
                this.pauseVideo();
            } else {
                if (videoInfo.lastPauseTime == 0) {
                    this.playVideo();
                } else {
                    this.unpauseVideo();
                    if (playbackState == Player.STATE_READY) {
                        this.realPlayVideo();
                    }
                }
                if (videoInfo.viewId == null) {
                    this.initView();
                }
            }
        }

        if (playbackState != lastPlaybackState) {
            if (lastPlaybackState == Player.STATE_ENDED && player.getCurrentPosition() == 0) {
                this.playVideo();
            }

            if (playbackState == Player.STATE_ENDED) {
                this.endVideo();
            } else if (playbackState == Player.STATE_BUFFERING) {
                this.bufferVideo();
            } else if (playbackState == Player.STATE_READY) {
                this.resumeVideo();
            }
        }

        lastPlaybackState = playbackState;
        lastPlayWhenReady = playWhenReady;


    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.d("SBDAnalyzer", "onPositionDiscontinuity");
        pauseVideo();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        Log.d("SBDAnalyzer", "onPlaybackParametersChanged");
    }

    @Override
    public void onSeekProcessed() {
        Log.d("SBDAnalyzer", ":onSeekProcessed");
    }


    public void onPlayWhenReadyCommitted() {
        Log.d("SBDAnalyzer", "onPlayWhenReadyCommitted");
        if (lastPlayWhenReady) {
            realPlayVideo();
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.d("SBDAnalyzer", error.getMessage());
        videoInfo.lastPlayPosition = player.getCurrentPosition();
        videoInfo.playing = false;
        videoInfo.buffering = false;

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "ERROR");
        sendViewEvent(data);

    }

    protected void playVideo() {
        synchronized (videoInfo) {
            videoInfo.playing = true;
            videoInfo.buffering = false;
            videoInfo.lastActive = new Date().getTime();
        }

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PLAY");

        sendViewEvent(data);
    }
    protected void pauseVideo() {
        synchronized (videoInfo) {
            videoInfo.playing = false;
            videoInfo.lastPauseTime = player.getCurrentPosition();
        }


        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PAUSE");
        data.addProperty("playPosition", videoInfo.lastPauseTime);

        this.sendViewEvent(data);
    }
    protected void unpauseVideo() {
        synchronized (videoInfo) {
            videoInfo.playing = true;
            videoInfo.buffering = false;
            videoInfo.lastActive = new Date().getTime();
        }

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PLAY");
        data.addProperty("playPosition", player.getCurrentPosition());

        sendViewEvent(data);
    }

    protected void realPlayVideo() {
        Log.d("SBDAnalyzer", "onPlay: realPlay");
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PLAYING");
        data.addProperty("playPosition", player.getCurrentPosition());
        double startupTime = (new Date().getTime() - videoInfo.lastActive) / 1000.0;
        data.addProperty("data", startupTime);

        videoInfo.hasStartup = true;
        videoInfo.lastActive = 0;
        sendViewEvent(data);
    }

    protected void bufferVideo() {
        synchronized (videoInfo) {
            videoInfo.buffering = true;
            videoInfo.lastActive = new Date().getTime();
            videoInfo.lastPlayPosition = player.getCurrentPosition();
        }

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "BUFFERING");
        data.addProperty("lastPlayPosition", videoInfo.lastPlayPosition);

        this.sendViewEvent(data);
    }

    protected void resumeVideo() {
//        synchronized (videoInfo) {
//            videoInfo.buffering = false;
//        }

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "SEEKED");

        sendViewEvent(data);
        if (lastPlayWhenReady) {
            realPlayVideo();
        }
    }

    protected void endVideo() {
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "END");

        this.sendViewEvent(data);
        synchronized (videoInfo) {
            videoInfo.lastPlayPosition = 0;
            videoInfo.playing = false;
            videoInfo.buffering = false;
            videoInfo.viewId = null;
            videoInfo.endView = true;
        }
    }

    protected void changeSize(int width, int height) {
        JsonObject infos = new JsonObject();
        infos.addProperty("playerWidth", width);
        infos.addProperty("playerHeight", height);
        infos.addProperty("videoWidth", width);
        infos.addProperty("videoHeight", height);

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "DIMENSION");
        data.add("infos", infos);

        this.sendViewEvent(data);

    }

}
