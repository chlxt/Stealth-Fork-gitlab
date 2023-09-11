package com.cosmos.unreddit.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cosmos.stealth.sdk.Stealth
import com.cosmos.stealth.sdk.data.model.api.Appendable
import com.cosmos.stealth.sdk.data.model.api.Feedable
import com.cosmos.stealth.sdk.data.model.api.Post
import com.cosmos.stealth.sdk.util.Resource
import com.cosmos.unreddit.data.model.Filtering
import com.cosmos.unreddit.data.model.Service
import com.cosmos.unreddit.data.model.ServiceQuery
import com.cosmos.unreddit.data.remote.datasource.FeedDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StealthRepository @Inject constructor() {

    fun getPost(post: String, service: Service, filtering: Filtering): Flow<Resource<Post>> = flow {
        val response = Stealth.getPost(post, service.asSupportedService()) {
            filtering.sort?.let { sort = it }
            filtering.order?.let { order = it }
        }

        emit(response)
    }

    fun getFeed(
        query: List<ServiceQuery>,
        filtering: Filtering,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Feedable>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            FeedDataSource(query, filtering, pageSize)
        }.flow
    }

    fun getMore(appendable: Appendable): Flow<Resource<List<Feedable>>> = flow {
        emit(Stealth.getMore(appendable))
    }

    companion object {
        private const val DEFAULT_LIMIT = 25
    }
}
