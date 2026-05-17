package com.anonymous.lifetool

object LifeToolPushRegistry {
  @Volatile
  var vendorDeviceId: String? = null

  @Volatile
  var pushToken: String? = null

  @Volatile
  var provider: String = "aliyun"

  @Volatile
  var initialized: Boolean = false
}
