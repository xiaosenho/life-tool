package com.lifetool.media;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryMediaAssetStore implements MediaAssetStore {

    private final Map<String, MediaAsset> byId = new ConcurrentHashMap<>();

    @Override
    public MediaAsset save(MediaAsset asset) {
        byId.put(asset.getId(), asset);
        return asset;
    }

    @Override
    public Optional<MediaAsset> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
