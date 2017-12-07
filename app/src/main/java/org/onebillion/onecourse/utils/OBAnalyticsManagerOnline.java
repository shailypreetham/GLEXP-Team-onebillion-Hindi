package org.onebillion.onecourse.utils;

import android.app.Activity;
import android.graphics.PointF;

import org.onebillion.onecourse.mainui.MainActivity;

import java.util.HashMap;
import java.util.Map;

import com.parse.Parse;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;

import android.location.Location;

import io.keen.client.java.JavaKeenClientBuilder;
import io.keen.client.java.KeenClient;
import io.keen.client.java.KeenProject;


/**
 * Created by pedroloureiro on 29/11/2017.
 */

public class OBAnalyticsManagerOnline extends OBAnalyticsManager
{

    /*
     * To open the dashboard
     * parse-dashboard --appId "org.onebillion.onecourse.kenya" --masterKey "4asterix" --serverURL "http://onecourse-kenya.herokuapp.com/parse" --appName "onecourse-kenya"
     *
     */

    public OBAnalyticsManagerOnline (Activity activity)
    {
        super();
        //
        if (OBConfigManager.sharedManager.isAnalyticsEnabled())
        {
            startupAnalytics(activity);
        }
    }



    @Override
    protected void startupAnalytics (Activity activity)
    {
        super.startupAnalytics(activity);
        //
        // TODO: move this to the config
        Parse.initialize(new Parse.Configuration.Builder(activity)
                .applicationId("org.onebillion.onecourse.kenya")
                .server("http://onecourse-kenya.herokuapp.com/parse/")
                .build()
        );

        KeenClient client = new JavaKeenClientBuilder().build();
        KeenClient.initialize(client);
        //
        // TODO: move this to the config
        KeenProject project = new KeenProject("5a28013ac9e77c000154b908", "2A82EE41AD3F74E58226C2E885C681251EB2620678A31F6E39E6FECC156E17E3AAA109F66870A18A9EBF909A614C94FC0E62820441105143A9DDA8BD2959AA3C231784D148EC26C9B0A8E867294C6B6404F0834E64BCA0EBA588A3DF074A41ED", null);
        KeenClient.client().setDefaultProject(project);


    }


    @Override
    public void onStart()
    {

    }

    @Override
    public void onStop()
    {

    }


    private void logEvent(String eventName, Map<String, Object> properties)
    {
        if (!OBConfigManager.sharedManager.isAnalyticsEnabled()) return;
        //
        MainActivity.log("OBAnalyticsManagerOnline.logEvent: " + eventName + " " + properties.toString());
        //
        ParseObject parseObject = new ParseObject(eventName);
        for (String key : properties.keySet())
        {
            parseObject.put(key, properties.get(key));
        }
        //
        parseObject.put(OBAnalytics.Params.DEVICE_UUID, OBSystemsManager.sharedManager.device_getUUID());
        //
        parseObject.saveInBackground();
        //
        KeenClient.client().addEvent(eventName, properties);
        KeenClient.client().sendQueuedEventsAsync();

    }


    @Override
    public void deviceTurnedOn ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_STATE, OBAnalytics.Params.DEVICE_STATE_ON);
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }


    @Override
    public void deviceTurnedOff ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_STATE, OBAnalytics.Params.DEVICE_STATE_OFF);
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }

    @Override
    public void deviceHeadphonesPluggedIn ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_HEADPHONES_STATE, OBAnalytics.Params.DEVICE_HEADPHONES_STATE_PLUGGED);
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }

    @Override
    public void deviceHeadphonesUnplugged ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_HEADPHONES_STATE, OBAnalytics.Params.DEVICE_HEADPHONES_STATE_UNPLUGGED);
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }


    @Override
    public void touchMadeInUnit (String unitID, PointF startLoc, long started, PointF endLoc, long finished)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.TOUCH_UNIT_ID, unitID);
        parameters.put(OBAnalytics.Params.TOUCH_START_LOCATION, startLoc);
        parameters.put(OBAnalytics.Params.TOUCH_START_TIME, Long.valueOf(started));
        parameters.put(OBAnalytics.Params.TOUCH_END_LOCATION, endLoc);
        parameters.put(OBAnalytics.Params.TOUCH_END_TIME, Long.valueOf(finished));
        //
        logEvent(OBAnalytics.Event.TOUCH, parameters);
    }



    @Override
    public void deviceGpsLocation ()
    {
        Map<String, Object> parameters = new HashMap();
        //
        Location loc = OBLocationManager.sharedManager.getLastKnownLocation();
        if (loc != null)
        {
            parameters.put(OBAnalytics.Params.DEVICE_GPS_LATITUDE, Double.valueOf(loc.getLatitude()));
            parameters.put(OBAnalytics.Params.DEVICE_GPS_LONGITUDE, Double.valueOf(loc.getLongitude()));
            parameters.put(OBAnalytics.Params.DEVICE_GPS_ALTITUDE, Double.valueOf(loc.getAltitude()));
            parameters.put(OBAnalytics.Params.DEVICE_GPS_BEARING, Float.valueOf(loc.getBearing()));
            //
            logEvent(OBAnalytics.Event.DEVICE, parameters);
        }
        else
        {
            MainActivity.log("Last Known Location is NULL. Skipping analytics event");
        }
    }



    @Override
    public void deviceVolumeChanged (float value)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_VOLUME, Float.valueOf(value));
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }



    @Override
    public void deviceScreenTurnedOn ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_SCREEN_STATE, OBAnalytics.Params.DEVICE_SCREEN_STATE_ON);
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }



    @Override
    public void deviceScreenTurnedOff ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_SCREEN_STATE, OBAnalytics.Params.DEVICE_SCREEN_STATE_OFF);
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }



    @Override
    public void deviceMobileSignalStrength (float value)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_SIGNAL_STRENGTH, Float.valueOf(value));
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }



    @Override
    public void deviceStorageUse (long used, long total)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.DEVICE_USED_STORAGE, Long.valueOf(used));
        parameters.put(OBAnalytics.Params.DEVICE_TOTAL_STORAGE, Long.valueOf(total));
        //
        logEvent(OBAnalytics.Event.DEVICE, parameters);
    }



    @Override
    public void batteryState(float batteryValue, Boolean pluggedIn, String chargerType)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.BATTERY_LEVEL, Float.valueOf(batteryValue));
        if (!pluggedIn)
        {
            parameters.put(OBAnalytics.Params.BATTERY_CHARGER_STATE, OBAnalytics.Params.BATTERY_CHARGER_STATE_UNPLUGGED);
        }
        else
        {
            parameters.put(OBAnalytics.Params.BATTERY_CHARGER_STATE, chargerType);
        }
        //
        logEvent(OBAnalytics.Event.BATTERY, parameters);
    }




    @Override
    public void studyZoneStartedNewDay ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.APP_MODE_CHANGE, OBAnalytics.Params.APP_STUDY_ZONE);
        //
        logEvent(OBAnalytics.Event.APP, parameters);
    }



    @Override
    public void studyZoneUnitCompleted(String unitID, long started, long finished, float score, int replayAudioPresses)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.UNIT_ID, unitID);
        parameters.put(OBAnalytics.Params.UNIT_SCORE, Float.valueOf(score));
        parameters.put(OBAnalytics.Params.UNIT_START_TIME,Long.valueOf(started));
        parameters.put(OBAnalytics.Params.UNIT_END_TIME,Long.valueOf(finished));
        parameters.put(OBAnalytics.Params.UNIT_REPLAY_AUDIO_COUNT, Integer.valueOf(replayAudioPresses));
        parameters.put(OBAnalytics.Params.UNIT_MODE, OBAnalytics.Params.UNIT_MODE_STUDY_ZONE);
        //
        logEvent(OBAnalytics.Event.APP, parameters);
    }



    @Override
    public void communityModeEntered ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.APP_MODE_CHANGE, OBAnalytics.Params.APP_COMMUNITY_MODE);
        //
        logEvent(OBAnalytics.Event.APP, parameters);
    }



    @Override
    public void communityModeUnitCompleted (String unitID, long started, long finished, float score, int replayAudioPresses)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.UNIT_ID, unitID);
        parameters.put(OBAnalytics.Params.UNIT_SCORE, Float.valueOf(score));
        parameters.put(OBAnalytics.Params.UNIT_START_TIME,Long.valueOf(started));
        parameters.put(OBAnalytics.Params.UNIT_END_TIME,Long.valueOf(finished));
        parameters.put(OBAnalytics.Params.UNIT_REPLAY_AUDIO_COUNT, Integer.valueOf(replayAudioPresses));
        parameters.put(OBAnalytics.Params.UNIT_MODE, OBAnalytics.Params.UNIT_MODE_COMMUNITY_MODE);
        //
        logEvent(OBAnalytics.Event.APP, parameters);
    }


    @Override
    public void playZoneEntered ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.APP_MODE_CHANGE, OBAnalytics.Params.APP_PLAY_ZONE);
        //
        logEvent(OBAnalytics.Event.APP, parameters);
    }



    @Override
    public void playZoneUnitCompleted (String unitID, long started, long finished, float score, int replayAudioPresses)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.UNIT_ID, unitID);
        parameters.put(OBAnalytics.Params.UNIT_SCORE, Float.valueOf(score));
        parameters.put(OBAnalytics.Params.UNIT_START_TIME,Long.valueOf(started));
        parameters.put(OBAnalytics.Params.UNIT_END_TIME,Long.valueOf(finished));
        parameters.put(OBAnalytics.Params.UNIT_REPLAY_AUDIO_COUNT, Integer.valueOf(replayAudioPresses));
        parameters.put(OBAnalytics.Params.UNIT_MODE, OBAnalytics.Params.UNIT_MODE_PLAY_ZONE);
        //
        logEvent(OBAnalytics.Event.UNITS, parameters);
    }


    @Override
    public void playZoneVideoWatched (String videoID)
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.PLAY_ZONE_VIDEO_ID, videoID);
        //
        logEvent(OBAnalytics.Event.PLAY_ZONE, parameters);
    }


    @Override
    public void playZoneCreationsVideoAdded ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.CREATION_TYPE, OBAnalytics.Params.CREATION_TYPE_VIDEO);
        //
        logEvent(OBAnalytics.Event.PLAY_ZONE, parameters);
    }

    @Override
    public void playZoneCreationsDoodleAdded ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.CREATION_TYPE, OBAnalytics.Params.CREATION_TYPE_DOODLE);
        //
        logEvent(OBAnalytics.Event.PLAY_ZONE, parameters);
    }

    @Override
    public void playZoneCreationsTextAdded ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.CREATION_TYPE, OBAnalytics.Params.CREATION_TYPE_TEXT);
        //
        logEvent(OBAnalytics.Event.PLAY_ZONE, parameters);
    }



    @Override
    public void nightModeEntered ()
    {
        Map<String, Object> parameters = new HashMap();
        parameters.put(OBAnalytics.Params.APP_MODE_CHANGE, OBAnalytics.Params.APP_NIGHT_MODE);
    }
}
