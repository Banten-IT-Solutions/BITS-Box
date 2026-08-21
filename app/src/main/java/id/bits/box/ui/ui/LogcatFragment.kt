package id.bits.box.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.bits.box.R
import id.bits.box.databinding.LayoutLogcatBinding
import id.bits.box.databinding.LayoutLogRowBinding
import id.bits.box.ktx.*
import id.bits.box.utils.SendLog
import id.bits.box.widget.ListListener
import libcore.Libcore

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    lateinit var binding: LayoutLogcatBinding

    private val adapter = LogAdapter()

    /** All parsed lines, before filtering. */
    private var allLines = emptyList<LogEntry>()

    /** Current level filter (ALL shows everything). */
    private var filter = Filter.ALL

    /** Follow new logs automatically; disabled when the user scrolls up. */
    private var autoScroll = true

    private enum class Level { INFO, WARN, ERROR, DEFAULT }

    private enum class Filter { ALL, INFO, WARN, ERROR }

    private data class LogEntry(val level: Level, val time: String?, val message: String)

    private val timeRegex = Regex("""^(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)\s+(.*)$""")

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)
        setNavigationIcon(R.drawable.ic_baseline_bug_report_24)

        toolbar.inflateMenu(R.menu.logcat_menu)
        (toolbar.menu as? androidx.appcompat.view.menu.MenuBuilder)
            ?.setOptionalIconsVisible(true)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)

        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = adapter
        binding.logList.setHasFixedSize(false)

        // Smart auto-scroll: keep following the latest logs until the user scrolls up.
        binding.logList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (adapter.itemCount == 0) return
                if (!rv.canScrollVertically(1)) {
                    // At the bottom: resume following.
                    autoScroll = true
                    binding.fabBottom.hide()
                } else {
                    // User scrolled up: pause following and offer a jump button.
                    autoScroll = false
                    binding.fabBottom.show()
                }
            }
        })

        binding.fabBottom.setOnClickListener {
            autoScroll = true
            binding.fabBottom.hide()
            scrollToBottom()
        }

        binding.filterGroup.setOnCheckedStateChangeListener { _, _ ->
            filter = when (binding.filterGroup.checkedChipId) {
                R.id.chip_info -> Filter.INFO
                R.id.chip_warn -> Filter.WARN
                R.id.chip_error -> Filter.ERROR
                else -> Filter.ALL
            }
            applyFilter()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        reloadSession()
    }

    private fun getColorForLevel(level: Level): Int = when (level) {
        Level.ERROR -> ContextCompat.getColor(requireContext(), R.color.color_log_error)
        Level.WARN -> ContextCompat.getColor(requireContext(), R.color.color_log_warn)
        Level.INFO -> ContextCompat.getColor(requireContext(), R.color.color_log_info)
        Level.DEFAULT -> ContextCompat.getColor(requireContext(), R.color.color_log_default)
    }

    private fun iconForLevel(level: Level): Int = when (level) {
        Level.ERROR -> R.drawable.ic_baseline_error_24
        Level.WARN -> R.drawable.ic_baseline_warning_24
        Level.INFO -> R.drawable.ic_baseline_info_24
        Level.DEFAULT -> R.drawable.ic_baseline_fiber_manual_record_24
    }

    private fun levelOf(line: String): Level {
        val upper = line.uppercase()
        return when {
            upper.contains("ERROR") -> Level.ERROR
            upper.contains("WARN") -> Level.WARN
            upper.contains("INFO") -> Level.INFO
            else -> Level.DEFAULT
        }
    }

    private fun parseLine(raw: String): LogEntry? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        val match = timeRegex.matchEntire(line)
        val time = match?.groupValues?.get(1)
        val message = match?.groupValues?.get(2)?.trim() ?: line
        return LogEntry(levelOf(line), time, message)
    }

    private fun parseLog(text: String): List<LogEntry> =
        text.lines().mapNotNull { parseLine(it) }

    private fun reloadSession() {
        val text = String(SendLog.getBITSBoxLog(50 * 1024))
        allLines = parseLog(text)
        applyFilter()
        if (autoScroll) scrollToBottom()
    }

    private fun applyFilter() {
        val filtered = when (filter) {
            Filter.ALL -> allLines
            else -> allLines.filter { it.level.name == filter.name }
        }
        adapter.submit(filtered)
        val isEmpty = filtered.isEmpty()
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.logList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (!isEmpty && autoScroll) scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.logList.post {
            if (adapter.itemCount > 0) {
                binding.logList.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                runOnDefaultDispatcher {
                    try {
                        Libcore.bitsBoxLogClear()
                        Runtime.getRuntime().exec("/system/bin/logcat -c")
                    } catch (e: Exception) {
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                        return@runOnDefaultDispatcher
                    }
                    onMainDispatcher {
                        allLines = emptyList()
                        applyFilter()
                    }
                }
            }

            R.id.action_copy_logcat -> {
                val text = buildString {
                    for (entry in adapter.currentItems()) {
                        if (entry.time != null) append(entry.time).append(" ")
                        append(entry.message).append('\n')
                    }
                }
                if (text.isBlank()) {
                    snackbar(getString(R.string.log_empty_to_copy)).show()
                } else {
                    val clipboard = requireContext().getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("BITS Box log", text))
                    snackbar(getString(R.string.log_copied)).show()
                }
            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "BITS Box")
                }
            }

            R.id.action_refresh -> {
                reloadSession()
            }
        }
        return true
    }

    /** RecyclerView adapter rendering one card per log line. */
    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

        private var items = emptyList<LogEntry>()

        fun submit(list: List<LogEntry>) {
            items = list
            notifyDataSetChanged()
        }

        fun currentItems(): List<LogEntry> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            return VH(LayoutLogRowBinding.inflate(inflater, parent, false))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(val row: LayoutLogRowBinding) : RecyclerView.ViewHolder(row.root) {
            fun bind(entry: LogEntry) {
                val color = getColorForLevel(entry.level)
                row.accentBar.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                row.levelIcon.setImageResource(iconForLevel(entry.level))
                row.levelIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
                if (entry.time != null) {
                    row.timestamp.visibility = View.VISIBLE
                    row.timestamp.text = entry.time
                } else {
                    row.timestamp.visibility = View.GONE
                }
                row.message.text = entry.message
            }
        }
    }
}