package com.github.hakko.musiccabinet.dao.jdbc;

import static com.github.hakko.musiccabinet.domain.model.library.WebserviceInvocation.Calltype.ARTIST_GET_INFO;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.BatchSqlUpdate;

import com.github.hakko.musiccabinet.dao.ArtistInfoDao;
import com.github.hakko.musiccabinet.domain.model.music.Artist;
import com.github.hakko.musiccabinet.domain.model.music.ArtistInfo;
import com.github.hakko.musiccabinet.log.Logger;

public class JdbcArtistInfoDao implements ArtistInfoDao, JdbcTemplateDao {

	private JdbcTemplate jdbcTemplate;

	private static final Logger LOG = Logger.getLogger(JdbcArtistInfoDao.class);
	
	@Override
	public void createArtistInfo(List<ArtistInfo> artistInfos) {
		if (artistInfos.size() > 0) {
			clearImportTable();
			batchInsert(artistInfos);
			updateLibrary();
		}
	}
	
	private void clearImportTable() {
		jdbcTemplate.execute("delete from music.artistinfo_import");
	}
	
	private void batchInsert(List<ArtistInfo> artistInfos) {
		String sql = "insert into music.artistinfo_import (artist_name, smallImageUrl, mediumImageUrl, largeImageUrl, extraLargeImageUrl, listeners, playcount, biosummary, biocontent) values (?,?,?,?,?,?,?,?,?)";
		BatchSqlUpdate batchUpdate = new BatchSqlUpdate(jdbcTemplate.getDataSource(), sql);
		batchUpdate.setBatchSize(1000);
		batchUpdate.declareParameter(new SqlParameter("artist_name", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("smallImageUrl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("mediumImageUrl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("largeImageUrl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("extraLargeImageUrl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("listeners", Types.INTEGER));
		batchUpdate.declareParameter(new SqlParameter("playcount", Types.INTEGER));
		batchUpdate.declareParameter(new SqlParameter("biosummary", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("biocontent", Types.VARCHAR));
		
		for (ArtistInfo ai : artistInfos) {
			batchUpdate.update(new Object[]{ai.getArtist().getName(),
					ai.getSmallImageUrl(), ai.getMediumImageUrl(), ai.getLargeImageUrl(),
					ai.getExtraLargeImageUrl(), ai.getListeners(), ai.getPlayCount(),
					ai.getBioSummary(), ai.getBioContent()});
		}
		batchUpdate.flush();

	}

	private void updateLibrary() {
		jdbcTemplate.execute("select music.update_artistinfo_from_import()");
	}

	@Override
	public ArtistInfo getArtistInfo(int artistId) {
		String sql = 
				"select ai.largeImageUrl, ai.biosummary from music.artistinfo ai" + 
				" where ai.artist_id = " + artistId;
		ArtistInfo artistInfo = null;
		
		try {
			artistInfo = jdbcTemplate.queryForObject(sql, 
					new RowMapper<ArtistInfo>() {
				@Override
				public ArtistInfo mapRow(ResultSet rs, int rowNum)
						throws SQLException {
					ArtistInfo ai = new ArtistInfo();
					ai.setLargeImageUrl(rs.getString(1));
					ai.setBioSummary(rs.getString(2));
					return ai;
				}

			});
		} catch (DataAccessException e) {
			LOG.warn("There's no artist info for artist " + artistId, e);
		}

		return artistInfo;
	}
	
	@Override
	public ArtistInfo getArtistInfo(final Artist artist) {
		String sql = 
				"select ai.smallImageUrl, ai.mediumImageUrl, ai.largeImageUrl, ai.extraLargeImageUrl, ai.listeners, ai.playcount, ai.biosummary, ai.biocontent from music.artistinfo ai" + 
				" inner join music.artist a on ai.artist_id = a.id" +
				" where a.artist_name = upper(?)";
		ArtistInfo artistInfo = jdbcTemplate.queryForObject(sql, new Object[]{artist.getName()}, 
				new RowMapper<ArtistInfo>() {
			@Override
			public ArtistInfo mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				ArtistInfo ai = new ArtistInfo();
				ai.setArtist(artist);
				ai.setSmallImageUrl(rs.getString(1));
				ai.setMediumImageUrl(rs.getString(2));
				ai.setLargeImageUrl(rs.getString(3));
				ai.setExtraLargeImageUrl(rs.getString(4));
				ai.setListeners(rs.getInt(5));
				ai.setPlayCount(rs.getInt(6));
				ai.setBioSummary(rs.getString(7));
				ai.setBioContent(rs.getString(8));
				return ai;
			}
			
		});

		return artistInfo;
	}

	@Override
	public List<Artist> getArtistsWithoutInfo() {
		String sql = "select distinct a.artist_name_capitalization from"
				+ " library.musicfile mf"
				+ " inner join music.track t on mf.track_id = t.id"
				+ " inner join music.artist a on t.artist_id = a.id"
				+ " where not exists ("
				+ " select 1 from music.artistinfo where artist_id = a.id)"
				+ " and not exists ("
				+ " select 1 from library.webservice_history where artist_id = a.id "
				+ " and calltype_id = " + ARTIST_GET_INFO.getDatabaseId() + ")";

		List<Artist> artists = jdbcTemplate.query(sql, new RowMapper<Artist>() {
			@Override
			public Artist mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new Artist(rs.getString(1));
			}
		});

		return artists;
	}

	@Override
	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}
	
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

}