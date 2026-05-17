package com.anonymous.lifetool

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.alibaba.sdk.android.push.CloudPushService
import com.alibaba.sdk.android.push.CommonCallback
import com.alibaba.sdk.android.push.noonesdk.PushServiceFactory
import android.content.res.Configuration

import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactPackage
import com.facebook.react.ReactHost
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint

import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ExpoReactHostFactory

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    ExpoReactHostFactory.getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(LifeToolPushPackage())
        }
    )
  }

  override fun onCreate() {
    super.onCreate()
    DefaultNewArchitectureEntryPoint.releaseLevel = try {
      ReleaseLevel.valueOf(BuildConfig.REACT_NATIVE_RELEASE_LEVEL.uppercase())
    } catch (e: IllegalArgumentException) {
      ReleaseLevel.STABLE
    }
    loadReactNative(this)
    initAliyunPush()
    ApplicationLifecycleDispatcher.onApplicationCreate(this)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
  }

  private fun initAliyunPush() {
    try {
      PushServiceFactory.init(this)
      createNotificationChannel()
      val pushService: CloudPushService = PushServiceFactory.getCloudPushService()
      pushService.register(this, object : CommonCallback {
        override fun onSuccess(response: String?) {
          LifeToolPushRegistry.initialized = true
          LifeToolPushRegistry.vendorDeviceId = pushService.deviceId
          LifeToolPushRegistry.pushToken = pushService.deviceId
          Log.i("LifeToolPush", "Aliyun push register success: ${pushService.deviceId}")
        }

        override fun onFailed(errorCode: String?, errorMessage: String?) {
          LifeToolPushRegistry.initialized = false
          Log.w("LifeToolPush", "Aliyun push register failed: $errorCode $errorMessage")
        }
      })
    } catch (error: Throwable) {
      Log.w("LifeToolPush", "Aliyun push init failed", error)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "lifetool_messages"
    val channel = NotificationChannel(
      channelId,
      "LifeTool 消息提醒",
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = "好友消息与提醒通知"
      enableLights(true)
      enableVibration(true)
    }
    manager.createNotificationChannel(channel)
  }
}
