package com.ctvhouse.ctvsdk

import android.app.Application
import android.util.Log
import com.ctvhouse.ctvads.CtvAds
import com.ctvhouse.ctvads.CtvAdsConfig

class CtvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CtvAds.initialize(
            context = this,
            config = CtvAdsConfig(
                host = AdConfig.BIDDER_HOST,
                appName = AdConfig.APP_NAME,
                publisherId = AdConfig.PUBLISHER_ID,
                test = AdConfig.TEST_MODE,
            ),
        ) { result ->
            Log.i("CtvApp", "CtvAds init: status=${result.status} version=${result.version}")
        }
    }
}
