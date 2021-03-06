package com.github.hakko.musiccabinet.domain.model.library;

import com.github.hakko.musiccabinet.domain.model.music.Album;
import com.github.hakko.musiccabinet.domain.model.music.Artist;
import com.github.hakko.musiccabinet.domain.model.music.Track;

public class WebserviceInvocation {

	/*
	 * This enum matches one-to-one with database table library.webservice_calltype on id.
	 */
	public enum Calltype { 
		GET_SCROBBLED_TRACKS	(0, 1), 
		ARTIST_GET_SIMILAR		(1, 10), 
		ARTIST_GET_TOP_TRACKS	(2, 10),
		TRACK_GET_SIMILAR		(3, 10),
		ARTIST_GET_TOP_TAGS		(4, 10),
		ARTIST_GET_INFO			(5, 15),
		ALBUM_GET_INFO			(6, 15),
		USER_GET_TOP_ARTISTS	(7, 7);
		
		private final int databaseId;
		private final int daysToCache;
		
		private Calltype(int databaseId, int daysToCache) {
			this.databaseId = databaseId;
			this.daysToCache = daysToCache;
		}
		
		public int getDatabaseId() {
			return databaseId;
		}
		
		public int getDaysToCache() {
			return daysToCache;
		}
	}

	private Artist artist;
	private Album album;
	private Track track;
	private LastFmUser user;
	private Calltype callType;
	private Short page;
	
	public WebserviceInvocation(Calltype callType, short page) {
		this.callType = callType;
		this.page = page;
	}
	
	public WebserviceInvocation(Calltype callType, Artist artist) {
		if (artist == null) {
			throw new IllegalArgumentException("Cannot create WebserviceInvocation for artist null!");
		}
		this.callType = callType;
		this.artist = artist;
	}
	
	public WebserviceInvocation(Calltype callType, Album album) {
		if (album == null) {
			throw new IllegalArgumentException("Cannot create WebserviceInvocation for album null!");
		}
		this.callType = callType;
		this.album = album;
	}
	
	public WebserviceInvocation(Calltype callType, Track track) {
		if (track == null) {
			throw new IllegalArgumentException("Cannot create WebserviceInvocation for track null!");
		}
		this.callType = callType;
		this.track = track;
	}
	
	public WebserviceInvocation(Calltype callType, LastFmUser user, short days) {
		if (user == null) {
			throw new IllegalArgumentException("Cannot create WebserviceInvocation for user null!");
		}
		this.callType = callType;
		this.user = user;
		this.page = days;
	}

	public Artist getArtist() {
		return artist;
	}
	
	public Album getAlbum() {
		return album;
	}

	public Track getTrack() {
		return track;
	}
	
	public LastFmUser getUser() {
		return user;
	}

	public Calltype getCallType() {
		return callType;
	}

	public Short getPage() {
		return page;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("type: " + callType.name());
		if (artist != null) {
			sb.append(", " + artist);
		}
		if (album != null) {
			sb.append(", " + album);
		}
		if (track != null) {
			sb.append(", " + track);
		}
		if (user != null) {
			sb.append(", " + user);
		}
		if (page != null) {
			sb.append(", page " + page);
		}
		return sb.toString();
	}
	
}