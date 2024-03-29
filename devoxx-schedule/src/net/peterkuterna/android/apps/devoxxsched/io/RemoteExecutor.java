/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified by Peter Kuterna to handle JSON responses instead of XML responses.
 */

package net.peterkuterna.android.apps.devoxxsched.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import net.peterkuterna.android.apps.devoxxsched.io.JSONHandler.JSONHandlerException;
import net.peterkuterna.android.apps.devoxxsched.model.RequestHash;
import net.peterkuterna.android.apps.devoxxsched.util.Lists;
import net.peterkuterna.android.apps.devoxxsched.util.SyncUtils;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.ContentResolver;
import android.util.Log;


/**
 * Executes an {@link HttpUriRequest} and passes the result as an
 * {@link JSONArray} to the given {@link JSONHandler}.
 */
public class RemoteExecutor {
    private final HttpClient mHttpClient;
    private final ContentResolver mResolver;

    public RemoteExecutor(HttpClient httpClient, ContentResolver resolver) {
        mHttpClient = httpClient;
        mResolver = resolver;
    }

    /**
     * Execute a {@link HttpGet} request, passing a valid response through
     * {@link JSONHandler#parseAndApply(JSONArray, ContentResolver)}.
     */
    public ArrayList<RequestHash> executeGet(String [] urls, JSONHandler handler) throws JSONHandlerException {
    	final ArrayList<RequestHash> result = Lists.newArrayList();
    	final ArrayList<JSONArray> entries = Lists.newArrayList();
    	for (String url : urls) {
            final HttpUriRequest request = new HttpGet(url);
            final String md5 = SyncUtils.getRemoteMd5(mHttpClient, url);
            JSONArray requestEntries = executeRequest(request);
            entries.add(requestEntries);
            result.add(new RequestHash(url, md5));
    	}
    	handler.setLocalSync(false);
		handler.parseAndApply(entries, mResolver);
    	return result;
    }

    /**
     * Execute this {@link HttpUriRequest}, passing a valid response through
     * {@link JSONHandler#parseAndApply(JSONArray, ContentResolver)}.
     */
    public JSONArray executeRequest(HttpUriRequest request) throws JSONHandlerException {
        try {
            final HttpResponse resp = mHttpClient.execute(request);
            final int status = resp.getStatusLine().getStatusCode();
            Log.d("RemoteExecutor", "status = " + status);
            if (status != HttpStatus.SC_OK) {
                throw new JSONHandlerException("Unexpected server response " + resp.getStatusLine()
                        + " for " + request.getRequestLine());
            }

            final InputStream input = resp.getEntity().getContent();
            try {
            	BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            	StringBuilder sb = new StringBuilder();
            	String line;
            	while ((line = reader.readLine()) != null) {
            		sb.append(line);
            	}
                String jsontext = sb.toString();
                return new JSONArray(jsontext);
            } catch (JSONException e) {
                throw new JSONHandlerException("Malformed response for " + request.getRequestLine(), e);
            } finally {
                if (input != null) input.close();
            }
        } catch (JSONHandlerException e) {
            throw e;
        } catch (IOException e) {
            throw new JSONHandlerException("Problem reading remote response for "
                    + request.getRequestLine(), e);
        }
    }
    
}
