package com.cosmos.unreddit.base

import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.navigation.fragment.findNavController
import com.cosmos.unreddit.R
import com.cosmos.unreddit.SubredditDirections
import com.cosmos.unreddit.UserDirections
import com.cosmos.unreddit.ViewerDirections
import com.cosmos.unreddit.model.MediaType
import com.cosmos.unreddit.post.PostEntity
import com.cosmos.unreddit.postdetails.PostDetailsFragment
import com.cosmos.unreddit.postlist.PostListAdapter
import com.cosmos.unreddit.util.LinkUtil
import com.cosmos.unreddit.util.openExternalLink
import com.cosmos.unreddit.view.RedditView

open class BaseFragment : Fragment(), PostListAdapter.PostClickListener,
    RedditView.OnLinkClickListener {

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            onBackPressed()
        }
    }

    protected open fun onBackPressed() {
        onBackPressedCallback.isEnabled = false
        activity?.onBackPressed()
    }

    override fun onClick(post: PostEntity) {
        parentFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .add(
                R.id.fragment_container,
                PostDetailsFragment.newInstance(post),
                PostDetailsFragment.TAG
            )
            .addToBackStack(null)
            .commit()
    }

    override fun onLongClick(post: PostEntity) {
        TODO("Not yet implemented")
    }

    override fun onImageClick(post: PostEntity) {
        if (post.gallery.isNotEmpty()) {
            findNavController().navigate(
                ViewerDirections.openGallery(post.gallery.toTypedArray())
            )
        } else {
            findNavController().navigate(
                ViewerDirections.openMedia(post.mediaUrl, post.mediaType)
            )
        }
    }

    override fun onVideoClick(post: PostEntity) {
        findNavController().navigate(
            ViewerDirections.openMedia(post.mediaUrl, post.mediaType)
        )
    }

    override fun onLinkClick(post: PostEntity) {
        TODO("Not yet implemented")
    }

    override fun onLinkClick(link: String) {
        when (val mediaType = LinkUtil.getLinkType(link)) {
            MediaType.REDDIT_SUBREDDIT -> {
                val subreddit = link.removePrefix("/r/")
                findNavController().navigate(
                    SubredditDirections.openSubreddit(subreddit)
                )
            }

            MediaType.REDDIT_USER -> {
                val user = link.removePrefix("/u/")
                findNavController().navigate(
                    UserDirections.openUser(user)
                )
            }

            MediaType.REDDIT_LINK -> {
                findNavController().navigate(Uri.parse(link))
            }

            MediaType.IMGUR_ALBUM,
            MediaType.IMGUR_GALLERY,
            MediaType.IMGUR_LINK,
            MediaType.IMGUR_GIF,
            MediaType.IMGUR_VIDEO,
            MediaType.IMGUR_IMAGE,
            MediaType.REDGIFS,
            MediaType.STREAMABLE,
            MediaType.IMAGE,
            MediaType.VIDEO -> {
                findNavController().navigate(
                    ViewerDirections.openMedia(link, mediaType)
                )
            }

            else -> openExternalLink(link)
        }
    }

    override fun onLinkLongClick(link: String) {
        TODO("Not yet implemented")
    }
}