package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.HomeScrollViewBinding
import com.lagradost.cloudstream3.databinding.HomeScrollViewTvBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

/** "★ 8.0 · 2026 · Adventure" for the phone hero. Parts the provider did not fill are left out. */
private fun heroMeta(context: Context, item: LoadResponse): CharSequence {
    val out = SpannableStringBuilder()
    item.score?.toStringNull(0.1, 10, 1, false)?.let { score ->
        val start = out.length
        out.append("\u2605 ").append(score)
        out.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.ratingStar)),
            start,
            out.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    val rest = listOfNotNull(item.year?.toString(), item.tags?.firstOrNull()?.trim())
        .filter { it.isNotBlank() }
    for (part in rest) {
        if (out.isNotEmpty()) out.append("  \u00B7  ")
        out.append(part)
    }
    return out
}

class HomeScrollAdapter(
    val callback: ((View, Int, LoadResponse) -> Unit)
) : NoStateAdapter<LoadResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.uniqueUrl == b.uniqueUrl && a.name == b.name
})) {
    var hasMoreItems: Boolean = false

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(TV or EMULATOR)) {
            HomeScrollViewTvBinding.inflate(inflater, parent, false)
        } else {
            HomeScrollViewBinding.inflate(inflater, parent, false)
        }

        return ViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is HomeScrollViewBinding -> {
                clearImage(binding.homeScrollPreview)
            }

            is HomeScrollViewTvBinding -> {
                clearImage(binding.homeScrollPreview)
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: LoadResponse,
        position: Int,
    ) {
        val binding = holder.view

        val posterUrl = item.backgroundPosterUrl ?: item.posterUrl

        when (binding) {
            is HomeScrollViewBinding -> {
                binding.homeScrollPreview.loadImage(posterUrl, item.posterHeaders)
                val meta = heroMeta(binding.root.context, item)
                binding.homeScrollPreviewMeta.text = meta
                binding.homeScrollPreviewMeta.isGone = meta.isEmpty()
                binding.homeScrollPreviewPlot.apply {
                    text = item.plot?.html() ?: ""
                    isGone = item.plot.isNullOrBlank()
                }
                binding.homeScrollPreviewTitle.text = item.name.html()

                bindLogo(
                    url = item.logoUrl,
                    headers = item.posterHeaders,
                    titleView = binding.homeScrollPreviewTitle,
                    logoView = binding.homePreviewLogo
                )
            }

            is HomeScrollViewTvBinding -> {
                binding.homeScrollPreview.isFocusable = false
                binding.homeScrollPreview.setOnClickListener { view ->
                    callback.invoke(view ?: return@setOnClickListener, position, item)
                }
                binding.homeScrollPreview.loadImage(posterUrl, item.posterHeaders)
            }
        }
    }
}
