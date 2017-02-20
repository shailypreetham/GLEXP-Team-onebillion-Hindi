package org.onebillion.onecourse.mainui;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.View;

import org.onebillion.onecourse.controls.OBControl;
import org.onebillion.onecourse.controls.OBGroup;
import org.onebillion.onecourse.controls.OBImage;
import org.onebillion.onecourse.controls.OBLabel;
import org.onebillion.onecourse.controls.OBTextLayer;
import org.onebillion.onecourse.controls.OBVideoPlayer;
import org.onebillion.onecourse.glstuff.OBRenderer;
import org.onebillion.onecourse.mainui.generic.OC_Generic;
import org.onebillion.onecourse.utils.OBAnim;
import org.onebillion.onecourse.utils.OBAnimationGroup;
import org.onebillion.onecourse.utils.OBImageManager;
import org.onebillion.onecourse.utils.OBSystemsManager;
import org.onebillion.onecourse.utils.OBUtils;
import org.onebillion.onecourse.utils.OBXMLManager;
import org.onebillion.onecourse.utils.OBXMLNode;
import org.onebillion.onecourse.utils.OB_Maths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by pedroloureiro on 07/02/2017.
 */

public class OC_VideoPlayback extends OC_SectionController
{
    private int subtitleIndex;
    private List<OBXMLNode> subtitleList;
    private float subtitleTextSize;
    private Runnable syncSubtitlesRunnable;
    private Handler handler;
    //
    private Map<String, OBXMLNode> videoXMLDict;
    private List<String> videoPlaylist;
    private int currentVideoIndex;
    //
    private List<OBControl> videoPreviewImages;
    private OBGroup videoPreviewGroup;
    //
    private String currentTab = null;
    private static Typeface plain, bold, italic, boldItalic;
    private float maximumX, minimumX;
    private PointF lastPoint = new PointF();
    private PointF lastLastPoint = new PointF();
    private PointF firstPoint = new PointF();
    private long lastMoveEvent, lastlastMoveEvent;
    private int videoPreviewIdx = 0;
    private int videoScrollState;
    private int intro_video_state = 0;
    private final static int ivs_act_normal = 0,
            ivs_before_play = 1,
            ivs_playing_full_screen = 2;
    private final static int VIDEO_SCROLL_NONE = 0,
            VIDEO_SCROLL_TOUCH_DOWNED = 1,
            VIDEO_SCROLL_MOVED = 2;
    private PointF videoTouchDownPoint = new PointF();
    private OBVideoPlayer videoPlayer;
    private String movieFolder;
    private boolean slowingDown;
    private boolean inited = false;

    private static Typeface plainFont ()
    {
        if (plain == null)
            plain = Typeface.createFromAsset(MainActivity.mainActivity.getAssets(), "fonts/F37Ginger-Regular.otf");
        return plain;
    }

    private static Typeface boldFont ()
    {
        if (bold == null)
            bold = Typeface.createFromAsset(MainActivity.mainActivity.getAssets(), "fonts/F37Ginger-Bold.otf");
        return bold;
    }

    private static Typeface italicFont ()
    {
        if (italic == null)
            italic = Typeface.createFromAsset(MainActivity.mainActivity.getAssets(), "fonts/F37Ginger-Italic.otf");
        return italic;
    }

    private static Typeface boldItalicFont ()
    {
        if (boldItalic == null)
            boldItalic = Typeface.createFromAsset(MainActivity.mainActivity.getAssets(), "fonts/F37Ginger-BoldItalic.otf");
        return boldItalic;
    }


    public int buttonFlags ()
    {
        return OBMainViewController.SHOW_TOP_LEFT_BUTTON;
    }


    public void prepare ()
    {
        super.prepare();
        loadEvent("mastera");
        subtitleTextSize = applyGraphicScale(Float.parseFloat(eventAttributes.get("subtitletextsize")));
        loadPlaylist();
        loadVideoThumbnails();
        //
        handler = new Handler();
        //
        syncSubtitlesRunnable = new Runnable()
        {
            @Override
            public void run ()
            {
                if (subtitleIndex >= subtitleList.size())
                {
                    MainActivity.log("OC_VideoPlayback:syncSubtitlesRunnable:reached the end of the subtitles.");
                    return;
                }
                //
                OBXMLNode subtitleNode = subtitleList.get(subtitleIndex);
                int start = subtitleNode.attributeIntValue("start");
                int currentPosition = videoPlayer == null ? -1 : videoPlayer.currentPosition();
                //
                int excess = start - currentPosition;
                if (currentPosition == -1) excess = 10;
                //
                if (excess > 0)
                {
                    OBSystemsManager.sharedManager.mainHandler.removeCallbacks(syncSubtitlesRunnable);
                    OBSystemsManager.sharedManager.mainHandler.postDelayed(syncSubtitlesRunnable, excess);
                }
                else
                {
                    OBUtils.runOnOtherThread(new OBUtils.RunLambda()
                    {
                        @Override
                        public void run () throws Exception
                        {
                            showSubtitle(currentVideoIndex);
                        }
                    });
                }
            }
        };
    }


    public void exitEvent()
    {
        MainActivity.log("OC_VideoPlayback:exitEvent");
        //
        super.exitEvent();
        //
        videoPlayer.stop();
    }


    private void playNextVideo ()
    {
        currentVideoIndex++;
        if (currentVideoIndex >= videoPlaylist.size())
        {
            MainActivity.log("OC_VideoPlayback:playNextVideo:reached the end of the playlist");
            return;
        }
        //
        scrollPreviewToVisible(currentVideoIndex, true);
        selectPreview(currentVideoIndex);
        setUpVideoPlayerForIndex(currentVideoIndex, true);
    }



    private void clearSubtitle ()
    {
        OBUtils.runOnMainThread(new OBUtils.RunLambda()
        {
            @Override
            public void run () throws Exception
            {
                OBLabel oldLabel = (OBLabel) objectDict.get("subtitle");
                objectDict.remove("subtitle");
                //
                if (oldLabel != null)
                {
                    lockScreen();
                    detachControl(oldLabel);
                    unlockScreen();
                }
            }
        });
    }


    private void showSubtitle (int videoIndex)
    {
        if (currentVideoIndex != videoIndex)
        {
            MainActivity.log("OC_VideoPLayback:showSubtitle:mismatch of video index and current video index in scene. aborting");
            return;
        }
//        MainActivity.log("OC_VideoPlayback:showSubtitle");
        OBControl textbox = objectDict.get("video_textbox");
        //
        if (textbox != null)
        {
            clearSubtitle();
            //
            if (subtitleIndex >= subtitleList.size())
            {
                MainActivity.log("OC_VideoPlayback:showSubtitle:reached the end of the subtitle for this video");
                return;
            }
            //
            OBXMLNode subtitleNode = subtitleList.get(subtitleIndex);
            //
            String text = subtitleNode.attributeStringValue("text");
            int waitPeriod = 0;
            if (text.startsWith("#"))
            {
                String components[] = text.split("#");
                waitPeriod = Integer.parseInt(components[1]);
                text = components[2];
            }
            final OBLabel label = new OBLabel(text, plainFont(), subtitleTextSize);
            label.setMaxWidth(textbox.width());
            label.setJustification(OBTextLayer.JUST_LEFT);
            label.setLineSpaceMultiplier(1.2f);
            label.sizeToBoundingBox();
            label.setPosition(new PointF(textbox.position().x, textbox.position().y));
            //
            label.setZPosition(videoPreviewGroup.zPosition());
            label.setColour(Color.BLACK);
//                     label.setBorderColor(Color.BLACK);
//                     label.setBorderWidth(2f);
            objectDict.put("subtitle", label);
            //
            MainActivity.log("OC_VideoPlayback:showSubtitle:attaching new subtitle");
            //
            OBUtils.runOnMainThread(new OBUtils.RunLambda()
            {
                @Override
                public void run () throws Exception
                {
                    lockScreen();
                    attachControl(label);
                    unlockScreen();
                }
            });
            //
            if (waitPeriod > 0)
            {
                setStatus(STATUS_WAITING_FOR_RESUME);
                videoPlayer.pause();
                try
                {
                    Thread.sleep(waitPeriod * 1000);
                }
                catch (Exception e)
                {
                    // nothing to do here
                }
                setStatus(STATUS_IDLE);
                videoPlayer.start();
            }
            //
            int end = subtitleNode.attributeIntValue("end");
            int currentPosition = videoPlayer == null ? -1 : videoPlayer.currentPosition();
            //
            while (currentPosition < end)
            {
                int excess = end - currentPosition;
                if (currentPosition == -1) excess = 10;
                //
//                        MainActivity.log("OC_VideoPlayback:showSubtitle:waiting for subtitle:" + excess);
                //
                try
                {
                    Thread.sleep(excess);
                }
                catch (Exception e)
                {
                    // nothing to do here
                }
                //
                currentPosition = videoPlayer == null ? -1 : videoPlayer.currentPosition();
            }
            //
            clearSubtitle();
            subtitleIndex++;
            //
            OBSystemsManager.sharedManager.mainHandler.removeCallbacks(syncSubtitlesRunnable);
            OBSystemsManager.sharedManager.mainHandler.post(syncSubtitlesRunnable);
        }
    }


    private void loadSubtitles (int idx)
    {
        subtitleIndex = 0;
        subtitleList = new ArrayList();
        //
        String videoID = videoPlaylist.get(idx);
        String srtPath = getLocalPath(String.format("%s.srt", videoID));
        //
        if (srtPath == null)
        {
            MainActivity.log("OC_VideoPlayback:loadSubtitles: couldn't find subtitles for video " + videoID);
            return;
        }
        //
        try
        {
            Scanner scanner = new Scanner(OBUtils.getInputStreamForPath(srtPath));
            String line;

            while (scanner.hasNextLine())
            {
                // index
                String index = scanner.nextLine();
                //
                // time stamp
                line = scanner.nextLine();
                String[] timestamps = line.split(" --> ");
                if (timestamps.length != 2)
                {
                    MainActivity.log("OC_VideoPlayback:loadSubtitles --> unable to find 2 timestamps in line: " + line);
                    MainActivity.log("OC_VideoPlayback:loadSubtitles.Unable to continue loading subtitles");
                    return;
                }
                //
                String[] topParts = timestamps[0].split(",");
                if (topParts.length != 2)
                {
                    MainActivity.log("OC_VideoPlayback:loadSubtitles --> unable to find 2 components separated by , in timestamp: " + timestamps[0]);
                    MainActivity.log("OC_VideoPlayback:loadSubtitles.Unable to continue loading subtitles");
                    return;
                }
                String[] parts = topParts[0].split(":");
                if (parts.length != 3)
                {
                    MainActivity.log("OC_VideoPlayback:loadSubtitles --> unable to find 3 components separated by : in timestamp: " + timestamps[0]);
                    MainActivity.log("OC_VideoPlayback:loadSubtitles.Unable to continue loading subtitles");
                    return;
                }
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                int milliseconds = Integer.parseInt(topParts[1]);
                //
                long subStart = milliseconds + seconds * 1000 + minutes * 60 * 1000 + hours * 60 * 60 * 1000;
                //
                topParts = timestamps[1].split(",");
                if (topParts.length != 2)
                {
                    MainActivity.log("OC_VideoPlayback:loadSubtitles --> unable to find 2 components separated by , in timestamp: " + timestamps[1]);
                    MainActivity.log("OC_VideoPlayback:loadSubtitles.Unable to continue loading subtitles");
                    return;
                }
                parts = topParts[0].split(":");
                if (parts.length != 3)
                {
                    MainActivity.log("OC_VideoPlayback:loadSubtitles --> unable to find 3 components separated by : in timestamp: " + timestamps[1]);
                    MainActivity.log("OC_VideoPlayback:loadSubtitles.Unable to continue loading subtitles");
                    return;
                }
                hours = Integer.parseInt(parts[0]);
                minutes = Integer.parseInt(parts[1]);
                seconds = Integer.parseInt(parts[2]);
                milliseconds = Integer.parseInt(topParts[1]);
                //
                long subEnd = milliseconds + seconds * 1000 + minutes * 60 * 1000 + hours * 60 * 60 * 1000;
                //
                // text
                String text = "";
                line = scanner.nextLine();
                while (!line.equals(""))
                {
                    text = text + ((text.length() > 0) ? System.lineSeparator() : "") + line;
                    //
                    if (!scanner.hasNextLine()) break;
                    //
                    line = scanner.nextLine();
                }
                //
                OBXMLNode subtitleNode = new OBXMLNode();
                subtitleNode.attributes.put("index", index);
                subtitleNode.attributes.put("text", text);
                subtitleNode.attributes.put("start", String.valueOf(subStart));
                subtitleNode.attributes.put("end", String.valueOf(subEnd));
                //
                subtitleList.add(subtitleNode);
            }
        }
        catch (Exception e)
        {
            MainActivity.log("OC_VideoPlayback:loadSubtitles:exception caught while reading srt " + srtPath);
            e.printStackTrace();
        }
    }


    private void loadPlaylist ()
    {
        videoPlaylist = new ArrayList<>();
        videoXMLDict = new HashMap<>();
        String xmlPath = getConfigPath("playlist.xml");
        try
        {
            OBXMLManager xmlManager = new OBXMLManager();
            List<OBXMLNode> xl = xmlManager.parseFile(OBUtils.getInputStreamForPath(xmlPath));
            OBXMLNode xmlNode = xl.get(0);
            List<OBXMLNode> videoXMLs = xmlNode.childrenOfType("video");
            for (OBXMLNode n : videoXMLs)
            {
                String key = n.attributeStringValue("id");
                videoXMLDict.put(key, n);
                videoPlaylist.add(key);
            }
        }
        catch (Exception e)
        {
            MainActivity.log("OC_VideoPlayback:loadPlaylist:exception caught while loading playlist.xml");
            e.printStackTrace();
        }
    }


    private void loadVideoThumbnails ()
    {
        OBControl vs = objectDict.get("video_selector");
        int col = vs.fillColor();
        deleteControls("video_selector");
        OBControl p1 = objectDict.get("video_preview1");
        OBControl p2 = objectDict.get("video_preview2");
        float videoPreviewX = p1.position().x;
        float videoPreviewTopY = p1.position().y;
        float videoPreviewXOffset = p2.position().x - p1.position().x;
        float videoPreviewHeight = p1.height();
        float videoPreviewWidth = p1.width();
        int idx = 0;
        float zpos = OC_Generic.getNextZPosition(this);
        //
        videoPreviewImages = new ArrayList<>();
        List<OBControl> lstgp = new ArrayList<>();
        for (String videoID : videoPlaylist)
        {
            OBXMLNode videoNode = videoXMLDict.get(videoID);
            String frame = videoNode.attributeStringValue("frame");
            //
            OBImage im = loadImageWithName(frame, new PointF(), new RectF(), false);
            if (movieFolder == null)
            {
                String f = OBImageManager.sharedImageManager().getImgPath(videoID);
                f = OBUtils.stringByDeletingLastPathComponent(f);
                f = OBUtils.stringByDeletingLastPathComponent(f);
                movieFolder = OBUtils.stringByAppendingPathComponent(f, "movies");
            }
            videoPreviewImages.add(im);
            im.setPosition(videoPreviewX + idx * videoPreviewXOffset, videoPreviewTopY);
            im.setScale(videoPreviewHeight / im.height());
            im.setZPosition(5);
            im.setHighlightColour(Color.argb(80, 0, 0, 0));
            //
            idx++;
        }
        lstgp.addAll(videoPreviewImages);
        //
        OBControl selector = new OBControl();
        selector.setBackgroundColor(col);
        selector.setFrame(videoPreviewImages.get(0).frame());
        selector.setZPosition(1);
        objectDict.put("video_preview_selector", selector);
        lstgp.add(selector);
        //
        OBControl mask = objectDict.get("video_mask");
        OBControl mc = mask.copy();
        lstgp.add(mc);
        RectF r = OBGroup.frameUnion(lstgp);
        r.bottom += applyGraphicScale(24);
        videoPreviewGroup = new OBGroup(lstgp, r);
        videoPreviewGroup.removeMember(mc);
        attachControl(videoPreviewGroup);
        videoPreviewGroup.setZPosition(zpos);
        objectDict.put("videoPreviewGroup", videoPreviewGroup);
        detachControl(mask);
        videoPreviewGroup.setScreenMaskControl(mask);
        maximumX = videoPreviewGroup.position().x;
        minimumX = maximumX - (videoPreviewGroup.right() - mask.right());
        //
        selectPreview(videoPreviewIdx);
    }


    private void videoinit ()
    {
        OBRenderer rn = MainActivity.mainActivity.renderer;
        while (rn.colourProgram == null)
        {
            try
            {
                waitForSecs(0.1);
            }
            catch (Exception e)
            {
                MainActivity.log("OC_VideoPlayback:videoinit:exception caught");
                e.printStackTrace();
            }
        }
        OBUtils.runOnMainThread(new OBUtils.RunLambda()
        {
            @Override
            public void run () throws Exception
            {
                switchTo("video", false);
//                showMessage();
            }
        });
    }

    public void start ()
    {
        super.start();
        setStatus(STATUS_IDLE);
        //blankTextureID(2);
        if (videoPlayer != null)
            videoPlayer.frameIsAvailable = false;
        if (!inited)
        {
            OBUtils.runOnOtherThread(new OBUtils.RunLambda()
            {
                @Override
                public void run () throws Exception
                {
                    videoinit();
                }
            });
            inited = true;
        }
    }


    private void selectPreview (int i)
    {
        videoPreviewIdx = i;
        OBControl selector = objectDict.get("video_preview_selector");
        OBControl pim = videoPreviewImages.get(videoPreviewIdx);
        RectF f = new RectF();
        f.set(pim.frame());
        float amt = applyGraphicScale(-4);
        f.inset(amt, amt);
        selector.setFrame(f);
    }


    private void goSmallScreen ()
    {
        lockScreen();
        OBControl placeHolder = objectDict.get("video_video");
        videoPlayer.setFillType(OBVideoPlayer.VP_FILL_TYPE_ASPECT_FILL);
        videoPlayer.setFrame(placeHolder.frame());
        unlockScreen();
    }

    private void setUpVideoPlayerForIndex (final int idx, boolean play)
    {
        if (idx == 0 && !play)
            intro_video_state = ivs_before_play;
        else
            intro_video_state = ivs_act_normal;
        //
        currentVideoIndex = idx;
        //
        String videoID = videoPlaylist.get(idx);
        OBXMLNode videoNode = videoXMLDict.get(videoID);
        String movie = videoNode.attributeStringValue("file");
        //
        String movieName = OBUtils.stringByAppendingPathComponent(movieFolder, movie);
        OBControl placeHolder = objectDict.get("video_video");
        //
        loadSubtitles(idx);
        //
        lockScreen();
        if (videoPlayer != null)
        {
            detachControl(videoPlayer);
            videoPlayer = null;
        }
        if (videoPlayer == null)
        {
            RectF r = new RectF();
            r.set(placeHolder.frame());
            r.left = (int) r.left;
            r.top = (int) r.top;
            r.right = (float) Math.ceil(r.right);
            r.bottom = (float) Math.ceil(r.bottom);
            placeHolder.setFrame(r);
            videoPlayer = new OBVideoPlayer(r, this, false, false);
            videoPlayer.stopOnCompletion = false;
            videoPlayer.setZPosition(190);
            videoPlayer.setFillType(OBVideoPlayer.VP_FILL_TYPE_ASPECT_FILL);
            //
            attachControl(videoPlayer);
        }
        else
        {
            if (!attachedControls.contains(videoPlayer))
                attachControl(videoPlayer);
            videoPlayer.stop();
            goSmallScreen();
        }
        unlockScreen();
        videoPlayer.playAfterPrepare = play;
        videoPlayer.startPlayingAtTime(OBUtils.getAssetFileDescriptorForPath(movieName), 0);
        //
        videoPlayer.player.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion (MediaPlayer mp)
            {
                MainActivity.log("OC_VideoPlayback.onCompletionListener");
                playNextVideo();
            }
        });
        //
        clearSubtitle();
        subtitleIndex = 0;
        //
        OBSystemsManager.sharedManager.mainHandler.removeCallbacks(syncSubtitlesRunnable);
        OBSystemsManager.sharedManager.mainHandler.post(syncSubtitlesRunnable);
    }




    private void populateVideo ()
    {
        setUpVideoPlayerForIndex(videoPreviewIdx, false);
        scrollPreviewToVisible(videoPreviewIdx, false);
    }


    private void switchTo (String s, boolean force)
    {
        lockScreen();
        if (videoPlayer != null)
        {
            videoPlayer.stop();
            detachControl(videoPlayer);
        }
        loadEvent(s);
        populateVideo();
        unlockScreen();
    }

    private void scrollPreviewToVisible (int idx, boolean animate)
    {
        OBControl preview = videoPreviewImages.get(idx);
        OBControl mask = objectDict.get("video_mask");
        RectF maskFrame = convertRectToControl(mask.frame(), videoPreviewGroup);
        float diff = 0;
        if (preview.top() < maskFrame.top)
        {
            float requiredX = 2 * preview.width() + maskFrame.left;
            diff = requiredX - preview.position().x;
        }
        else if (preview.right() > maskFrame.right)
        {
            float requiredY = maskFrame.bottom - 2 * preview.height();
            diff = requiredY - preview.position().y;
        }
        if (diff == 0)
        {
            return;
        }
        float newX = videoPreviewGroup.position().x + diff;
        if (newX > maximumX)
        {
            newX = maximumX;
        }
        else if (newX < minimumX)
        {
            newX = minimumX;
        }
        if (newX != videoPreviewGroup.position().x)
        {
            if (animate)
            {
                PointF pt = new PointF(newX, videoPreviewGroup.position().y);
                OBAnim anim = OBAnim.moveAnim(pt, videoPreviewGroup);
                OBAnimationGroup grp = new OBAnimationGroup();
                registerAnimationGroup(grp, "videoscrollanim");
                grp.applyAnimations(Collections.singletonList(anim), 0.4, false, OBAnim.ANIM_EASE_IN_EASE_OUT, this);
            }
            else
                videoPreviewGroup.setPosition(newX, videoPreviewGroup.position().y);
        }

    }

    public void touchUpAtPoint (PointF pto, View v)
    {
        // add time threshold to prevent maniacal tapping
        //
        if (status() == STATUS_WAITING_FOR_RESUME)
        {
            MainActivity.log("OC_VideoPlayback:touchUpAtPoint:status is waiting for resume: skipping further processing");
            return;
        }
        if (status() == 0)
        {
            return;
        }
        if (videoScrollState > 0)
        {
            boolean mustSelect = videoScrollState != VIDEO_SCROLL_MOVED;
            videoScrollState = VIDEO_SCROLL_NONE;
            if (mustSelect)
            {
                for (int i = 0; i < videoPreviewImages.size(); i++)
                {
                    OBControl im = videoPreviewImages.get(i);
                    RectF f = convertRectFromControl(im.bounds(), im);
                    if (f.contains(pto.x, pto.y))
                    {
                        if (videoPlayer != null)
                        {
                            videoPlayer.stop();
                        }
                        selectPreview(i);
                        scrollPreviewToVisible(i, true);
                        setStatus(STATUS_IDLE);
                        setUpVideoPlayerForIndex(i, true);
                        return;
                    }
                }
            }
            else
            {
                float dist = lastPoint.x - lastLastPoint.x;
                float time = (lastMoveEvent - lastlastMoveEvent) / 1000.0f;
                final float speed = dist / time;
                OBUtils.runOnOtherThread(new OBUtils.RunLambda()
                {
                    @Override
                    public void run () throws Exception
                    {
                        slowDown(speed, videoPreviewGroup);
                    }
                });
            }
        }
        setStatus(STATUS_IDLE);
    }

    private void slowDown (float xSpeed, OBControl group)
    {
        slowingDown = true;
        try
        {
            while (slowingDown)
            {
                if (Math.abs(xSpeed) < 1)
                {
                    slowingDown = false;
                    return;
                }
                xSpeed *= 0.925;
                float dist = xSpeed * 0.02f;
                float x = group.position().x;
                x += dist;
                boolean fin = false;
                if (x > maximumX)
                {
                    x = maximumX;
                    fin = true;
                }
                else if (x < minimumX)
                {
                    x = minimumX;
                    fin = true;
                }
                group.setPosition(x, group.position().y);
                if (fin)
                    slowingDown = false;
                waitForSecs(0.02);
            }
        }
        catch (Exception e)
        {
            slowingDown = false;
        }
    }

    public void touchMovedToPoint (PointF pt, View v)
    {
        if (status() == STATUS_WAITING_FOR_RESUME)
        {
            MainActivity.log("OC_VideoPlayback:touchMovedToPoint:status is waiting for resume: skipping further processing");
            return;
        }
        if (status() == 0)
        {
            MainActivity.log("OC_VideoPlayback:touchMovedToPoint:status 0");
            return;
        }
        //
        if (videoScrollState == VIDEO_SCROLL_TOUCH_DOWNED && OB_Maths.PointDistance(videoTouchDownPoint, pt) > applyGraphicScale(8))
        {
            videoScrollState = VIDEO_SCROLL_MOVED;
        }
        if (videoScrollState > 0)
        {
            float dx = pt.x - lastPoint.x;
            float newX = videoPreviewGroup.position().x + dx;
            if (newX <= maximumX && newX >= minimumX)
            {
                videoPreviewGroup.setPosition(newX, videoPreviewGroup.position().y);
            }
            lastLastPoint.x = lastPoint.x;
            lastlastMoveEvent = lastMoveEvent;
            lastPoint.x = pt.x;
            lastMoveEvent = System.currentTimeMillis();
        }
    }

    private void handleVideoPress (PointF pt)
    {
        if (intro_video_state == ivs_before_play)
        {
            goSmallScreen();
            videoPlayer.start();
            intro_video_state = ivs_playing_full_screen;
            return;
        }
        if (videoPlayer.isPlaying())
            videoPlayer.pause();
        else
            videoPlayer.start();
    }

    private void processVideoTouch (PointF pt)
    {
        videoScrollState = VIDEO_SCROLL_NONE;
        RectF f = videoPreviewGroup.frame();
        if (f.contains(pt.x, pt.y))
        {
            videoScrollState = VIDEO_SCROLL_TOUCH_DOWNED;
            videoTouchDownPoint.set(pt);
        }
        else
        {
            if (videoPlayer != null && videoPlayer.frame().contains(pt.x, pt.y))
            {
                handleVideoPress(pt);
            }
        }
    }


    public void touchDownAtPoint (PointF pt, View v)
    {
        if (status() == STATUS_WAITING_FOR_RESUME)
        {
            MainActivity.log("OC_VideoPlayback:touchDownAtPoint:status is waiting for resume: skipping further processing");
            return;
        }
        //
        lastPoint.set(pt);
        lastLastPoint.set(pt);
        firstPoint.set(pt);
        slowingDown = false;
        //
        if (status() != STATUS_IDLE)
        {
            return;
        }
        //
        lastPoint.set(pt);
        processVideoTouch(pt);
    }


    public void onResume ()
    {
        videoPlayer.onResume();
        if (currentTab.equals("video") && videoPreviewIdx >= 0)
        {
            setUpVideoPlayerForIndex(videoPreviewIdx, false);
        }
        super.onResume();
    }

    @Override
    public void onPause ()
    {
        super.onPause();
        try
        {
            videoPlayer.onPause();

        }
        catch (Exception e)
        {
            MainActivity.log("OC_VideoPlayback:onPause:exception caught");
            e.printStackTrace();
        }

    }
}
