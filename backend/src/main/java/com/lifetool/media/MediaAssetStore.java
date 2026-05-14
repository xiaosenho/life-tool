package com.lifetool.media;

import java.util.Optional;

public interface MediaAssetStore {
    MediaAsset save(MediaAsset asset);

    Optional<MediaAsset> findById(String id);
}
