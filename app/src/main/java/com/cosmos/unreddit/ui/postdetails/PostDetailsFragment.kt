package com.cosmos.unreddit.ui.postdetails

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.cosmos.stealth.sdk.data.model.api.ServiceName
import com.cosmos.unreddit.R
import com.cosmos.unreddit.data.local.mapper.FeedableMapper
import com.cosmos.unreddit.data.model.Resource
import com.cosmos.unreddit.data.model.Service
import com.cosmos.unreddit.data.model.db.PostItem
import com.cosmos.unreddit.data.repository.PreferencesRepository
import com.cosmos.unreddit.data.repository.StealthRepository
import com.cosmos.unreddit.databinding.FragmentPostDetailsBinding
import com.cosmos.unreddit.di.DispatchersModule.DefaultDispatcher
import com.cosmos.unreddit.di.DispatchersModule.MainImmediateDispatcher
import com.cosmos.unreddit.ui.base.BaseFragment
import com.cosmos.unreddit.ui.commentmenu.CommentMenuFragment
import com.cosmos.unreddit.ui.common.ElasticDragDismissFrameLayout
import com.cosmos.unreddit.ui.loadstate.ResourceStateAdapter
import com.cosmos.unreddit.ui.sort.SortFragment
import com.cosmos.unreddit.util.extension.applyWindowInsets
import com.cosmos.unreddit.util.extension.betterSmoothScrollToPosition
import com.cosmos.unreddit.util.extension.clearCommentSaveListener
import com.cosmos.unreddit.util.extension.clearFilteringListener
import com.cosmos.unreddit.util.extension.launchRepeat
import com.cosmos.unreddit.util.extension.orFalse
import com.cosmos.unreddit.util.extension.parcelable
import com.cosmos.unreddit.util.extension.setCommentSaveListener
import com.cosmos.unreddit.util.extension.setFilteringListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class PostDetailsFragment : BaseFragment(),
    ElasticDragDismissFrameLayout.ElasticDragDismissCallback, PopupMenu.OnMenuItemClickListener {

    private var _binding: FragmentPostDetailsBinding? = null
    private val binding get() = _binding!!

    override val viewModel: PostDetailsViewModel by viewModels()

    private val args: PostDetailsFragmentArgs by navArgs()

    private val contentRadius by lazy { resources.getDimension(R.dimen.subreddit_content_radius) }
    private val contentElevation by lazy {
        resources.getDimension(R.dimen.subreddit_content_elevation)
    }

    private var isLegacyNavigation: Boolean = false

    private lateinit var postAdapter: PostAdapter
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var resourceStateAdapter: ResourceStateAdapter

    private val isOnlyPostOpen: Boolean
        get() = parentFragmentManager.fragments.count { it is PostDetailsFragment } == 1

    @Inject
    lateinit var repository: StealthRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var feedableMapper: FeedableMapper

    @Inject
    @MainImmediateDispatcher
    lateinit var mainImmediateDispatcher: CoroutineDispatcher

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            preferencesRepository.getContentPreferences().first()
        }

        if (savedInstanceState == null) {
            handleArguments()
        }

        isLegacyNavigation = (args.name == null || args.id == null)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailsBinding.inflate(LayoutInflater.from(context))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutRoot.applyWindowInsets(bottom = false)

        showNavigation(false)

        binding.root.addListener(this)

        initResultListener()
        initAppBar()
        initRecyclerView()

        val post = arguments?.parcelable<PostItem>(KEY_POST_ENTITY)
        post?.let {
            bindPost(it, true)
        }

        bindViewModel()

        binding.singleThreadLayout.setOnClickListener { loadFullDiscussion() }
    }

    private fun initRecyclerView() {
        val contentPreferences = runBlocking {
            preferencesRepository.getContentPreferences().first()
        }

        postAdapter = PostAdapter(contentPreferences, this, this)
        commentAdapter = CommentAdapter(
            requireContext(),
            mainImmediateDispatcher,
            defaultDispatcher,
            repository,
            feedableMapper,
            this
        ) {
            CommentMenuFragment.show(childFragmentManager, it, CommentMenuFragment.MenuType.DETAILS)
        }.apply {
            // Wait for data to restore adapter position
            stateRestorationPolicy = PREVENT_WHEN_EMPTY
        }
        resourceStateAdapter = ResourceStateAdapter { retry() }

        val concatAdapter = ConcatAdapter(postAdapter, resourceStateAdapter, commentAdapter)
        binding.listComments.apply {
            applyWindowInsets(left = false, top = false, right = false)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = concatAdapter
        }
    }

    private fun bindViewModel() {
        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                combine(
                    viewModel.postId,
                    viewModel.service,
                    viewModel.filtering
                ) { postId, service, _ ->
                    if (postId != null && service != null) {
                        viewModel.loadPost(false)
                    }
                }.collect()
            }

            launch {
                viewModel.post.collect {
                    when (it) {
                        is Resource.Success -> bindPost(it.data as PostItem, false)
                        else -> {
                            // ignore
                        }
                    }
                }
            }

            launch {
                viewModel.replies.collect {
                    resourceStateAdapter.resource = it
                    when (it) {
                        is Resource.Success -> commentAdapter.submitList(it.data)
                        else -> {
                            // ignore
                        }
                    }
                }
            }

            launch {
                viewModel.filtering.collect {
                    binding.appBar.sortIcon.setFiltering(it)
                }
            }

            launch {
                viewModel.singleThread.collect { isSingleThread ->
                    val transition = Slide(Gravity.TOP).apply {
                        duration = 500
                        addTarget(binding.singleThreadLayout)
                    }
                    TransitionManager.beginDelayedTransition(binding.root, transition)
                    binding.singleThreadLayout.isVisible = isSingleThread
                }
            }

            launch {
                viewModel.savedCommentIds.collect { savedCommentIds ->
                    commentAdapter.savedIds = savedCommentIds
                }
            }
        }
    }

    private fun initAppBar() {
        with(binding.appBar) {
            backCard.setOnClickListener { onBackPressed() }
            sortCard.setOnClickListener { showSortDialog() }
            moreCard.setOnClickListener { showMenu() }
        }
    }

    private fun initResultListener() {
        setFilteringListener { filtering ->
            filtering?.let {
                viewModel.setFiltering(filtering)
                binding.listComments.betterSmoothScrollToPosition(0)
            }
        }
        setCommentSaveListener { comment -> comment?.let { viewModel.toggleSaveComment(it) } }
    }

    private fun bindPost(post: PostItem, fromCache: Boolean) {
        binding.appBar.label.text = post.title
        postAdapter.setPost(post, fromCache)
        commentAdapter.postItem = post
        viewModel.insertPostInHistory(post.id)
    }

    private fun handleArguments() {
        if (args.id != null) {
            // TODO: Handle other services (only Reddit deep links are set)
            viewModel.setPostId(args.id!!)
            viewModel.setService(Service(ServiceName.reddit))
        } else {
            when {
                arguments?.containsKey(KEY_POST_ENTITY).orFalse() -> {
                    val post = arguments?.parcelable<PostItem>(KEY_POST_ENTITY)

                    post?.let {
                        viewModel.setPostId(it.id)
                        viewModel.setService(it.service)
                    }
                }
                arguments?.containsKey(KEY_POST_ID).orFalse() -> {
                    val postId = arguments?.getString(KEY_POST_ID)
                    val service = arguments?.parcelable<Service>(KEY_SERVICE)

                    if (postId != null && service != null) {
                        viewModel.setPostId(postId)
                        viewModel.setService(service)
                    }
                }
            }
        }
    }

    private fun loadFullDiscussion() {
        val permalink = viewModel.postId.value
        permalink?.let {
            val newPermalink = it.removeSuffix("/").substringBeforeLast("/")
            viewModel.setPostId(newPermalink)
            viewModel.setSingleThread(false)
            viewModel.loadPost(false)
        }
    }

    private fun retry() {
        viewModel.loadPost(true)
    }

    private fun showSortDialog() {
        SortFragment.show(
            childFragmentManager,
            viewModel.filtering.value,
            SortFragment.SortType.POST
        )
    }

    private fun showNavigation(show: Boolean) {
        setFragmentResult(REQUEST_KEY_NAVIGATION, bundleOf(BUNDLE_KEY_NAVIGATION to show))
    }

    private fun showMenu() {
        PopupMenu(requireContext(), binding.appBar.moreCard)
            .apply {
                menuInflater.inflate(R.menu.post_menu, this.menu)
                setOnMenuItemClickListener(this@PostDetailsFragment)
            }
            .show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> viewModel.loadPost(true)
            else -> {
                return false
            }
        }
        return true
    }

    override fun onBackPressed() {
        if (isOnlyPostOpen) {
            showNavigation(true)
        }

        if (isLegacyNavigation) {
            // Prevent onBackPressed event to be passed to PostDetailsFragment and show bottom nav
            parentFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()

        // Save comment hierarchy
        viewModel.setReplies(commentAdapter.currentList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearFilteringListener()
        clearCommentSaveListener()
        _binding = null
        commentAdapter.cleanUp()
    }

    override fun onDrag(
        elasticOffset: Float,
        elasticOffsetPixels: Float,
        rawOffset: Float,
        rawOffsetPixels: Float
    ) {
        binding.root.cardElevation = contentElevation * rawOffset
        binding.root.radius = contentRadius * rawOffset
    }

    override fun onDragDismissed() {
        onBackPressed()
    }

    companion object {
        const val TAG = "PostDetailsFragment"

        const val REQUEST_KEY_NAVIGATION = "REQUEST_KEY_NAVIGATION"
        const val BUNDLE_KEY_NAVIGATION = "BUNDLE_KEY_NAVIGATION"

        private const val KEY_POST_ENTITY = "KEY_POST_ENTITY"

        private const val KEY_POST_ID = "KEY_POST_ID"
        private const val KEY_SERVICE = "KEY_SERVICE"

        fun newInstance(post: PostItem) = PostDetailsFragment().apply {
            arguments = bundleOf(
                KEY_POST_ENTITY to post
            )
        }

        fun newInstance(postId: String, service: Service) = PostDetailsFragment().apply {
            arguments = bundleOf(
                KEY_POST_ID to postId,
                KEY_SERVICE to service
            )
        }
    }
}
