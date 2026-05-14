package com.lifetool.media;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcMediaAssetStore implements MediaAssetStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcMediaAssetStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public MediaAsset save(MediaAsset asset) {
        String sql = """
                INSERT INTO media_assets (id, user_id, object_key, content_type, purpose,
                  file_size, width, height, status, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::text, ?, ?, ?, 'uploaded', ?, now())
                ON CONFLICT (id) DO UPDATE SET
                  status = EXCLUDED.status,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, asset.getId());
            stmt.setString(2, asset.getUserId());
            stmt.setString(3, asset.getObjectKey());
            stmt.setString(4, asset.getContentType());
            stmt.setString(5, asset.getPurpose());
            stmt.setLong(6, asset.getFileSize());
            if (asset.getWidth() != null) {
                stmt.setInt(7, asset.getWidth());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            if (asset.getHeight() != null) {
                stmt.setInt(8, asset.getHeight());
            } else {
                stmt.setNull(8, Types.INTEGER);
            }
            stmt.setTimestamp(9, Timestamp.from(asset.getCreatedAt()));
            stmt.executeUpdate();
            return asset;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save media asset", ex);
        }
    }

    @Override
    public Optional<MediaAsset> findById(String id) {
        String sql = """
                SELECT id, user_id, object_key, content_type, purpose,
                  file_size, width, height, status, created_at
                FROM media_assets
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int w = rs.getInt("width");
                int h = rs.getInt("height");
                MediaAsset asset = new MediaAsset(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("object_key"),
                        rs.getString("content_type"),
                        rs.getString("purpose"),
                        rs.getLong("file_size"),
                        rs.wasNull() ? null : w,
                        rs.wasNull() ? null : h);
                if ("deleted".equals(rs.getString("status"))) {
                    asset.markDeleted();
                }
                return Optional.of(asset);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find media asset", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
