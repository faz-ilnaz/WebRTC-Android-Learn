package org.appspot.apprtc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.util.Log;
import android.widget.Toast;

import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

import java.util.ArrayList;

//import de.lespace.apprtc.service.SignalingService;


public abstract class RTCConnection extends Activity implements
        PeerConnectionClient.PeerConnectionEvents {


    public final static String EXTRA_INITIATOR = "de.lespace.mscwebrtc.INITIATOR";
    public final static String EXTRA_TO = "de.lespace.mscwebrtc.TO";
    public static final String EXTRA_FROM = "de.lespace.mscwebrtc.FROM";
    public static final String EXTRA_VIDEO_CALL = "de.lespace.mscwebrtc.VIDEO_CALL";
    public static final String EXTRA_VIDEO_WIDTH = "de.lespace.mscwebrtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT = "de.lespace.mscwebrtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS = "de.lespace.mscwebrtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED = "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
    public static final String EXTRA_VIDEO_BITRATE = "de.lespace.mscwebrtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC = "de.lespace.mscwebrtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED = "de.lespace.mscwebrtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "de.lespace.mscwebrtc.CAPTURETOTEXTURE";
    public static final String EXTRA_AUDIO_BITRATE = "de.lespace.mscwebrtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "de.lespace.mscwebrtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED = "de.lespace.mscwebrtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED = "de.lespace.mscwebrtc.AECDUMP";
    public static final String EXTRA_OPENSLES_ENABLED = "de.lespace.mscwebrtc.OPENSLES";
    public static final String EXTRA_DISPLAY_HUD = "de.lespace.mscwebrtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "de.lespace.mscwebrtc.TRACING";
    public static final String EXTRA_RUNTIME = "de.lespace.mscwebrtc.RUNTIME";
    public static final String EXTRA_MSC_REGISTRATION = "de.lespace.mscwebrtc.MSC_REGISTRATION";
    public static final String EXTRA_SIGNALING_REGISTRATION = "de.lespace.mscwebrtc.SIGNALING_REGISTRATION";
    public static final int CONNECTION_REQUEST = 1;

    public static PeerConnectionClient peerConnectionClient = null;
    public static PeerConnectionClient peerConnectionClient2 = null;
    public static boolean callActive = false;
    public static boolean online = false;

    //"ConnectionParamters"
    public static String wssUrl;
    public static String from;
    public static String to;
    public static boolean initiator;

    public Toast logToast;
    public long callStartedTimeMs = 0;
    private static final String TAG = "RTCConnection";
    public boolean iceConnected;
    public boolean isError;
    public static SharedPreferences sharedPref;

    public int runTimeMs;
    public boolean activityRunning;


    public RendererCommon.ScalingType scalingType;
    public boolean callControlFragmentVisible = true;

    public static ArrayList<String> userList;
    public static Ringtone r;

    // Peer connection statistics callback period in ms.
    public static final int STAT_CALLBACK_PERIOD = 1000;

    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;

   // public static AppRTCClient.RoomConnectionParameters roomConnectionParameters;
    public static PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    public static AppRTCClient.SignalingParameters signalingParam;

    public static boolean doToast = false;


    public RTCConnection(){

    }

    // Log |msg| and Toast about it.
    public void logAndToast(String msg) {
        Log.d(TAG, msg);
        if(doToast){
            if (logToast != null) {
                logToast.cancel();
            }
            logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
            logToast.show();
        }

    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.

    private void onConnectedToRoomInternal(final AppRTCClient.SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        signalingParam = params;
    }

    public void onChannelError(final String description){
        logAndToast(description);
    }

    // Activity interfaces
    @Override
    public void onPause() {
        super.onPause();
        activityRunning = false;
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
       // activityRunning = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
    }

    public boolean validateUrl(String url) {
        //if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
        if (isWSUrl(url) || isWSSUrl(url)) {
            return true;
        }

        new AlertDialog.Builder(this)
                .setTitle(getText(R.string.invalid_url_title))
                .setMessage(getString(R.string.invalid_url_text, url))
                .setCancelable(false)
                .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                }).create().show();
        return false;
    }


    public static boolean isWSUrl(String url) {
        return (null != url) &&
                (url.length() > 4) &&
                url.substring(0, 5).equalsIgnoreCase("ws://");
    }


    /**PeerConnectinoEvents**/
    @Override
    public void onPeerConnectionClosed() {
    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {

    }
    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    @Override
    public void onLocalDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                if (SignalingService.appRTCClient != null) {
//
//                    boolean isScreenSharingConnection = (peerConnectionClient2!=null);
//                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
//
//                    if (initiator && !isScreenSharingConnection) {
//                        SignalingService.appRTCClient.call(sdp);
//                    } else {
//                        SignalingService.appRTCClient.sendOfferSdp(sdp,isScreenSharingConnection);
//                    }
//                }
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                if (SignalingService.appRTCClient != null) {
//                    SignalingService.appRTCClient.sendLocalIceCandidate(candidate,(peerConnectionClient2!=null));
//                }
            }
        });
    }
    @Override
    public void onIceConnected() {

    }

    @Override
    public void onIceDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
                iceConnected = false;
                disconnect(false);
            }
        });
    }

    @Override
    public void onPeerConnectionError(final String description) {
        reportError(description);
    }

    public void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (!activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect(true);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getText(R.string.channel_error_title))
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            disconnect(true);
                        }
                    }).create().show();
        }
    }


    // Disconnect from remote resources, dispose of local resources, and exit.
    public void disconnect(boolean sendRemoteHangup) {
        Intent intent = new Intent("finish_CallActivity");
        sendBroadcast(intent);
    }

    /**
     * @return True iff the url is an https: url.
     */
    public static boolean isWSSUrl(String url) {
        return (null != url) &&
                (url.length() > 5) &&
                url.substring(0, 6).equalsIgnoreCase("wss://");
    }

}
