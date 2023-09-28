package com.cosmos.unreddit.ui.profilemanager

import androidx.lifecycle.viewModelScope
import com.cosmos.unreddit.data.model.ProfileItem
import com.cosmos.unreddit.data.model.db.Profile
import com.cosmos.unreddit.data.repository.DatabaseRepository
import com.cosmos.unreddit.data.repository.PreferencesRepository
import com.cosmos.unreddit.di.DispatchersModule.DefaultDispatcher
import com.cosmos.unreddit.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileManagerViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val databaseRepository: DatabaseRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : BaseViewModel(preferencesRepository, databaseRepository) {

    val profiles: Flow<List<ProfileItem>> = databaseRepository.getAllProfiles()
        .map { list ->
            list.map {
                ProfileItem.UserProfile(it.apply { canDelete = list.size > 1 })
            }
        }
        .map {
            mutableListOf<ProfileItem>().apply {
                addAll(it)
                add(ProfileItem.NewProfile)
            }
        }
        .flowOn(defaultDispatcher)

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            preferencesRepository.setCurrentProfile(profile.id)
        }
    }

    fun addProfile(name: String) {
        viewModelScope.launch {
            databaseRepository.addProfile(name)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            databaseRepository.deleteProfile(profile.id)
        }
    }

    fun renameProfile(profile: Profile, newName: String) {
        viewModelScope.launch {
            val updatedProfile = profile.copy(name = newName)
            databaseRepository.updateProfile(updatedProfile)
        }
    }
}
