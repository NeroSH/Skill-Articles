package ru.skillbranch.skillarticles.ui

import android.os.Bundle
import android.text.Selection
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import ru.skillbranch.skillarticles.R
import ru.skillbranch.skillarticles.ui.delegates.AttrValue
import ru.skillbranch.skillarticles.ui.delegates.viewBinding
import ru.skillbranch.skillarticles.databinding.ActivityRootBinding
import ru.skillbranch.skillarticles.extensions.dpToIntPx
import ru.skillbranch.skillarticles.extensions.setMarginOptionally
import ru.skillbranch.skillarticles.ui.custom.SearchFocusSpan
import ru.skillbranch.skillarticles.ui.custom.SearchSpan
import ru.skillbranch.skillarticles.viewmodels.*

class RootActivity : AppCompatActivity(), IArticleView {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var viewModelFactory: ViewModelProvider.Factory = ViewModelFactory(this, "0")
    private val viewModel: ArticleViewModel by viewModels { viewModelFactory }

    private val vb by viewBinding(ActivityRootBinding::inflate)

    private val vbBottombar
        get() = vb.bottombar.binding
    private val vbSubMenu
        get() = vb.submenu.binding
    private lateinit var searchView: SearchView

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val bgColor by AttrValue(R.attr.colorSecondary)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val fgColor by AttrValue(R.attr.colorOnSecondary)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupToolbar()
        setupBottombar()
        setupSubmenu()

        viewModel.observeState(this, ::renderUi)
        viewModel.observeSubState(this, ArticleState::toBottombarData, ::renderBotombar)
        viewModel.observeSubState(this, ArticleState::toSubmenuData, ::renderSubmenu)

        viewModel.observeNotifications(this, ::renderNotification)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        val menuItem = menu.findItem(R.id.action_search)
        searchView = (menuItem.actionView as SearchView)
        searchView.queryHint = getString(R.string.article_search_placeholder)

        //restore
        if (viewModel.currentState.isSearch) {
            menuItem.expandActionView()
            searchView.setQuery(viewModel.currentState.searchQuery, false)
            searchView.requestFocus()
        } else {
            searchView.clearFocus()
        }
        menuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                viewModel.handleSearchMode(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                viewModel.handleSearchMode(false)
                return true
            }

        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.handleSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.handleSearch(newText)
                return true
            }

        })

        return super.onCreateOptionsMenu(menu)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.saveState()
        super.onSaveInstanceState(outState)
    }

    private fun renderNotification(notify: Notify) {
        val snackbar = Snackbar.make(vb.coordinatorContainer, notify.message, Snackbar.LENGTH_SHORT)
            .setAnchorView(vb.bottombar)

        when (notify) {
            is Notify.ActionMessage -> {
                val (_, label, handler) = notify
                with(snackbar) {
                    setActionTextColor(getColor(R.color.color_accent_dark))
                    setAction(label) { handler.invoke() }
                }
            }
            is Notify.ErrorMessage -> {
                val (_, label, handler) = notify
                with(snackbar) {
                    setBackgroundTint(getColor(R.color.design_default_color_error))
                    setTextColor(getColor(android.R.color.white))
                    setActionTextColor(getColor(android.R.color.white))
                    handler ?: return@with
                    setAction(label) { handler.invoke() }
                }
            }
            is Notify.TextMessage -> {/*nothing*/
            }
        }

        snackbar.show()
    }

    override fun setupSubmenu() {
        with(vbSubMenu) {
            btnTextUp.setOnClickListener { viewModel.handleUpText() }
            btnTextDown.setOnClickListener { viewModel.handleDownText() }
            switchMode.setOnClickListener { viewModel.handleNightMode() }
        }
    }

    override fun setupBottombar() {
        with(vbBottombar) {
            btnLike.setOnClickListener { viewModel.handleLike() }
            btnBookmark.setOnClickListener { viewModel.handleBookmark() }
            btnShare.setOnClickListener { viewModel.handleShare() }
            btnSettings.setOnClickListener { viewModel.handleToggleMenu() }

            btnResultUp.setOnClickListener {
                searchView.clearFocus()
                vb.tvTextContent.requestFocus()
                viewModel.handleUpResult()
            }
            btnResultDown.setOnClickListener {
                searchView.clearFocus()
                vb.tvTextContent.requestFocus()
                viewModel.handleDownResult()
            }
            btnSearchClose.setOnClickListener {
                viewModel.handleSearchMode(false)
                invalidateOptionsMenu()
            }
        }
    }

    override fun renderBotombar(data: BottombarData) {
        with(vbBottombar) {
            btnSettings.isChecked = data.isShowMenu
            btnLike.isChecked = data.isLike
            btnBookmark.isChecked = data.isBookmark
        }

        if (data.isSearch) showSearchBar(data.resultsCount, data.searchPosition)
        else hideSearchBar()
    }

    override fun renderSubmenu(data: SubmenuData) {
        with(vbSubMenu) {
            switchMode.isChecked = data.isDarkMode
            btnTextDown.isChecked = !data.isBigText
            btnTextUp.isChecked = data.isBigText
        }

        if (data.isShowMenu) vb.submenu.open() else vb.submenu.close()
    }

    override fun renderUi(data: ArticleState) {
        delegate.localNightMode =
            if (data.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

        with(vb.tvTextContent) {
            textSize = if (data.isBigText) 18f else 14f
            movementMethod = ScrollingMovementMethod()
            val content = if (data.isLoadingContent) "loading" else data.content.first()
            if (text.toString() == content) return@with
            setText(content, TextView.BufferType.SPANNABLE)
        }

        with(vb.toolbar) {
            title = data.title ?: "loading"
            subtitle = data.category ?: "loading"
            if (data.categoryIcon != null) logo =
                ContextCompat.getDrawable(this@RootActivity, data.categoryIcon as Int)
        }

        if (data.isLoadingContent) return

        if (data.isSearch) {
            renderSearchResult(data.searchResults)
            renderSearchPosition(data.searchPosition)
        } else {
            clearSearchResult()
        }
    }

    override fun setupToolbar() {
        setSupportActionBar(vb.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val logo = vb.toolbar.children.find { it is AppCompatImageView } as? ImageView
        logo ?: return
        logo.scaleType = ImageView.ScaleType.CENTER_CROP
        val lp = logo.layoutParams as? Toolbar.LayoutParams
        lp?.let {
            it.width = this.dpToIntPx(40)
            it.height = this.dpToIntPx(40)
            it.marginEnd = this.dpToIntPx(16)
            logo.layoutParams = it
        }
    }

    override fun renderSearchResult(searchResult: List<Pair<Int, Int>>) {
        val content = vb.tvTextContent.text.toSpannable()
        clearSearchResult()
        searchResult.forEach { (start, end) ->
            content.setSpan(
                SearchSpan(bgColor, fgColor),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    override fun renderSearchPosition(searchPosition: Int) {
        val content = vb.tvTextContent.text.toSpannable()
        val spans = content.getSpans<SearchSpan>()
        content.getSpans<SearchFocusSpan>().forEach { content.removeSpan(it) }
        if (spans.isNotEmpty()) {
            val result = spans[searchPosition]
            Selection.setSelection(content, content.getSpanStart(result))
            content.setSpan(
                SearchFocusSpan(bgColor, fgColor),
                content.getSpanStart(result),
                content.getSpanEnd(result),
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    override fun clearSearchResult() {
        val content = vb.tvTextContent.text.toSpannable()
        content.getSpans<SearchSpan>()
            .forEach { content.removeSpan(it) }
    }

    override fun showSearchBar(resultsCount: Int, searchPosition: Int) {
        with(vb.bottombar) {
            setSearchState(true)
            setSearchInfo(resultsCount, searchPosition)
        }
        vb.scroll.setMarginOptionally(bottom = vb.bottombar.height)
    }

    override fun hideSearchBar() {
        with(vb.bottombar) {
            setSearchState(false)
        }
        vb.scroll.setMarginOptionally(bottom = 0)
    }

}
