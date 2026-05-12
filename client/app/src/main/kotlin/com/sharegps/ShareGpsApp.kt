package com.sharegps

import android.app.Application
import com.naver.maps.map.NaverMapSdk

class ShareGpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.NAVER_MAPS_CLIENT_ID.isNotEmpty()) {
            NaverMapSdk.getInstance(this).client =
                NaverMapSdk.NaverCloudPlatformClient(BuildConfig.NAVER_MAPS_CLIENT_ID)
        }
    }
}
