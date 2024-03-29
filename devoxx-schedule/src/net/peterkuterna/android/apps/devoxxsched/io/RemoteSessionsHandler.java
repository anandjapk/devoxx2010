/*
 * Copyright 2010 Peter Kuterna
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

package net.peterkuterna.android.apps.devoxxsched.io;

import static net.peterkuterna.android.apps.devoxxsched.util.ParserUtils.sanitizeId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleContract;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleContract.SearchSuggest;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleContract.Sessions;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleContract.Speakers;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleContract.Tags;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleContract.Tracks;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleDatabase.SessionsSpeakers;
import net.peterkuterna.android.apps.devoxxsched.provider.ScheduleDatabase.SessionsTags;
import net.peterkuterna.android.apps.devoxxsched.util.Lists;
import net.peterkuterna.android.apps.devoxxsched.util.Maps;
import net.peterkuterna.android.apps.devoxxsched.util.ParserUtils;
import net.peterkuterna.android.apps.devoxxsched.util.Sets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.SearchManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;


/**
 * Handle a remote {@link JSONArray} that defines a set of {@link Sessions}
 * entries.
 */
public class RemoteSessionsHandler extends JSONHandler {
	
    private static final String TAG = "SessionsHandler";

    private static final String TRACK_JAVA_CORE = "javacoreseee";
    private static final String TRACK_WEB_FRAMEWORKS = "webframeworks";
    private static final String TRACK_DESKTOP_RIA_MOBILE = "desktopriamobile";
    private static final String TRACK_NEW_JVM_LANG = "newlanguagesonthejvm";
    private static final String TRACK_METHODOLOGY = "methodology";
    private static final String TRACK_ARCHI_SEC = "architecturesecurity";
    private static final String TRACK_CLOUD_NOSQL = "cloudnosql";
    private static final String TRACK_OTHER = "other";

    private static final String COLOR_JAVA_CORE = "#FF2A5699";
    private static final String COLOR_WEB_FRAMEWORKS = "#FFFFCC00";
    private static final String COLOR_DESKTOP_RIA_MOBILE = "#FFFF2222";
    private static final String COLOR_NEW_JVM_LANG = "#FF0FABFF";
    private static final String COLOR_METHODOLOGY = "#FFA0CE67";
    private static final String COLOR_ARCHI_SEC = "#FFEEB211";
    private static final String COLOR_CLOUD_NOSQL = "#FF0066CC";
    private static final String COLOR_OTHER = "#FFBF0000";
    private static final String COLOR_DEFAULT = "#FF272526";
    
    public RemoteSessionsHandler() {
		super(ScheduleContract.CONTENT_AUTHORITY);
	}

	@Override
	public ArrayList<ContentProviderOperation> parse(ArrayList<JSONArray> entries, ContentResolver resolver) throws JSONException {
		final ArrayList<ContentProviderOperation> batch = Lists.newArrayList();
		final HashSet<String> sessionIds = Sets.newHashSet();
		final HashSet<String> trackIds = Sets.newHashSet();
		final HashMap<String, HashSet<String>> sessionSpeakerIds = Maps.newHashMap();
		final HashMap<String, HashSet<String>> sessionTagIds = Maps.newHashMap();
		
		int nrEntries = 0;
		for (JSONArray sessions : entries) {
			Log.d(TAG, "Retrieved " + sessions.length() + " presentation entries.");
			nrEntries += sessions.length();
	
	        for (int i=0; i < sessions.length(); i++) {
	            JSONObject session = sessions.getJSONObject(i);
	            String id = session.getString("id");
	            
	            final String sessionId = sanitizeId(id);
	            final Uri sessionUri = Sessions.buildSessionUri(sessionId);
	            sessionIds.add(sessionId);
	            int isStarred = isStarred(sessionUri, resolver);
	
	            boolean sessionUpdated = false;
	            boolean newSession = false;
	            ContentProviderOperation.Builder builder;
	            if (isRowExisting(sessionUri, SessionsQuery.PROJECTION, resolver)) {
	            	builder = ContentProviderOperation.newUpdate(sessionUri);
	            	builder.withValue(Sessions.NEW, false);
	        		sessionUpdated = isSessionUpdated(sessionUri, session, resolver);
	    			if (isRemoteSync()) {
	            		builder.withValue(Sessions.UPDATED, sessionUpdated);
	            	}
	            } else {
	            	newSession = true;
		            builder = ContentProviderOperation.newInsert(Sessions.CONTENT_URI);
		            builder.withValue(Sessions.SESSION_ID, sessionId);
		            if (!isLocalSync()) {
		            	builder.withValue(Sessions.NEW, true);
		            }
	            }
	            
			    final String type = session.getString("type");
			    if (newSession || sessionUpdated) {
				    builder.withValue(Sessions.TITLE, session.getString("title"));
				    builder.withValue(Sessions.EXPERIENCE, session.getString("experience"));
				    builder.withValue(Sessions.TYPE, type);
				    builder.withValue(Sessions.SUMMARY, session.getString("summary"));
				    builder.withValue(Sessions.STARRED, isStarred);
	            }
			    builder.withValue(Sessions.TYPE_ID, getTypeId(type));
			    
	        	batch.add(builder.build());
			    
			    if (session.has("track")) {
			    	final String trackName = session.getString("track");
			    	final String trackId = Tracks.generateTrackId(trackName);
			    	final Uri trackUri = Tracks.buildTrackUri(trackId);
	
				    if (!trackIds.contains(trackId)) {
				    	trackIds.add(trackId);
				    	
			            ContentProviderOperation.Builder trackBuilder;
			            if (isRowExisting(Tracks.buildTrackUri(trackId), TracksQuery.PROJECTION, resolver)) {
			            	trackBuilder = ContentProviderOperation.newUpdate(trackUri);
			            } else {
			            	trackBuilder = ContentProviderOperation.newInsert(Tracks.CONTENT_URI);
			            	trackBuilder.withValue(Tracks.TRACK_ID, trackId);
			            }
	
		                trackBuilder.withValue(Tracks.TRACK_NAME, trackName);
		                final int color = Color.parseColor(getTrackColor(trackId));
		                trackBuilder.withValue(Tracks.TRACK_COLOR, color);
		    		    batch.add(trackBuilder.build());
				    }
				    
		            if (newSession || sessionUpdated) {
					    builder.withValue(Sessions.TRACK_ID, trackId);
		            }
			    }
			    
			    if (session.has("speakers")) {
				    final Uri speakerSessionsUri = Sessions.buildSpeakersDirUri(sessionId);
			    	final JSONArray speakers = session.getJSONArray("speakers");
					final HashSet<String> speakerIds = Sets.newHashSet();
			    	
			    	if (!isLocalSync()) {
	            		final boolean sessionSpeakersUpdated = isSessionSpeakersUpdated(speakerSessionsUri, speakers, resolver);
			    		if (sessionSpeakersUpdated) {
				    		Log.d(TAG, "Speakers of session with id " + sessionId + " was udpated.");
				    		batch.add(ContentProviderOperation.newUpdate(sessionUri)
				    				.withValue(Sessions.UPDATED, true)
				    				.build());
			    		}
			    	}
			    	
			    	for (int j = 0; j < speakers.length(); j++) {
			    		JSONObject speaker = speakers.getJSONObject(j);
			    		
		            	final Uri speakerUri = Uri.parse(speaker.getString("speakerUri"));
		            	final String speakerId = speakerUri.getLastPathSegment();
		            	speakerIds.add(speakerId);
	
				    	batch.add(ContentProviderOperation.newInsert(speakerSessionsUri)
				    			.withValue(SessionsSpeakers.SPEAKER_ID, speakerId)
				    			.withValue(SessionsSpeakers.SESSION_ID, sessionId).build());
			    	}
			    	
			    	sessionSpeakerIds.put(sessionId, speakerIds);
			    }
			    
			    if (session.has("tags")) {
				    final Uri tagSessionsUri = Sessions.buildTagsDirUri(sessionId);
			    	final JSONArray tags = session.getJSONArray("tags");
			    	final HashSet<String> tagIds = Sets.newHashSet();
			    	
			    	for (int j = 0; j < tags.length(); j++) {
			    		JSONObject tag = tags.getJSONObject(j);
			    		final String tagName = tag.getString("name").toLowerCase();
			    		final String tagId = Tags.generateTagId(tagName);
			    		tagIds.add(tagId);
			    		
			            batch.add(ContentProviderOperation.newInsert(Tags.CONTENT_URI)
					            .withValue(Tags.TAG_ID, tagId)
			            		.withValue(Tags.TAG_NAME, tagName).build());
			    		
	                    batch.add(ContentProviderOperation.newInsert(SearchSuggest.CONTENT_URI)
	                            .withValue(SearchManager.SUGGEST_COLUMN_TEXT_1, tagName).build());

	                    batch.add(ContentProviderOperation.newInsert(tagSessionsUri)
			    				.withValue(SessionsTags.TAG_ID, tagId)
			    				.withValue(SessionsTags.SESSION_ID, sessionId).build());
			    	}

			    	sessionTagIds.put(sessionId, tagIds);
			    }
	        }
		}
        
        if (isRemoteSync() && nrEntries > 0) {
        	for (Entry<String, HashSet<String>> entry : sessionSpeakerIds.entrySet()) {
        		String sessionId = entry.getKey();
        		HashSet<String> speakerIds = entry.getValue();
			    final Uri speakerSessionsUri = Sessions.buildSpeakersDirUri(sessionId);
    	    	HashSet<String> lostSpeakerIds = getLostIds(speakerIds, speakerSessionsUri, SpeakersQuery.PROJECTION, SpeakersQuery.SPEAKER_ID, resolver);
            	for (String lostSpeakerId : lostSpeakerIds) {
	        		final Uri deleteUri = Sessions.buildSessionSpeakerUri(sessionId, lostSpeakerId);
			    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
            	}
        	}

        	for (Entry<String, HashSet<String>> entry : sessionTagIds.entrySet()) {
        		String sessionId = entry.getKey();
        		HashSet<String> tagIds = entry.getValue();
			    final Uri tagSessionsUri = Sessions.buildTagsDirUri(sessionId);
    	    	HashSet<String> lostTagIds = getLostIds(tagIds, tagSessionsUri, TagsQuery.PROJECTION, TagsQuery.TAG_ID, resolver);
            	for (String lostTagId : lostTagIds) {
	        		final Uri deleteUri = Sessions.buildSessionTagUri(sessionId, lostTagId);
			    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
            	}
        	}

        	HashSet<String> lostTrackIds = getLostIds(trackIds, Tracks.CONTENT_URI, TracksQuery.PROJECTION, TracksQuery.TRACK_ID, resolver);
        	for (String lostTrackId : lostTrackIds) {
        		Uri deleteUri = Tracks.buildSessionsUri(lostTrackId);
		    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
		    	deleteUri = Tracks.buildTrackUri(lostTrackId);
		    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
        	}
        	HashSet<String> lostSessionIds = getLostIds(sessionIds, Sessions.CONTENT_URI, SessionsQuery.PROJECTION, SessionsQuery.SESSION_ID, resolver);
        	for (String lostSessionId : lostSessionIds) {
		    	Uri deleteUri = Sessions.buildSpeakersDirUri(lostSessionId);
		    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
		    	deleteUri = Sessions.buildTagsDirUri(lostSessionId);
		    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
		    	deleteUri = Sessions.buildSessionUri(lostSessionId);
		    	batch.add(ContentProviderOperation.newDelete(deleteUri).build());
        	}
        }
        
        return batch;
	}
	
	private static final String getTypeId(String type) {
		if (type != null) {
			String typeId = type;
			int lastIndex = typeId.lastIndexOf(" (");
			if (lastIndex != -1) {
				typeId = typeId.substring(0, lastIndex);
			}
			return ParserUtils.sanitizeId(typeId.replaceAll(" ", "_"));
		}
		return null;
	}

	private static final String getTrackColor(String trackId) {
		if (TRACK_ARCHI_SEC.equalsIgnoreCase(trackId)) {
			return COLOR_ARCHI_SEC;
		} else if (TRACK_CLOUD_NOSQL.equalsIgnoreCase(trackId)) {
			return COLOR_CLOUD_NOSQL;
		} else if (TRACK_DESKTOP_RIA_MOBILE.equalsIgnoreCase(trackId)) {
			return COLOR_DESKTOP_RIA_MOBILE;
		} else if (TRACK_JAVA_CORE.equalsIgnoreCase(trackId)) {
			return COLOR_JAVA_CORE;
		} else if (TRACK_METHODOLOGY.equalsIgnoreCase(trackId)) {
			return COLOR_METHODOLOGY;
		} else if (TRACK_NEW_JVM_LANG.equalsIgnoreCase(trackId)) {
			return COLOR_NEW_JVM_LANG;
		} else if (TRACK_OTHER.equalsIgnoreCase(trackId)) {
			return COLOR_OTHER;
		} else if (TRACK_WEB_FRAMEWORKS.equalsIgnoreCase(trackId)) {
			return COLOR_WEB_FRAMEWORKS;
		} else {
			return COLOR_DEFAULT;
		}
	}
	
	private static int isStarred(Uri uri, ContentResolver resolver) {
        final Cursor cursor = resolver.query(uri, SessionsQuery.PROJECTION, null, null, null);
        int starred = 0;
        try {
            if (cursor.moveToFirst()) {
            	starred = cursor.getInt(SessionsQuery.STARRED);
            }
        } finally {
            cursor.close();
        }
        return starred;
    }
	
	private static boolean isSessionUpdated(Uri uri, JSONObject session, ContentResolver resolver) throws JSONException {
        final Cursor cursor = resolver.query(uri, SessionsQuery.PROJECTION, null, null, null);
        try {
            if (!cursor.moveToFirst()) return false;

            final String curTitle = cursor.getString(SessionsQuery.TITLE).toLowerCase().trim();
        	final String curSummary = cursor.getString(SessionsQuery.SUMMARY).toLowerCase().trim();
        	final String curExperience = cursor.getString(SessionsQuery.EXPERIENCE).toLowerCase().trim();
        	final String curType = cursor.getString(SessionsQuery.TYPE).toLowerCase().trim();
        	final String newTitle = session.getString("title").toLowerCase().trim();
        	final String newSummary = session.getString("summary").toLowerCase().trim();
        	final String newExperience = session.getString("experience").toLowerCase().trim();
        	final String newType = session.getString("type").toLowerCase().trim();
        	
        	return (!curTitle.equals(newTitle)
        			|| !curSummary.equals(newSummary)
        			|| !curExperience.equals(newExperience)
        			|| !curType.equals(newType));
        } finally {
            cursor.close();
        }
	}

	private static boolean isSessionSpeakersUpdated(Uri uri, JSONArray speakers, ContentResolver resolver) throws JSONException {
        final Cursor cursor = resolver.query(uri, SpeakersQuery.PROJECTION, null, null, null);
        try {
            if (!cursor.moveToFirst()) return false;
            return cursor.getCount() != speakers.length();
        } finally {
            cursor.close();
        }
	}

    private interface SessionsQuery {
        String[] PROJECTION = {
        		Sessions.SESSION_ID,
        		Sessions.TITLE,
        		Sessions.SUMMARY,
        		Sessions.EXPERIENCE,
        		Sessions.TYPE,
        		Sessions.TYPE_ID,
                Sessions.STARRED,
        };

        int SESSION_ID = 0;
        int TITLE = 1;
        int SUMMARY = 2;
        int EXPERIENCE = 3;
        int TYPE = 4;
        int STARRED = 5;
    }

    private interface SpeakersQuery {
        String[] PROJECTION = {
        		Speakers.SPEAKER_ID,
        };

        int SPEAKER_ID = 0;
    }

    private interface TagsQuery {
        String[] PROJECTION = {
        		Tags.TAG_ID,
        };

        int TAG_ID = 0;
    }

    private interface TracksQuery {
        String[] PROJECTION = {
                Tracks.TRACK_ID,
        };

        int TRACK_ID = 0;
    }

}
