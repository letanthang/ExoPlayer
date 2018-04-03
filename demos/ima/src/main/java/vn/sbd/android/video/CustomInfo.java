package vn.sbd.android.video;

import com.google.gson.JsonObject;

/**
 * Created by letanthang on 3/30/18.
 */

public class CustomInfo {
   public String envKey;
   public String viewerId;
   public String videoUrl;
   public String videoId;
   public String videoTitle;
   public String videoSeries;
   public String videoDuration;
   public String videoAuthor;
   public String videoCdn;
   public String videoIsp;
   public CustomInfo(String envKey, String viewerId) {
       this.envKey = envKey;
       this.viewerId = viewerId;
   }
   public String getVideoJsonString() {
       JsonObject obj = new JsonObject();
       JsonObject filter = new JsonObject();
       filter.addProperty("author", videoAuthor);
       filter.addProperty("cdn", videoCdn);
       filter.addProperty("isp", videoIsp);
       obj.addProperty("videoUrl", videoUrl);
       obj.addProperty("videoId", videoId);
       obj.addProperty("videoTitle", videoTitle);
       obj.addProperty("videoSeries", videoSeries);
       obj.addProperty("videoDuration", videoDuration);
       obj.add("filter", filter);
       return obj.toString();
   }
}
