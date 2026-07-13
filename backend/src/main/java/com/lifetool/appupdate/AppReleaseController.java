package com.lifetool.appupdate;

import com.lifetool.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/releases")
public class AppReleaseController {

    private final AppReleaseResponse latestRelease;

    public AppReleaseController(
            @Value("${lifetool.app-update.android.version-name:1.0.0}") String versionName,
            @Value("${lifetool.app-update.android.version-code:1}") int versionCode,
            @Value("${lifetool.app-update.android.download-url:}") String downloadUrl,
            @Value("${lifetool.app-update.android.release-notes:暂无更新说明}") String releaseNotes,
            @Value("${lifetool.app-update.android.force-update:false}") boolean forceUpdate) {
        this.latestRelease = new AppReleaseResponse(
                "android",
                versionName,
                versionCode,
                downloadUrl,
                releaseNotes,
                forceUpdate);
    }

    @GetMapping("/latest")
    public ApiResponse<AppReleaseResponse> latest() {
        return ApiResponse.ok(latestRelease);
    }

    public record AppReleaseResponse(
            String platform,
            String versionName,
            int versionCode,
            String downloadUrl,
            String releaseNotes,
            boolean forceUpdate) {
    }
}
