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


import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * AsyncTask that converts an AppRTC room URL into the set of signaling
 * parameters to use with that room.
 */
public class RoomParametersFetcher {
  private static final String TAG = "RoomParametersFetcher";

  private final WebSocket wsClient;


  public RoomParametersFetcher(WebSocket wsClient) {
    this.wsClient = wsClient;
  }

  public void makeRequest() {
      JSONObject json = new JSONObject();
      WebSocketRTCClient.jsonPut(json, "id", "appConfig");
      wsClient.sendText(json.toString());
      Log.d(TAG, "made json request " + json.toString()+" ws:"+wsClient.getState().name());
  }



  // Requests & returns a TURN ICE Server based on a request URL.  Must be run
  // off the main thread!
  private void requestTurnServers()
      throws IOException, JSONException {
      Log.d(TAG, "Request TURN from websocket: ");
      JSONObject json = new JSONObject();
      WebSocketRTCClient.jsonPut(json, "id", "turn");
      wsClient.sendText(json.toString());
  }



  // Return the contents of an InputStream as a String.
  private static String drainStream(InputStream in) {
    Scanner s = new Scanner(in).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

}
