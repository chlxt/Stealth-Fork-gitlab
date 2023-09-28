package com.cosmos.unreddit.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Slide
import com.cosmos.unreddit.R
import com.cosmos.unreddit.data.model.Service
import com.cosmos.unreddit.databinding.FragmentSearchBinding
import com.cosmos.unreddit.ui.base.BaseFragment
import com.cosmos.unreddit.ui.common.adapter.FragmentAdapter
import com.cosmos.unreddit.ui.searchquery.SearchQueryViewModel
import com.cosmos.unreddit.ui.sort.SortFragment
import com.cosmos.unreddit.util.SearchUtil
import com.cosmos.unreddit.util.extension.clearFilteringListener
import com.cosmos.unreddit.util.extension.getRecyclerView
import com.cosmos.unreddit.util.extension.launchRepeat
import com.cosmos.unreddit.util.extension.scrollToTop
import com.cosmos.unreddit.util.extension.setFilteringListener
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : BaseFragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    override val viewModel: SearchViewModel by hiltNavGraphViewModels(R.id.search)
    private val searchQueryViewModel: SearchQueryViewModel by hiltNavGraphViewModels(R.id.search)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val service = Service(
                searchQueryViewModel.serviceName.value,
                searchQueryViewModel.instance
            )
            viewModel.setService(service)
            viewModel.setFiltering(searchQueryViewModel.filtering.value)
            viewModel.setQuery(searchQueryViewModel.query)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        initAnimations()

        val query = viewModel.query.value.takeIf { it.isNotBlank() }

        binding.appBar.searchInput.setText(query)

        initResultListener()
        initAppBar()
        initViewPager()
        bindViewModel()

        lifecycleScope.launch {
            delay(250)
            showSearchInput(false)
        }
    }

    private fun initAnimations() {
        enterTransition = Slide().apply {
            duration = resources.getInteger(R.integer.motion_duration_medium).toLong()
            addTarget(binding.root)
        }
        returnTransition = Slide().apply {
            duration = resources.getInteger(R.integer.motion_duration_medium).toLong()
            addTarget(binding.root)
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.query.collect { query ->
                    query.takeIf { it.isNotBlank() }?.let {
                        binding.appBar.label.text = query
                    }
                }
            }

            launch {
                viewModel.filtering.collect {
                    binding.appBar.sortIcon.setFiltering(it)
                }
            }
        }
    }

    private fun initViewPager() {
        val fragments = listOf(
            FragmentAdapter.Page(R.string.tab_search_post, SearchPostFragment::class.java),
            FragmentAdapter.Page(
                R.string.tab_search_subreddit,
                SearchSubredditFragment::class.java
            ),
            FragmentAdapter.Page(R.string.tab_search_user, SearchUserFragment::class.java)
        )

        val fragmentAdapter = FragmentAdapter(this, fragments)

        binding.viewPager.apply {
            adapter = fragmentAdapter
            offscreenPageLimit = 2
            getRecyclerView()?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // ignore
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // ignore
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.let { binding.viewPager.scrollToTop(it.position) }
            }
        })

        TabLayoutMediator(binding.tabs, binding.viewPager) { tab, position ->
            tab.setText(fragments[position].title)
        }.attach()
    }

    private fun initAppBar() {
        with(binding.appBar) {
            label.setOnClickListener { showSearchInput(true) }
            root.setOnClickListener { showSearchInput(true) }
            searchInput.apply {
                addTarget(backCard)
                addTarget(label)
                addTarget(sortCard)
                addTarget(sortIcon)
                addTarget(cancelCard)
                setSearchActionListener {
                    handleSearchAction(it)
                }
            }
            sortCard.setOnClickListener { showSortDialog() }
            backCard.setOnClickListener { onBackPressed() }
            cancelCard.setOnClickListener { showSearchInput(false) }
        }
    }

    private fun initResultListener() {
        setFilteringListener { filtering -> filtering?.let { viewModel.setFiltering(it) } }
    }

    private fun showSearchInput(show: Boolean) {
        binding.appBar.searchInput.apply {
            show(binding.appBar.root, show) {
                with(binding.appBar) {
                    backCard.isVisible = !show
                    label.isVisible = !show
                    sortCard.isVisible = !show
                    sortIcon.isVisible = !show
                    cancelCard.isVisible = show
                }
            }
            setSelection(text?.length ?: 0)
        }
    }

    private fun handleSearchAction(query: String) {
        if (SearchUtil.isQueryValid(query)) {
            viewModel.setQuery(query)
            showSearchInput(false)
        }
    }

    private fun showSortDialog() {
        SortFragment.show(
            childFragmentManager,
            viewModel.filtering.value,
            SortFragment.SortType.SEARCH
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearFilteringListener()
        _binding = null
    }

    companion object {
        const val TAG = "SearchFragment"
    }
}
