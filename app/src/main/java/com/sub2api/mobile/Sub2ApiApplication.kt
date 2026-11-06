package com.sub2api.mobile

import android.app.Application
import com.sub2api.mobile.data.ApiClientFactory
import com.sub2api.mobile.data.ApiRepository
import com.sub2api.mobile.data.AppUpdateRepository
import com.sub2api.mobile.data.SessionStore
import com.sub2api.mobile.data.SessionState

class Sub2ApiApplication : Application() {
    lateinit var sessionStore: SessionStore
        private set
    private lateinit var clientFactory: ApiClientFactory
    fun repository(session: SessionState = sessionStore.state.value) = ApiRepository(sessionStore, clientFactory, session)
    lateinit var updateRepository: AppUpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        clientFactory = ApiClientFactory(sessionStore)
        updateRepository = AppUpdateRepository()
    }
}
