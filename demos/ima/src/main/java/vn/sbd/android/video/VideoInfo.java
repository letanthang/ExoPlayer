package vn.sbd.android.video;

import org.json.JSONArray;

import java.util.Date;
import java.util.UUID;

/**
 * Created by letanthang on 4/10/18.
 */

class VideoInfo {
    public String viewId;
    public JSONArray loadInfo;
    public boolean loadComplete = false;
    public boolean playing = false;
    public boolean buffering = false;

    public String localId = UUID.randomUUID().toString();
    public String wsSession = "";
    public long lastPlayPosition = 0;
    public long lastActive = 0;
    public long lastPauseTime = 0;
    public boolean endView = false;
    public boolean loaded = false;
    public int workerInterval = 0;
    public boolean hasStartup = false;
}
