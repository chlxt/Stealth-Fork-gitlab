package com.cosmos.unreddit.ui.base

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.cosmos.unreddit.NavigationGraphDirections
import com.cosmos.unreddit.R
import com.cosmos.unreddit.data.model.Service
import com.cosmos.unreddit.data.model.db.FeedItem
import com.cosmos.unreddit.data.model.db.PostItem
import com.cosmos.unreddit.ui.common.listener.ItemClickListener
import com.cosmos.unreddit.ui.common.widget.RedditView
import com.cosmos.unreddit.ui.linkmenu.LinkMenuFragment
import com.cosmos.unreddit.ui.postdetails.PostDetailsFragment
import com.cosmos.unreddit.ui.postmenu.PostMenuFragment
import com.cosmos.unreddit.util.LinkHandler
import com.cosmos.unreddit.util.extension.applyWindowInsets
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class BaseFragment : Fragment(), ItemClickListener, RedditView.OnLinkClickListener {

    protected open val viewModel: BaseViewModel? = null

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private val navOptions: NavOptions by lazy {
        NavOptions.Builder()
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_enter_anim)
            .setPopExitAnim(R.anim.nav_exit_anim)
            .build()
    }

    @Inject
    lateinit var linkHandler: LinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            onBackPressed()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets(view)
    }

    protected open fun applyInsets(view: View) {
        view.applyWindowInsets(bottom = false)
    }

    protected open fun onBackPressed() {
        onBackPressedCallback.isEnabled = false
        findNavController().navigateUp()
    }

    protected fun navigate(directions: NavDirections, navOptions: NavOptions = this.navOptions) {
        findNavController().navigate(directions, navOptions)
    }

    protected fun navigate(deepLink: Uri, navOptions: NavOptions = this.navOptions) {
        findNavController().navigate(deepLink, navOptions)
    }

    protected open fun onClick(fragmentManager: FragmentManager, post: PostItem) {
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim,
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim
            )
            .add(
                R.id.fragment_container,
                PostDetailsFragment.newInstance(post),
                PostDetailsFragment.TAG
            )
            .addToBackStack(null)
            .commit()
    }

    override fun onClick(item: FeedItem) {
        when (item) {
            is PostItem -> onClick(parentFragmentManager, item)
            else -> { /* ignore */ }
        }
    }

    override fun onLongClick(item: FeedItem) {
        when (item) {
            is PostItem -> PostMenuFragment.show(parentFragmentManager, item)
            else -> { /* ignore */ }
        }
    }

    override fun onMenuClick(item: FeedItem) {
        when (item) {
            is PostItem -> PostMenuFragment.show(parentFragmentManager, item)
            else -> { /* ignore */ }
        }
    }

    override fun onMediaClick(item: FeedItem) {
        viewModel?.insertPostInHistory(item.id)
        when (item) {
            is PostItem -> {
                if (item.media != null) {
                    linkHandler.openGallery(item.media)
                } else {
                    linkHandler.openMedia(item.url, item.mediaType)
                }
            }
            else -> { /* ignore */ }
        }
    }

    override fun onLinkClick(item: FeedItem) {
        viewModel?.insertPostInHistory(item.id)
        when (item) {
            is PostItem -> onLinkClick(item.url)
            else -> { /* ignore */ }
        }
    }

    override fun onSaveClick(item: FeedItem) {
        when (item) {
            is PostItem -> viewModel?.toggleSavePost(item)
            else -> { /* ignore */ }
        }
    }

    override fun onLinkClick(link: String) {
        linkHandler.handleLink(link)
    }

    override fun onLinkLongClick(link: String) {
        LinkMenuFragment.show(parentFragmentManager, link)
    }

    open fun openCommunity(community: String, service: Service) {
        navigate(NavigationGraphDirections.openCommunity(community, service))
    }

    open fun openUser(user: String, service: Service) {
        navigate(NavigationGraphDirections.openUser(user, service))
    }

    open fun openRedditLink(link: String) {
        try {
            navigate(Uri.parse(link))
        } catch (e: IllegalArgumentException) {
            linkHandler.openBrowser(link)
        }
    }
}
