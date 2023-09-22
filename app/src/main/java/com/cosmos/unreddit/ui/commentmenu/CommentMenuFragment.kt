package com.cosmos.unreddit.ui.commentmenu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.cosmos.unreddit.NavigationGraphDirections
import com.cosmos.unreddit.R
import com.cosmos.unreddit.data.model.Block
import com.cosmos.unreddit.data.model.db.CommentItem
import com.cosmos.unreddit.databinding.FragmentCommentMenuBinding
import com.cosmos.unreddit.util.extension.doAndDismiss
import com.cosmos.unreddit.util.extension.openExternalLink
import com.cosmos.unreddit.util.extension.parcelable
import com.cosmos.unreddit.util.extension.serializable
import com.cosmos.unreddit.util.extension.shareExternalLink
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CommentMenuFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCommentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val type = arguments?.serializable(BUNDLE_KEY_TYPE) ?: MenuType.DETAILS
        binding.type = type

        val comment = arguments?.parcelable<CommentItem>(BUNDLE_KEY_COMMENT)
        comment?.let {
            binding.comment = it

            binding.textTitle.text = if (it.bodyText.isFirstBlockText()) {
                (it.bodyText.blocks.first().block as Block.TextBlock).text
            } else {
                it.community
            }

            initActions(it)
        }
    }

    private fun initActions(comment: CommentItem) {
        with(binding) {
            buttonUser.setOnClickListener {
                doAndDismiss {
                    findNavController().navigate(NavigationGraphDirections.openUser(comment.author))
                }
            }

            buttonSubreddit.setOnClickListener {
                doAndDismiss {
                    // TODO: Migration V3
                    //  Deprecate openSubreddit destination
                    findNavController().navigate(NavigationGraphDirections.openSubreddit(comment.community))
                }
            }

            buttonSave.run {
                setOnClickListener {
                    doAndDismiss {
                        setFragmentResult(REQUEST_KEY_COMMENT, bundleOf(BUNDLE_KEY_COMMENT to comment))
                    }
                }
                if (comment.saved) {
                    text = getString(R.string.unsave)
                    chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_save_filled)
                } else {
                    text = getString(R.string.save)
                    chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_save_outline)
                }
            }

            buttonOpen.setOnClickListener {
                doAndDismiss { openExternalLink(comment.refLink) }
            }

            buttonShareLink.setOnClickListener {
                doAndDismiss { shareExternalLink(comment.refLink) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class MenuType {
        USER, DETAILS
    }

    companion object {
        private const val TAG = "CommentMenuFragment"

        const val REQUEST_KEY_COMMENT = "REQUEST_KEY_COMMENT"

        const val BUNDLE_KEY_COMMENT = "BUNDLE_KEY_COMMENT"
        private const val BUNDLE_KEY_TYPE = "BUNDLE_KEY_TYPE"

        fun show(
            fragmentManager: FragmentManager,
            comment: CommentItem,
            type: MenuType
        ) {
            CommentMenuFragment().apply {
                arguments = bundleOf(
                    BUNDLE_KEY_COMMENT to comment,
                    BUNDLE_KEY_TYPE to type
                )
            }.show(fragmentManager, TAG)
        }
    }
}
