package com.lagradost.cloudstream3.ui.browse

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.Job

enum class BrowseCategory(@StringRes val title: Int, val types: Set<TvType>) {
    MOVIES(R.string.movies, setOf(TvType.Movie, TvType.AnimeMovie)),
    SERIES(R.string.tv_series, setOf(TvType.TvSeries, TvType.Cartoon)),
    ANIME(R.string.anime, setOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
    OTHERS(
        R.string.others,
        setOf(
            TvType.AsianDrama, TvType.Documentary, TvType.Live, TvType.Torrent, TvType.Music,
            TvType.AudioBook, TvType.Audio, TvType.Podcast, TvType.Video, TvType.Others
        )
    );

    fun supports(api: MainAPI): Boolean = api.supportedTypes.any { it in types }

    /** Items without a type are kept: many providers leave it empty. */
    fun accepts(item: SearchResponse): Boolean = item.type?.let { it in types } ?: true
}

enum class BrowseSort(@StringRes val title: Int) {
    PROVIDER(R.string.browse_sort_default),
    RATING(R.string.sort_rating_desc),
    NEWEST(R.string.sort_release_date_new),
    TITLE(R.string.sort_alphabetical_a),
}

val SearchResponse.releaseYear: Int?
    get() = when (this) {
        is MovieSearchResponse -> year
        is TvSeriesSearchResponse -> year
        is AnimeSearchResponse -> year
        else -> null
    }

data class BrowseState(
    val category: BrowseCategory = BrowseCategory.MOVIES,
    val apiName: String? = null,
    /** Home page rows of the provider, in provider order. */
    val sections: List<String> = emptyList(),
    /** null means every section. */
    val section: String? = null,
    val sort: BrowseSort = BrowseSort.PROVIDER,
    /** null means every year. */
    val year: Int? = null,
    val years: List<Int> = emptyList(),
    val items: Resource<List<SearchResponse>> = Resource.Loading(),
)

/**
 * Browse lists what a provider shows on its home page, as one grid.
 * Extensions have no filter API, so sorting and the year filter work on what is already loaded.
 */
class BrowseViewModel : ViewModel() {
    private val _state = MutableLiveData(BrowseState())
    val state: LiveData<BrowseState> = _state

    // Written from the main thread and from loaders; LiveData.postValue is async,
    // so the latest state is kept here and read back from here.
    @Volatile
    private var latest = BrowseState()

    private fun emit(new: BrowseState) {
        latest = new
        _state.postValue(new)
    }

    private var rows: List<Pair<String, List<SearchResponse>>> = emptyList()
    private var api: MainAPI? = null
    private var nextPage = 2
    private var hasMore = false
    private var loadJob: Job? = null
    private var loadingMore = false

    private fun current() = latest

    fun setCategory(
        category: BrowseCategory,
        providers: List<MainAPI>,
        preferredApi: String?,
        force: Boolean = false,
    ) {
        val old = current()
        if (!force && old.category == category && api != null) return
        val usable = providers.filter { category.supports(it) }
        val pick = usable.firstOrNull { it.name == preferredApi }
            ?: usable.firstOrNull { it.name == old.apiName }
            ?: usable.firstOrNull()
        emit(BrowseState(category = category, apiName = pick?.name))
        load(pick)
    }

    fun setProvider(provider: MainAPI) {
        emit(BrowseState(category = current().category, apiName = provider.name))
        load(provider)
    }

    fun setSection(section: String?) {
        emit(current().copy(section = section))
        if (section == null) {
            // Rows may hold only the last single section, so fetch them all again.
            load(api)
            return
        }
        // A single row can page further than the combined first page.
        val api = api ?: return
        val index = api.mainPage.indexOfFirst { it.name == section }
        if (index < 0) {
            publish()
            return
        }
        loadJob?.cancel()
        emit(current().copy(items = Resource.Loading()))
        loadJob = ioSafe {
            val result = APIRepository(api).getMainPage(1, index)
            if (result is Resource.Success) {
                rows = collect(result.value)
                hasMore = result.value.any { it?.hasNext == true }
                nextPage = 2
            }
            publish(result as? Resource.Failure)
        }
    }

    fun setSort(sort: BrowseSort) {
        emit(current().copy(sort = sort))
        publish()
    }

    fun setYear(year: Int?) {
        emit(current().copy(year = year))
        publish()
    }

    /** Called near the end of the grid. Only a single section can page. */
    fun loadMore() {
        val section = current().section ?: return
        val api = api ?: return
        if (!hasMore || loadingMore) return
        val index = api.mainPage.indexOfFirst { it.name == section }
        if (index < 0) return
        loadingMore = true
        ioSafe {
            try {
                val result = APIRepository(api).getMainPage(nextPage, index)
                if (result is Resource.Success) {
                    val more = collect(result.value)
                    rows = rows.map { (name, list) ->
                        name to (list + (more.firstOrNull { it.first == name }?.second ?: emptyList()))
                    }
                    hasMore = result.value.any { it?.hasNext == true }
                    nextPage++
                    publish()
                } else {
                    hasMore = false
                }
            } finally {
                loadingMore = false
            }
        }
    }

    fun reload() {
        load(api)
    }

    private fun load(provider: MainAPI?) {
        loadJob?.cancel()
        api = provider
        rows = emptyList()
        hasMore = false
        nextPage = 2
        if (provider == null) {
            emit(current().copy(items = Resource.Success(emptyList())))
            return
        }
        emit(current().copy(items = Resource.Loading()))
        loadJob = ioSafe {
            val result = APIRepository(provider).getMainPage(1, null)
            if (result is Resource.Success) rows = collect(result.value)
            val sections = provider.mainPage.map { it.name }
            emit(current().copy(sections = sections))
            publish(result as? Resource.Failure)
        }
    }

    private fun collect(pages: List<HomePageResponse?>) =
        pages.filterNotNull().flatMap { page -> page.items.map { it.name to it.list } }

    private fun publish(failure: Resource.Failure? = null) {
        val state = current()
        if (failure != null && rows.isEmpty()) {
            emit(state.copy(items = failure))
            return
        }
        val inCategory = rows
            .filter { state.section == null || it.first == state.section }
            .flatMap { it.second }
            .filter { state.category.accepts(it) }
            .distinctBy { it.url }
        val years = inCategory.mapNotNull { it.releaseYear }.distinct().sortedDescending()
        val filtered = inCategory.filter { state.year == null || it.releaseYear == state.year }
        val sorted = when (state.sort) {
            BrowseSort.PROVIDER -> filtered
            BrowseSort.RATING -> filtered.sortedByDescending { it.score?.toDouble() ?: -1.0 }
            BrowseSort.NEWEST -> filtered.sortedByDescending { it.releaseYear ?: Int.MIN_VALUE }
            BrowseSort.TITLE -> filtered.sortedBy { it.name.lowercase() }
        }
        emit(state.copy(years = years, items = Resource.Success(sorted)))
    }
}
