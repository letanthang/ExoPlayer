package vn.sbd.android.video;

import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by letanthang on 3/28/18.
 */

public class SBDAnalyzer implements Player.EventListener {
    CustomInfo customInfo;
    WebSocketClient ws;
    public interface CallBack {
        public void work(JsonObject resp);
    }
    public class VideoInfo {
        public String viewId;
        public JSONArray loadInfo;
        public boolean loadComplete = false;
        public boolean playing = false;
        public boolean buffering = false;
        public Date lastBuffering = null;

        public String localId = UUID.randomUUID().toString();
        public String wsSession = "";
        public long lastPlayPosition = 0;
        public Date lastActive = null;
        public long lastPauseTime = 0;
        public boolean endView = false;
        public boolean loaded = false;
        public int workerInterval = 0;
    }
    ExoPlayer player;
    VideoInfo videoInfo = new VideoInfo();
    HashMap<String, CallBack> callbacks = new HashMap<>();
    String session;
    Boolean sessionReady = false;
    public SBDAnalyzer(ExoPlayer player, CustomInfo customInfo) {
        try {
            this.player = player;
            this.customInfo = customInfo;
            player.addListener(this);
            this.lastPlayWhenReady = player.getPlayWhenReady();
            ws = new WebSocketClient( new URI( "ws://ws.sa.sbd.vn:10080" ) ) {

                @Override
                public void onMessage( String message ) {
                    Log.d("SBDAnalyzer","onMessage: got: " + message + "\n");
                    // aWjeif-opfdsdf-lkj::{status:ok}
                    String[] data = message.split("::");
                    String cbKey = data[0];
                    String respString = data[1];
                    Gson gson = new Gson();
                    JsonElement resp = gson.fromJson(respString, JsonElement.class);
                    callbacks.get(cbKey).work(resp.getAsJsonObject());
                    callbacks.remove(cbKey);
                }

                @Override
                public void onOpen( ServerHandshake handshake ) {
                    Log.d("SBDAnalyzer", "onOpen: You are connected to Server: " + getURI() + "\n" );
                    JsonObject data = new JsonObject();
                    data.addProperty("os", "android");
                    data.addProperty("device", "Nexus 5");

                    JsonObject json = new JsonObject();
                    json.addProperty("type", "initWS");
                    json.addProperty("session", session);
                    json.add("data", data);


                    SBDAnalyzer.this.send(json, new CallBack() {
                        @Override
                        public void work(JsonObject resp) {
                            if (resp.get("status").getAsString().equals("OK")) {
                                session = resp.getAsJsonArray("data").get(0).getAsString();
                                sessionReady = true;
                            }
                        }
                    });

                    SBDAnalyzer.this.initView();
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

    public boolean send(JsonObject json, CallBack cb) {
        String key = UUID.randomUUID().toString();
        json.addProperty("callback", key);
        if (this.send(json)) {
            callbacks.put(key, cb);
        }
        return false;
    }

    public boolean send(JsonObject json) {
//        if (json.getAsJsonObject().get("type").getAsString() == "initWS") {
//            this.ws.send(json.toString());
//        } else { //should implement queue for not init event
//            JsonArray queue = new JsonArray();
//            queue.add(json);
//        }
        String jsonString = "[" + json.toString() + "]";
        if (ws.isOpen()) {
            ws.send(jsonString);
            Log.d("SBDAnalyzer","send data" + jsonString);
            return true;
        }
        Log.d("SBDAnalyzer","WS not connected. " + jsonString);
        return false;

    }

    public void initView() {
        JsonObject data = new JsonObject();
        data.addProperty("envKey", customInfo.envKey);
        data.addProperty("viewerId", customInfo.viewerId);
        data.addProperty("playUrl", customInfo.videoUrl);
        data.addProperty("video", customInfo.getVideoJsonString());

        JsonObject json = new JsonObject();
        json.addProperty("type", "initView");
        json.add("data", data);

        this.send(json, new CallBack() {
            @Override
            public void work(JsonObject resp) {
                if (resp.get("status").getAsString().equals("OK")) {
                    SBDAnalyzer.this.videoInfo.viewId = resp.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
                    JsonObject data = new JsonObject();
                    data.addProperty("viewId", SBDAnalyzer.this.videoInfo.viewId);
                    data.addProperty("eventName", "PLAYER_LOAD");

                    JsonObject json = new JsonObject();
                    json.addProperty("type", "event");
                    json.add("data", data);
                    SBDAnalyzer.this.send(json);
                }
            }
        });

    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        Log.d("SBDAnalyzer","onTimelineChanged " + reason);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d("SBDAnalyzer","onTracksChanged");
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        Log.d("SBDAnalyzer","onLoadingChanged " + isLoading);
    }

    private boolean lastPlayWhenReady;
    private int lastPlaybackState = Player.STATE_ENDED;
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

    protected void playVideo() {
        videoInfo.playing = true;
        videoInfo.buffering = false;
        videoInfo.lastActive = new Date();
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PLAY");
        data.addProperty("viewId", videoInfo.viewId);
        data.addProperty("data", (new Date().getTime() - videoInfo.lastActive.getTime())/1000);
        data.addProperty("playPosition", 0);


        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.add("data", data);
        send(json);
    }
    protected void pauseVideo() {
        videoInfo.playing = false;
        videoInfo.lastPauseTime = player.getCurrentPosition();

        JsonObject data = new JsonObject();
        data.addProperty("eventName", "PAUSE");
        data.addProperty("viewId", videoInfo.viewId);
        data.addProperty("playPosition", videoInfo.lastPauseTime);


        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.add("data", data);
        this.send(json);
    }
    protected void unpauseVideo() {
        videoInfo.playing = true;
        videoInfo.buffering = false;
        videoInfo.lastActive = new Date();
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "UNPAUSE");
        data.addProperty("viewId", videoInfo.viewId);
        data.addProperty("playPosition", videoInfo.lastPauseTime);


        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.add("data", data);
        send(json);
    }
    protected void bufferVideo() {
        videoInfo.buffering = true;
        videoInfo.lastBuffering = new Date();
        videoInfo.lastPlayPosition = player.getCurrentPosition();
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "BUFFERING");
        data.addProperty("viewId", videoInfo.viewId);
        data.addProperty("lastPlayPosition", videoInfo.lastPlayPosition);

        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.add("data", data);
        this.send(json);
    }

    protected void resumeVideo() {
        videoInfo.buffering = false;
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "RESUME");
        data.addProperty("viewId", videoInfo.viewId);
        data.addProperty("data", (new Date().getTime() - videoInfo.lastBuffering.getTime()) / 1000);


        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.add("data", data);
        send(json);
    }

    protected void endVideo() {
        JsonObject data = new JsonObject();
        data.addProperty("eventName", "END");
        data.addProperty("viewId", videoInfo.viewId);

        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.add("data", data);
        this.send(json);
        videoInfo.lastPlayPosition = 0;
        videoInfo.playing = false;
        videoInfo.buffering = false;
        videoInfo.viewId = null;
        videoInfo.endView = true;
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        Log.d("SBDAnalyzer","onShuffleModeEnabledChanged");
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.d("SBDAnalyzer","onPlayerError");
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.d("SBDAnalyzer","onPositionDiscontinuity " + reason);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        Log.d("SBDAnalyzer","onPlaybackParametersChanged");
    }

    @Override
    public void onSeekProcessed() {
        Log.d("SBDAnalyzer","onSeekProcessed");
    }
}
