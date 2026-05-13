package com.lifetool.media;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MediaAssetStore {

    private final Map<String, MediaAsset> byId = new ConcurrentHashMap<>();

    public MediaAsset save(MediaAsset asset) {
        byId.put(asset.getId(), asset);
        return asset;
    }

    public Optional<MediaAsset> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
