package com.cosmos.unreddit.ui.subreddit

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cosmos.unreddit.data.model.Sorting
import com.cosmos.unreddit.data.model.db.PostEntity
import com.cosmos.unreddit.data.model.preferences.ContentPreferences
import com.cosmos.unreddit.data.remote.api.reddit.RedditApi
import com.cosmos.unreddit.data.repository.PostListRepository
import com.cosmos.unreddit.data.repository.PreferencesRepository
import com.cosmos.unreddit.ui.base.BaseViewModel
import com.cosmos.unreddit.util.PagerHelper
import com.cosmos.unreddit.util.PostUtil
import com.cosmos.unreddit.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SubredditSearchViewModel @Inject constructor(
    private val repository: PostListRepository,
    preferencesRepository: PreferencesRepository
) : BaseViewModel(preferencesRepository, repository) {

    val contentPreferences: Flow<ContentPreferences> =
        preferencesRepository.getContentPreferences()

    private val _sorting: MutableStateFlow<Sorting> = MutableStateFlow(DEFAULT_SORTING)
    val sorting: StateFlow<Sorting> = _sorting

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query

    private val _subreddit: MutableStateFlow<String?> = MutableStateFlow(null)
    val subreddit: StateFlow<String?> = _subreddit

    private val searchPagerHelper = object : PagerHelper<PostEntity>() {
        override fun getResults(query: String, sorting: Sorting): Flow<PagingData<PostEntity>> {
            return repository.searchInSubreddit(
                query,
                _subreddit.value ?: FALLBACK_SUBREDDIT,
                sorting
            ).cachedIn(viewModelScope)
        }
    }

    fun searchAndFilterPosts(query: String, sorting: Sorting): Flow<PagingData<PostEntity>> {
        return PostUtil.filterPosts(
            searchPagerHelper.loadData(query, sorting),
            historyIds,
            contentPreferences
        ).cachedIn(viewModelScope)
    }

    fun setQuery(subreddit: String) {
        _query.updateValue(subreddit)
    }

    fun setSorting(sorting: Sorting) {
        _sorting.updateValue(sorting)
    }

    fun setSubreddit(subreddit: String) {
        _subreddit.updateValue(subreddit)
    }

    companion object {
        private const val FALLBACK_SUBREDDIT = "all"

        private val DEFAULT_SORTING = Sorting(RedditApi.Sort.RELEVANCE, RedditApi.TimeSorting.ALL)
    }
}
