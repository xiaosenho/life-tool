import { memo } from "react";
import { ImageStyle, StyleProp } from "react-native";
import { Image as ExpoImage } from "expo-image";

type CachedAvatarProps = {
  uri: string;
  cacheKey?: string | null;
  style: StyleProp<ImageStyle>;
  onError?: () => void;
};

function CachedAvatarComponent({ uri, cacheKey, style, onError }: CachedAvatarProps) {
  return (
    <ExpoImage
      source={{
        uri,
        cacheKey: cacheKey ?? uri
      }}
      style={style}
      contentFit="cover"
      cachePolicy="memory-disk"
      transition={0}
      onError={onError}
    />
  );
}

export const CachedAvatar = memo(CachedAvatarComponent);
