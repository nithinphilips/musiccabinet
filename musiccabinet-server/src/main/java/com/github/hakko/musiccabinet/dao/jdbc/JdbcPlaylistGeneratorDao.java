package com.github.hakko.musiccabinet.dao.jdbc;

import static com.github.hakko.musiccabinet.dao.util.PostgreSQLUtil.getParameters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.github.hakko.musiccabinet.dao.PlaylistGeneratorDao;
import com.github.hakko.musiccabinet.domain.model.aggr.PlaylistItem;

/*
 * 
 */
public class JdbcPlaylistGeneratorDao implements PlaylistGeneratorDao, JdbcTemplateDao {

	private JdbcTemplate jdbcTemplate;
	
	@Override
	public List<Integer> getTopTracksForArtist(int artistId, int totalCount) {
		String sql = "select t.id from library.track t"
			+ " inner join library.artisttoptrackplaycount att on att.track_id = t.id" 
			+ " where att.artist_id = " + artistId
			+ " order by rank limit " + totalCount;
		
		return jdbcTemplate.queryForList(sql, Integer.class);
	}
	
	/*
	 * Return a list of tracks, related to a given track.
	 * Pre-condition: music.trackrelation is populated for given track.
	 * 
	 * Strategy:
	 * 
	 * Select the top 25 highest ranked tracks that we have in library.
	 * 
	 * TODO : possible improvement: a view of trackrelation * library.track 
	 * 
	 */
	@Override
	public List<PlaylistItem> getPlaylistForTrack(int trackId) {
		String sql = "select distinct on (t.id, tr.weight) a.id, lt.id" 
			+ " from music.trackrelation tr"
			+ " inner join music.track t on t.id = tr.target_id"
			+ " inner join music.artist a on a.id = t.artist_id"
			+ " inner join library.track lt on lt.track_id = t.id"
			+ " where tr.source_id = " + trackId 
			+ " order by tr.weight desc, t.id"
			+ " limit 25";

		return jdbcTemplate.query(sql, new RowMapper<PlaylistItem>() {
			@Override
			public PlaylistItem mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				return new PlaylistItem(rs.getInt(1), rs.getInt(2));
			}
		});
	}

	/*
	 * Return a list of tracks, related to a given artist.
	 * Pre-condition: music.artistrelation and music.artisttoptrack are populated.
	 * 
	 * Strategy:
	 * 
	 * (waiting for proper built-in postgresql support of materialized views...)
	 * 
	 * Use pre-calculated table library.artisttoptrackplaycount, that holds the
	 * match of music.artisttoptrack * music.artistrelation * library.track * 
	 * library.trackplaycount (ids only).
	 * 
	 * Using this, pick the artists that will be eligible for being part of the final
	 * playlist, joined with their top tracks that we have a matching music file for.
	 * 
	 * Sort the resulting tracks by global track popularity + personal play count +
	 * some randomness, and then pick top (n) rows from each artist.
	 * 
	 * Sort the remaining tracks by artist relevance +  a bit of randomness again,
	 * and pick top (m) rows that will go into the final playlist.
	 * 
	 * Intention: relevant artists appear more often (but not necessarily all the time).
	 * One single artist can never have more than (n) tracks in a given playlist.
	 * (unless the random() function very very unlikely makes rank() return double rows) 
	 * Popular tracks for chosen artists appear more often, but less famous tracks might
	 * climb the ranks every now and then.
	 * 
	 * No assumptions can be made on order of returned tracks.
	 * 
	 */
	@Override
	public List<PlaylistItem> getPlaylistForArtist(int artistId, int artistCount, int totalCount) {
		String sql = "select artist_id, track_id from ("
			+ "  select att.track_id, att.artist_id, ar.weight as artist_weight, rank() over" 
			+ "  (partition by att.artist_id order by (random()*(110 - rank + (play_count/3))) desc) as artist_rank from library.artisttoptrackplaycount att"
			+ "   inner join (select source_id, target_id, weight from music.artistrelation union all select " + artistId + ", " + artistId + ", 1) ar" 
			+ "	    on ar.target_id = att.artist_id and ar.source_id = " + artistId
			+ "  ) ranked_tracks"
			+ "  where ranked_tracks.artist_rank <= " + artistCount
			+ "  order by random() * ranked_tracks.artist_weight * ranked_tracks.artist_weight desc limit " + totalCount;

		return jdbcTemplate.query(sql, new RowMapper<PlaylistItem>() {
			@Override
			public PlaylistItem mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				return new PlaylistItem(rs.getInt(1), rs.getInt(2));
			}
		});
	}

	@Override
	public List<PlaylistItem> getPlaylistForTags(String[] tags, int artistCount, int totalCount) {
		if (tags == null || tags.length == 0) {
			throw new IllegalArgumentException("At least one tag must be specified!");
		}

		String sql = "select artist_id, track_id from ("
				+ "	  select att.track_id, att.artist_id, tag.tag_count as tag_weight, rank() over" 
				+ "	  (partition by att.artist_id order by (random()*(110 - rank + (play_count/3))) desc) as artist_rank from library.artisttoptrackplaycount att"
				+ "	   inner join (select toptag.artist_id, sum(tag_count) as tag_count from music.artisttoptag toptag"
				+ "			inner join music.tag tag on toptag.tag_id = tag.id"
				+ "			where tag.tag_name in (" + getParameters(tags.length) + ")"
				+ "			group by toptag.artist_id) tag"
				+ "		on tag.artist_id = att.artist_id"
				+ "	  ) ranked_tracks"
				+ "	  where ranked_tracks.artist_rank <= " + artistCount
				+ "	  order by random() * ranked_tracks.tag_weight * ranked_tracks.tag_weight desc"
				+ " limit " + totalCount;

		return jdbcTemplate.query(sql, tags, new RowMapper<PlaylistItem>() {
			@Override
			public PlaylistItem mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				return new PlaylistItem(rs.getInt(1), rs.getInt(2));
			}
		});
	}

	@Override
	public void updateSearchIndex() { // TODO : evaluate time consumed + optimize
		String deleteSql = 
				"truncate library.artisttoptrackplaycount";
		
		String insertSql =
				"insert into library.artisttoptrackplaycount (track_id, artist_id, rank, play_count)"
				+ " select distinct on (lt.track_id) lt.id, att.artist_id, att.rank, coalesce(tpc.play_count, 0)"
				+ " from music.artisttoptrack att"
				+ " inner join library.track lt on lt.track_id = att.track_id"
				+ " left outer join library.trackplaycount tpc on tpc.track_id = lt.track_id";
		
		jdbcTemplate.execute(deleteSql);
		jdbcTemplate.execute(insertSql);
	}

	@Override
	public boolean isSearchIndexCreated() {
		String sql = "select 1 from library.artisttoptrackplaycount limit 1";

		boolean indexCreated;
		try {
			indexCreated = jdbcTemplate.queryForInt(sql) > 0;
		} catch (DataAccessException e) {
			indexCreated = false; // no rdbms, no credentials, no schema...
		}
		return indexCreated;
	}
	
	@Override
	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	// Spring setters
	
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

}