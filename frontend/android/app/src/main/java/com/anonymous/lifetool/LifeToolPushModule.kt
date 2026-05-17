package com.anonymous.lifetool

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableNativeMap

class LifeToolPushModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "LifeToolPushModule"

  @ReactMethod
  fun getRegistrationInfo(promise: Promise) {
    val result = WritableNativeMap().apply {
      putString("vendorDeviceId", LifeToolPushRegistry.vendorDeviceId)
      putString("pushToken", LifeToolPushRegistry.pushToken)
      putString("provider", LifeToolPushRegistry.provider)
      putBoolean("initialized", LifeToolPushRegistry.initialized)
    }
    promise.resolve(result)
  }
}
