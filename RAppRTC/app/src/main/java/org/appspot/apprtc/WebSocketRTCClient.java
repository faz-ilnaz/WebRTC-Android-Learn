/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketConnectionState;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.appspot.apprtc.util.LooperExecutor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 * <p>
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class WebSocketRTCClient implements AppRTCClient, WebSocketChannelEvents {
    private static final String TAG = "WSRTCClient";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private enum ConnectionState {NEW, CONNECTED, CLOSED, ERROR}

    private enum MessageType {MESSAGE, LEAVE}

    public static boolean initiator;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;

    private final LooperExecutor executor;

    public WebSocketRTCClient(SignalingEvents events) {
        this.events = events;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        executor = new LooperExecutor();
        executor.requestStart();
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    connectToRoomInternal();
                } catch (Exception e) {
                    reportError("WebSocketerror: " + e.toString());
                }
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        //events.onDisconnectedFromRoom();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                executor.requestStop();
            }
        });
    }

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(executor, this, connectionParameters.roomId);

        wsClient.connect(connectionUrl);
        wsClient.setState(WebSocketConnectionState.CONNECTED);
        Log.d(TAG, "wsClient connect " + connectionUrl);

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("turn:numb.viagenie.ca", "louis@mozilla.com", "webrtcdemo"));
        SignalingParameters signalingParameters = new SignalingParameters(iceServers, true, "57889279", connectionUrl, "https://apprtc-ws-2.webrtc.org:443", null, null);


        // register WebSocket client
        wsClient.register(connectionParameters.roomId, signalingParameters.clientId);

        // Fire connection and signaling parameters events.
        events.onConnectedToRoom(signalingParameters);
        //Set room state to CONNECTED after triggereing event
        roomState = ConnectionState.CONNECTED;
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);

        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            wsClient.send("{\"signal\":\"left\"}");
        }
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }

    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
        return connectionParameters.roomUrl + "/signaling";
    }


    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp, final String to) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "offerResponse sent to " + to);
                JSONObject json = new JSONObject();
                jsonPut(json, "to", to);
                jsonPut(json, "from", "");
                jsonPut(json, "signal", "offerResponse");
                jsonPut(json, "content", sdp.description);
                wsClient.send(json.toString());
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "answerResponse sent to " + PeerConnectionClient.to);
                JSONObject json = new JSONObject();
                jsonPut(json, "signal", "answerResponse");
                jsonPut(json, "to", PeerConnectionClient.to);
                jsonPut(json, "from", "");
                jsonPut(json, "content", sdp.description);
                wsClient.send(json.toString());
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "signal", "candidate");
                jsonPut(json, "candidate", candidate.sdp);
                jsonPut(json, "to", PeerConnectionClient.to);
                jsonPut(json, "from", "");
                if (initiator) {
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(json.toString());
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(json.toString());
                }
            }
        });
    }

    // --------------------------------------------------------------------
    // WebSocketChannelEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        Log.d(TAG, msg);

        try {
            JSONObject json = new JSONObject(msg);
            String signal = json.getString("signal");

            if (signal.equals("ping"))
                return;

            if (signal.equals("created") || (signal.equals("joined"))) {
                this.wsClient.setState(WebSocketConnectionState.REGISTERED);
            }
            if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {
                Log.e(TAG, "Got WebSocket message in non registered state.");
                return;
            }

            SessionDescription sdp = null;

            if (signal.length() > 0) {
                switch (signal) {
                    case "created":
                        initiator = true;
                        PeerConnectionClient.clientID = json.getString("to");
                        this.wsClient.setState(WebSocketConnectionState.REGISTERED);
                        break;
                    case "joined":
                        initiator = false;
                        PeerConnectionClient.clientID = json.getString("to");
                        this.wsClient.setState(WebSocketConnectionState.REGISTERED);
                        break;
                    case "newJoined":
                        Log.i(TAG, "New users has joined");
                        break;
                    case "offerRequest":
                        Log.i(TAG, "Offer request come from " + json.getString("from"));
                        events.onOfferRequest(json.getString("from"));
                        break;
                    case "finalize":
                        Log.i(TAG, "Connection finalize btw: from " + json.getString("from") + ", to " + json.getString("to"));
                        sdp = new SessionDescription(
                                SessionDescription.Type.ANSWER, json.getString("content"));
                        events.onRemoteDescription(sdp);
                        break;
                    case "candidate":
                        events.onRemoteIceCandidate(toJavaCandidate(new JSONObject(json.getString("content"))));
                        break;
                    case "remove-candidates":
                        JSONArray candidateArray = json.getJSONArray("candidates");
                        IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                        for (int i = 0; i < candidateArray.length(); ++i) {
                            candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                        }
                        events.onRemoteIceCandidatesRemoved(candidates);
                        break;
                    case "answerRequest":
                        if (!initiator) {
                            sdp = new SessionDescription(
                                    SessionDescription.Type.OFFER, json.getString("content"));
                            events.onAnswerRequest(sdp, json.getString("from"));
                        } else {
                            reportError("Received answer for call initiator: " + msg);
                        }
                        break;
                    case "left":
                        events.onChannelClose();
                        break;
                    default:
                        reportError("Unexpected WebSocket message: " + msg);
                        break;
                }
            } else {
                reportError("Unexpected WebSocket message: " + msg);
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Send SDP or ICE candidate to a room server.
    private void sendPostMessage(
            final MessageType messageType, final String url, final String message) {
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        Log.d(TAG, "C->GAE: " + logInfo);
        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("POST", url, message, new AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        reportError("GAE POST error: " + errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        if (messageType == MessageType.MESSAGE) {
                            try {
                                JSONObject roomJson = new JSONObject(response);
                                String result = roomJson.getString("result");
                                if (!result.equals("SUCCESS")) {
                                    reportError("GAE POST error: " + result);
                                }
                            } catch (JSONException e) {
                                reportError("GAE POST JSON error: " + e.toString());
                            }
                        }
                    }
                });
        httpConnection.send();
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"));
    }
}
