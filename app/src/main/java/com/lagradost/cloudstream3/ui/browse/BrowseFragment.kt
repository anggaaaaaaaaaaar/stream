package com.lagradost.cloudstream3.ui.browse

import android.view.View
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentBrowseBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class BrowseFragment : BaseFragment<FragmentBrowseBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentBrowseBinding::inflate)
) {
    private val viewModel: BrowseViewModel by viewModels()

    private fun providers(): List<MainAPI> =
        context?.filterProviderByPreferredMedia() ?: emptyList()

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = !isLayout(PHONE)
        )
    }

    private fun tabs(binding: FragmentBrowseBinding): List<Pair<TextView, BrowseCategory>> = listOf(
        binding.browseTabMovies to BrowseCategory.MOVIES,
        binding.browseTabSeries to BrowseCategory.SERIES,
        binding.browseTabAnime to BrowseCategory.ANIME,
        binding.browseTabOthers to BrowseCategory.OTHERS,
    )

    override fun onBindingCreated(binding: FragmentBrowseBinding) {
        val adapter = BrowseAdapter { card -> activity?.loadSearchResult(card) }

        binding.browseGrid.apply {
            val widthDp = resources.configuration.screenWidthDp
            layoutManager = GridLayoutManager(context, (widthDp / 130).coerceAtLeast(3))
            this.adapter = adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0 && !recyclerView.canScrollVertically(1)) viewModel.loadMore()
                }
            })
        }

        for ((tab, category) in tabs(binding)) {
            tab.setOnClickListener {
                viewModel.setCategory(category, providers(), DataStoreHelper.currentHomePage)
            }
        }

        binding.browseFilterService.setOnClickListener { pickService() }
        binding.browseFilterService.setOnCloseIconClickListener { pickService() }
        binding.browseFilterSection.setOnClickListener { pickSection() }
        binding.browseFilterSection.setOnCloseIconClickListener { pickSection() }
        binding.browseFilterSort.setOnClickListener { pickSort() }
        binding.browseFilterSort.setOnCloseIconClickListener { pickSort() }
        binding.browseFilterYear.setOnClickListener { pickYear() }
        binding.browseFilterYear.setOnCloseIconClickListener { pickYear() }
        binding.browseRetry.setOnClickListener { viewModel.reload() }

        observe(viewModel.state) { state ->
            for ((tab, category) in tabs(binding)) tab.isSelected = category == state.category

            binding.browseFilterService.text = state.apiName ?: getString(R.string.browse_service)
            binding.browseFilterSection.isVisible = state.sections.size > 1
            binding.browseFilterSection.text = state.section ?: getString(R.string.all)
            binding.browseFilterSort.text = getString(state.sort.title)
            binding.browseFilterYear.text = state.year?.toString() ?: getString(R.string.year)
            binding.browseFilterYear.isVisible = state.years.isNotEmpty() || state.year != null

            when (val items = state.items) {
                is Resource.Loading -> {
                    binding.browseLoading.isVisible = true
                    binding.browseEmpty.isGone = true
                }

                is Resource.Success -> {
                    binding.browseLoading.isGone = true
                    adapter.submitList(items.value)
                    binding.browseEmpty.isVisible = items.value.isEmpty()
                    binding.browseRetry.isVisible = state.apiName != null
                    binding.browseEmptyText.setText(
                        if (state.apiName == null) R.string.browse_no_provider else R.string.no_data
                    )
                }

                is Resource.Failure -> {
                    binding.browseLoading.isGone = true
                    adapter.submitList(emptyList())
                    binding.browseEmpty.isVisible = true
                    binding.browseRetry.isVisible = true
                    binding.browseEmptyText.text = items.errorString
                }
            }
        }

        viewModel.setCategory(
            viewModel.state.value?.category ?: BrowseCategory.MOVIES,
            providers(),
            DataStoreHelper.currentHomePage
        )
    }

    private fun pickService() {
        val category = viewModel.state.value?.category ?: return
        val usable = providers().filter { category.supports(it) }
        if (usable.isEmpty()) return
        activity?.showBottomDialog(
            usable.map { it.name },
            usable.indexOfFirst { it.name == viewModel.state.value?.apiName },
            getString(R.string.browse_service),
            showApply = false,
            {}
        ) { index -> usable.getOrNull(index)?.let { viewModel.setProvider(it) } }
    }

    private fun pickSection() {
        val sections = viewModel.state.value?.sections ?: return
        val options = listOf(getString(R.string.all)) + sections
        val selected = viewModel.state.value?.section?.let { sections.indexOf(it) + 1 } ?: 0
        activity?.showBottomDialog(
            options,
            selected,
            getString(R.string.browse_section),
            showApply = false,
            {}
        ) { index -> viewModel.setSection(sections.getOrNull(index - 1)) }
    }

    private fun pickSort() {
        val sorts = BrowseSort.entries
        activity?.showBottomDialog(
            sorts.map { getString(it.title) },
            sorts.indexOf(viewModel.state.value?.sort ?: BrowseSort.PROVIDER),
            getString(R.string.sort_by),
            showApply = false,
            {}
        ) { index -> viewModel.setSort(sorts[index]) }
    }

    private fun pickYear() {
        val years = viewModel.state.value?.years ?: return
        val options = listOf(getString(R.string.all)) + years.map { it.toString() }
        val selected = viewModel.state.value?.year?.let { years.indexOf(it) + 1 } ?: 0
        activity?.showBottomDialog(
            options,
            selected,
            getString(R.string.year),
            showApply = false,
            {}
        ) { index -> viewModel.setYear(years.getOrNull(index - 1)) }
    }

    private fun onPluginsLoaded(@Suppress("UNUSED_PARAMETER") success: Boolean = false) = main {
        val state = viewModel.state.value ?: return@main
        if (state.apiName == null) {
            viewModel.setCategory(state.category, providers(), DataStoreHelper.currentHomePage, force = true)
        }
    }

    override fun onResume() {
        super.onResume()
        afterPluginsLoadedEvent += ::onPluginsLoaded
    }

    override fun onStop() {
        super.onStop()
        afterPluginsLoadedEvent -= ::onPluginsLoaded
    }
}
