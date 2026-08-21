package id.bits.box.ui

import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import id.bits.box.R
import id.bits.box.database.DataStore
import id.bits.box.databinding.LayoutAssetItemBinding
import id.bits.box.databinding.LayoutAssetsBinding
import id.bits.box.ktx.*
import id.bits.box.widget.UndoSnackbarManager
import libcore.Libcore
import id.bits.box.utils.Util
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class AssetsActivity : ThemedActivity() {

    lateinit var adapter: AssetAdapter
    lateinit var layout: LayoutAssetsBinding
    lateinit var undoManager: UndoSnackbarManager<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutAssetsBinding.inflate(layoutInflater)
        layout = binding
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.route_assets)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.recyclerView.layoutManager = FixedLinearLayoutManager(binding.recyclerView)
        adapter = AssetAdapter()
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setOnRefreshListener {
            adapter.reloadAssets()
            binding.refreshLayout.isRefreshing = false
        }
        binding.refreshLayout.setColorSchemeColors(getColorAttr(R.attr.primaryOrTextPrimary))

        undoManager = UndoSnackbarManager(this, adapter)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.START
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                val index = viewHolder.bindingAdapterPosition
                if (index < assetNames.size) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                adapter.remove(index)
                undoManager.remove(index to (viewHolder as AssetHolder).file)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

        }).attachToRecyclerView(binding.recyclerView)
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(layout.coordinator, text, Snackbar.LENGTH_LONG)
    }

    val assetNames = arrayOf("geoip.db", "geosite.db", "geoip-id.srs", "geosite-rule-ads.srs", "geosite-rule-indo.srs")

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.import_asset_menu, menu)
        return true
    }

    val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) {
            val fileName = contentResolver.query(file, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }?.takeIf { it.isNotBlank() } ?: file.pathSegments.last()
                .substringAfterLast('/')
                .substringAfter(':')

            if (!fileName.endsWith(".db") && !fileName.endsWith(".srs")) {
                alert(getString(R.string.route_not_asset, fileName)).show()
                return@registerForActivityResult
            }
            val filesDir = getExternalFilesDir(null) ?: filesDir

            runOnDefaultDispatcher {
                val outFile = File(filesDir, fileName).apply {
                    parentFile?.mkdirs()
                }

                contentResolver.openInputStream(file)?.use(outFile.outputStream())

                File(outFile.parentFile, "${outFile.nameWithoutExtension}.version.txt").apply {
                    if (isFile) delete()
                    createNewFile()
                    val fw = FileWriter(this)
                    fw.write("Custom")
                    fw.close()
                }

                adapter.reloadAssets()
            }

        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
                return true
            }
        }
        return false
    }

    inner class AssetAdapter : RecyclerView.Adapter<AssetHolder>(),
        UndoSnackbarManager.Interface<File> {

        val assets = ArrayList<File>()

        init {
            reloadAssets()
        }

        fun reloadAssets() {
            val filesDir = getExternalFilesDir(null) ?: filesDir
            val files = filesDir.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(".db") || it.name.endsWith(".srs")) && it.name !in assetNames }
            assets.clear()
            assets.add(File(filesDir, "geoip.db"))
            assets.add(File(filesDir, "geosite.db"))
            assets.add(File(filesDir, "geoip-id.srs"))
            assets.add(File(filesDir, "geosite-rule-ads.srs"))
            assets.add(File(filesDir, "geosite-rule-indo.srs"))
            if (files != null) assets.addAll(files)

            layout.refreshLayout.post {
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetHolder {
            return AssetHolder(LayoutAssetItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: AssetHolder, position: Int) {
            holder.bind(assets[position])
        }

        override fun getItemCount(): Int {
            return assets.size
        }

        fun remove(index: Int) {
            assets.removeAt(index)
            notifyItemRemoved(index)
        }

        override fun undo(actions: List<Pair<Int, File>>) {
            for ((index, item) in actions) {
                assets.add(index, item)
                notifyItemInserted(index)
            }
        }

        override fun commit(actions: List<Pair<Int, File>>) {
            val groups = actions.map { it.second }.toTypedArray()
            runOnDefaultDispatcher {
                groups.forEach { it.deleteRecursively() }
            }
        }

    }

    val updating = AtomicInteger()

    inner class AssetHolder(val binding: LayoutAssetItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        lateinit var file: File

        fun bind(file: File) {
            this.file = file

            binding.assetName.text = file.name
            val versionFile = File(file.parentFile, "${file.nameWithoutExtension}.version.txt")

            val isInstalled = file.isFile()
            val localVersion = if (isInstalled) {
                if (versionFile.isFile) {
                    try {
                        versionFile.readText().trim()
                    } catch (e: Throwable) {
                        snackbar(e.readableMessage)
                        "<unknown>"
                    }
                } else {
                    "Unknown-" + DateFormat.getDateFormat(app).format(Date(file.lastModified()))
                }
            } else {
                "<unknown>"
            }

            binding.assetStatus.text = getString(R.string.route_asset_status, localVersion)

            // Show Update/Download button only for default assets
            val isDefaultAsset = file.name in assetNames
            binding.rulesUpdate.isInvisible = !isDefaultAsset
            
            if (isDefaultAsset) {
                // Different icon/text for installed vs not installed
                if (isInstalled) {
                    binding.rulesUpdate.setImageResource(R.drawable.ic_baseline_update_24)
                    binding.rulesUpdate.contentDescription = getString(R.string.group_update)
                } else {
                    binding.rulesUpdate.setImageResource(R.drawable.ic_baseline_download_24)
                    binding.rulesUpdate.contentDescription = getString(R.string.group_download)
                }
                
                binding.rulesUpdate.setOnClickListener {
                    updating.incrementAndGet()
                    layout.refreshLayout.isEnabled = false
                    binding.subscriptionUpdateProgress.isInvisible = false
                    binding.rulesUpdate.isInvisible = true
                    runOnDefaultDispatcher {
                        runCatching {
                            updateAsset(file, versionFile, localVersion)
                        }.onFailure {
                            onMainDispatcher {
                                alert(it.readableMessage).tryToShow()
                            }
                        }

                        onMainDispatcher {
                            binding.rulesUpdate.isInvisible = false
                            binding.subscriptionUpdateProgress.isInvisible = true
                            if (updating.decrementAndGet() == 0) {
                                layout.refreshLayout.isEnabled = true
                            }
                        }
                    }
                }
            }

        }

    }

    // Satu source resmi (bitscoid/BITS-*); perbedaan hanya pada variant aset.
    private val ruleAssetRepo = mapOf(
        "geoip.db" to "bitscoid/BITS-GeoIP",
        "geosite.db" to "bitscoid/BITS-GeoSite",
        "geoip-id.srs" to "bitscoid/BITS-GeoIP",
        "geosite-rule-ads.srs" to "bitscoid/BITS-GeoSite",
        "geosite-rule-indo.srs" to "bitscoid/BITS-GeoSite",
    )

    // Nama aset upstream untuk tiap variant (0 = minimal, 1 = full).
    // Hasil download selalu disimpan dengan nama kanonik (geoip.db / geosite.db).
    // .srs files are the same for both variants — only the minimal set is needed.
    private val ruleAssetVariantNames = mapOf(
        0 to mapOf(
            "geoip.db" to "geoip-min.db",
            "geosite.db" to "geosite-min.db",
            "geoip-id.srs" to "geoip-id.srs",
            "geosite-rule-ads.srs" to "geosite-rule-ads.srs",
            "geosite-rule-indo.srs" to "geosite-rule-indo.srs",
        ),
        1 to mapOf(
            "geoip.db" to "geoip.db",
            "geosite.db" to "geosite.db",
            "geoip-id.srs" to "geoip-id.srs",
            "geosite-rule-ads.srs" to "geosite-rule-ads.srs",
            "geosite-rule-indo.srs" to "geosite-rule-indo.srs",
        ),
    )

    suspend fun updateAsset(file: File, versionFile: File, localVersion: String) {
        var fileName = file.name

        val repo = ruleAssetRepo[fileName] ?: error("Unknown asset $fileName")
        val variant = DataStore.rulesVariant
        val upstreamAsset = ruleAssetVariantNames[variant]?.get(fileName) ?: fileName
        // Track variant per-file: geoip dan geosite bisa di-update terpisah,
        // jadi ganti variant di tengah proses harus tetap memicu download ulang
        // file yang belum diperbarui.
        val downloadedVariant =
            if (fileName.startsWith("geoip")) DataStore.geoipDownloadedVariant
            else DataStore.geositeDownloadedVariant

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }

        try {
            var response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
            }.execute()

            val release = JSONObject(Util.getStringBox(response.contentString))
            val tagName = release.optString("tag_name")

            if (tagName == localVersion && downloadedVariant == variant) {
                onMainDispatcher {
                    snackbar(R.string.route_asset_no_update).show()
                }
                return
            }

            val releaseAssets = release.getJSONArray("assets").filterIsInstance<JSONObject>()
            val assetToDownload = releaseAssets.find { it.getStr("name") == upstreamAsset }
                ?: error("File $upstreamAsset not found in release ${release["url"]}")
            val browserDownloadUrl = assetToDownload.getStr("browser_download_url")

            response = client.newRequest().apply {
                setURL(browserDownloadUrl)
            }.execute()

            val cacheFile = File(file.parentFile, "${file.name}.tmp")
            cacheFile.parentFile?.mkdirs()

            response.writeTo(cacheFile.canonicalPath)

            if (fileName.endsWith(".xz")) {
                Libcore.unxz(cacheFile.absolutePath, file.absolutePath)
                cacheFile.delete()
            } else {
                cacheFile.renameTo(file)
            }

            versionFile.writeText(tagName)
            if (fileName.startsWith("geoip")) DataStore.geoipDownloadedVariant = variant
            else DataStore.geositeDownloadedVariant = variant

            adapter.reloadAssets()

            onMainDispatcher {
                snackbar(R.string.route_asset_updated).show()
            }
        } finally {
            client.close()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.reloadAssets()
        }
    }
}
