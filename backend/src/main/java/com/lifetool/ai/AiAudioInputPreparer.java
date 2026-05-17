package com.lifetool.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiAudioInputPreparer {
    private static final Logger log = LoggerFactory.getLogger(AiAudioInputPreparer.class);

    public record PreparedAudio(byte[] bytes, String format) {}

    public PreparedAudio prepare(byte[] sourceBytes, String contentType) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalStateException("Audio bytes are empty");
        }
        if (looksLikeWav(sourceBytes)) {
            return new PreparedAudio(sourceBytes, "wav");
        }
        if (looksLikeMp3(sourceBytes)) {
            return new PreparedAudio(sourceBytes, "mp3");
        }
        return new PreparedAudio(transcodeToMp3(sourceBytes, contentType), "mp3");
    }

    private byte[] transcodeToMp3(byte[] sourceBytes, String contentType) {
        String inputExtension = extensionFor(contentType);
        Path inputPath = null;
        Path outputPath = null;
        try {
            inputPath = Files.createTempFile("lifetool-ai-audio-", inputExtension);
            outputPath = Files.createTempFile("lifetool-ai-audio-", ".mp3");
            Files.write(inputPath, sourceBytes);

            Process process = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", inputPath.toString(),
                    "-vn",
                    "-acodec", "libmp3lame",
                    "-ar", "16000",
                    "-ac", "1",
                    outputPath.toString())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Failed to transcode AI audio to mp3. contentType={}, exitCode={}, ffmpegOutput={}",
                        contentType, exitCode, output);
                throw new IllegalStateException("AI 语音暂时无法处理当前音频格式，请稍后重试");
            }

            byte[] mp3Bytes = Files.readAllBytes(outputPath);
            if (mp3Bytes.length == 0) {
                throw new IllegalStateException("AI 语音转码结果为空");
            }
            return mp3Bytes;
        } catch (IOException ex) {
            log.error("Failed to transcode AI audio because ffmpeg is unavailable. contentType={}", contentType, ex);
            throw new IllegalStateException("AI 语音转码组件未就绪，请检查服务端 ffmpeg 配置", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 语音转码被中断", ex);
        } finally {
            deleteQuietly(inputPath);
            deleteQuietly(outputPath);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String extensionFor(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/mp4" -> ".mp4";
            case "audio/m4a", "audio/x-m4a", "audio/aac" -> ".m4a";
            default -> ".audio";
        };
    }

    private boolean looksLikeWav(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'A'
                && bytes[10] == 'V'
                && bytes[11] == 'E';
    }

    private boolean looksLikeMp3(byte[] bytes) {
        if (bytes.length >= 3
                && bytes[0] == 'I'
                && bytes[1] == 'D'
                && bytes[2] == '3') {
            return true;
        }
        return bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xE0) == 0xE0;
    }
}
