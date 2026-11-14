package com.lagradost.cloudstream3.ui.browse

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.BrowseGridItemBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class BrowseAdapter(
    private val onClick: (SearchResponse) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(
    itemSame = { a, b -> a.url == b.url },
    contentSame = { a, b -> a == b }
)) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> =
        ViewHolderState(BrowseGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onClearView(holder: ViewHolderState<Any>) {
        (holder.view as? BrowseGridItemBinding)?.let { clearImage(it.browseItemPoster) }
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        val binding = holder.view as? BrowseGridItemBinding ?: return
        val context = binding.root.context
        binding.browseItemPoster.loadImage(item.posterUrl, item.posterHeaders)
        binding.browseItemTitle.text = item.name

        val meta = SpannableStringBuilder()
        item.score?.toStringNull(0.1, 10, 1, false)?.let { score ->
            meta.append("★ ").append(score)
            meta.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.ratingStar)),
                0,
                meta.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        item.releaseYear?.let { year ->
            if (meta.isNotEmpty()) meta.append("  ·  ")
            meta.append(year.toString())
        }
        binding.browseItemMeta.text = meta
        binding.browseItemMeta.isGone = meta.isEmpty()

        binding.browseItemRoot.setOnClickListener { onClick(item) }
    }
}
