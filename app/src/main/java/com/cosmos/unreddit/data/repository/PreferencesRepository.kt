package com.cosmos.unreddit.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.cosmos.stealth.sdk.data.model.api.ServiceName
import com.cosmos.stealth.sdk.data.model.service.RedditService.Instance.OLD
import com.cosmos.stealth.sdk.data.model.service.RedditService.Instance.REGULAR
import com.cosmos.unreddit.data.local.RedditDatabase
import com.cosmos.unreddit.data.model.Service
import com.cosmos.unreddit.data.model.db.Redirect
import com.cosmos.unreddit.data.model.preferences.ContentPreferences
import com.cosmos.unreddit.data.model.preferences.DataPreferences
import com.cosmos.unreddit.data.model.preferences.DataPreferences.RedditSource.REDDIT
import com.cosmos.unreddit.data.model.preferences.DataPreferences.RedditSource.REDDIT_SCRAP
import com.cosmos.unreddit.data.model.preferences.DataPreferences.RedditSource.TEDDIT
import com.cosmos.unreddit.data.model.preferences.MediaPreferences
import com.cosmos.unreddit.data.model.preferences.PolicyDisclaimerPreferences
import com.cosmos.unreddit.data.model.preferences.ProfilePreferences
import com.cosmos.unreddit.data.model.preferences.UiPreferences
import com.cosmos.unreddit.di.CoroutinesScopesModule.ApplicationScope
import com.cosmos.unreddit.util.extension.getValue
import com.cosmos.unreddit.util.extension.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val preferencesDatastore: DataStore<Preferences>,
    private val redditDatabase: RedditDatabase,
    @ApplicationScope appScope: CoroutineScope
) {

    //region Ui

    suspend fun setNightMode(nightMode: Int) {
        preferencesDatastore.setValue(UiPreferences.PreferencesKeys.NIGHT_MODE, nightMode)
    }

    fun getNightMode(defaultValue: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM): Flow<Int> {
        return preferencesDatastore.getValue(UiPreferences.PreferencesKeys.NIGHT_MODE, defaultValue)
    }

    suspend fun setLeftHandedMode(leftHandedMode: Boolean) {
        preferencesDatastore.setValue(
            UiPreferences.PreferencesKeys.LEFT_HANDED_MODE,
            leftHandedMode
        )
    }

    fun getLeftHandedMode(defaultValue: Boolean = false): Flow<Boolean> {
        return preferencesDatastore.getValue(
            UiPreferences.PreferencesKeys.LEFT_HANDED_MODE,
            defaultValue
        )
    }

    //endregion

    //region Content

    val redditSource: StateFlow<Service> = combine(
        getRedditSource(),
        getRedditSourceInstance()
    ) { source, instance ->
        when (DataPreferences.RedditSource.fromValue(source)) {
            REDDIT -> Service(ServiceName.reddit, REGULAR.url)
            REDDIT_SCRAP -> Service(ServiceName.reddit, OLD.url)
            TEDDIT -> Service(ServiceName.teddit, instance)
        }
    }.stateIn(appScope, SharingStarted.Eagerly, DEFAULT_REDDIT_SOURCE)

    suspend fun setShowNsfw(showNsfw: Boolean) {
        preferencesDatastore.setValue(ContentPreferences.PreferencesKeys.SHOW_NSFW, showNsfw)
    }

    fun getShowNsfw(defaultValue: Boolean = false): Flow<Boolean> {
        return preferencesDatastore.getValue(
            ContentPreferences.PreferencesKeys.SHOW_NSFW,
            defaultValue
        )
    }

    suspend fun setShowNsfwPreview(showNsfwPreview: Boolean) {
        preferencesDatastore.setValue(
            ContentPreferences.PreferencesKeys.SHOW_NSFW_PREVIEW,
            showNsfwPreview
        )
    }

    fun getShowNsfwPreview(defaultValue: Boolean = false): Flow<Boolean> {
        return preferencesDatastore.getValue(
            ContentPreferences.PreferencesKeys.SHOW_NSFW_PREVIEW,
            defaultValue
        )
    }

    suspend fun setShowSpoilerPreview(showSpoilerPreview: Boolean) {
        preferencesDatastore.setValue(
            ContentPreferences.PreferencesKeys.SHOW_SPOILER_PREVIEW,
            showSpoilerPreview
        )
    }

    fun getShowSpoilerPreview(defaultValue: Boolean = false): Flow<Boolean> {
        return preferencesDatastore.getValue(
            ContentPreferences.PreferencesKeys.SHOW_SPOILER_PREVIEW,
            defaultValue
        )
    }

    suspend fun setRedditSource(redditSource: Int) {
        preferencesDatastore.setValue(
            DataPreferences.PreferencesKeys.REDDIT_SOURCE,
            redditSource
        )
    }

    fun getRedditSource(defaultValue: Int = DataPreferences.RedditSource.REDDIT.value): Flow<Int> {
        return preferencesDatastore.getValue(
            DataPreferences.PreferencesKeys.REDDIT_SOURCE,
            defaultValue
        )
    }

    suspend fun setRedditSourceInstance(instance: String) {
        preferencesDatastore.setValue(
            DataPreferences.PreferencesKeys.REDDIT_SOURCE_INSTANCE,
            instance
        )
    }

    fun getRedditSourceInstance(defaultValue: String = ""): Flow<String> {
        return preferencesDatastore.getValue(
            DataPreferences.PreferencesKeys.REDDIT_SOURCE_INSTANCE,
            defaultValue
        )
    }

    suspend fun setPrivacyEnhancerEnabled(enablePrivacyEnhancer: Boolean) {
        preferencesDatastore.setValue(
            DataPreferences.PreferencesKeys.PRIVACY_ENHANCER,
            enablePrivacyEnhancer
        )
    }

    fun getPrivacyEnhancerEnabled(defaultValue: Boolean = false): Flow<Boolean> {
        return preferencesDatastore.getValue(
            DataPreferences.PreferencesKeys.PRIVACY_ENHANCER,
            defaultValue
        )
    }

    fun getAllRedirects(): Flow<List<Redirect>> {
        return redditDatabase.redirectDao().getAllRedirects()
    }

    suspend fun updateRedirect(redirect: Redirect) {
        redditDatabase.redirectDao().upsert(redirect)
    }

    fun getContentPreferences(): Flow<ContentPreferences> {
        return preferencesDatastore.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val showNsfw = preferences[ContentPreferences.PreferencesKeys.SHOW_NSFW] ?: false
            val showNsfwPreview =
                preferences[ContentPreferences.PreferencesKeys.SHOW_NSFW_PREVIEW] ?: false
            val showSpoilerPreview =
                preferences[ContentPreferences.PreferencesKeys.SHOW_SPOILER_PREVIEW] ?: false
            ContentPreferences(showNsfw, showNsfwPreview, showSpoilerPreview)
        }
    }

    //endregion

    //region Profile

    fun getCurrentProfile(): Flow<Int> {
        return preferencesDatastore.getValue(
            ProfilePreferences.PreferencesKeys.CURRENT_PROFILE,
            -1
        )
    }

    suspend fun setCurrentProfile(profileId: Int) {
        preferencesDatastore.setValue(ProfilePreferences.PreferencesKeys.CURRENT_PROFILE, profileId)
    }

    //endregion

    //region Media

    fun getMuteVideo(defaultValue: Boolean): Flow<Boolean> {
        return preferencesDatastore.getValue(
            MediaPreferences.PreferencesKeys.MUTE_VIDEO,
            defaultValue
        )
    }

    suspend fun setMuteVideo(muteVideo: Boolean) {
        preferencesDatastore.setValue(MediaPreferences.PreferencesKeys.MUTE_VIDEO, muteVideo)
    }

    //endregion

    //region Policy Disclaimer

    fun getPolicyDisclaimerShown(defaultValue: Boolean): Flow<Boolean> {
        return preferencesDatastore.getValue(
            PolicyDisclaimerPreferences.PreferencesKeys.POLICY_DISCLAIMER_SHOWN,
            defaultValue
        )
    }

    suspend fun setPolicyDisclaimerShown(shown: Boolean) {
        preferencesDatastore.setValue(
            PolicyDisclaimerPreferences.PreferencesKeys.POLICY_DISCLAIMER_SHOWN,
            shown
        )
    }

    //endregion

    companion object {
        private val DEFAULT_REDDIT_SOURCE = Service(ServiceName.reddit, REGULAR.url)
    }
}
