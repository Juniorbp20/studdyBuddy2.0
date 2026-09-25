package com.example.studybuddy

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd

class App : Application() {

    companion object {
        private const val TAG = "AppOpenAd"
        private const val AD_UNIT_ID = "ca-app-pub-6527199097237109/3907609897"
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
        private const val TEST_DEVICE_ID = "a02760da45b83748"
    }

    private val adUnitId = if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else AD_UNIT_ID

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var isFirstLaunch = true
    private var pendingShow = false
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {
            if (BuildConfig.DEBUG) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(TEST_DEVICE_ID))
                        .build()
                )
            }
            loadAd()
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
                if (pendingShow) {
                    pendingShow = false
                    if (!isFirstLaunch) showAdIfAvailable()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START && !isFirstLaunch) {
                    if (currentActivity != null) {
                        showAdIfAvailable()
                    } else {
                        pendingShow = true
                    }
                }
                if (event == Lifecycle.Event.ON_STOP) {
                    isFirstLaunch = false
                }
            }
        )
    }

    private fun loadAd() {
        if (isLoadingAd || appOpenAd != null) return
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            this,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "ad cargado")
                    appOpenAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "error al cargar: ${error.message}")
                    isLoadingAd = false
                }
            }
        )
    }

    private fun showAdIfAvailable() {
        val activity = currentActivity
        if (isShowingAd || appOpenAd == null || activity == null) return
        isShowingAd = true
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }
        }
        appOpenAd?.show(activity)
    }
}