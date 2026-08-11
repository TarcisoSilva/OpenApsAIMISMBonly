// Alterado pelo Tarciso - Tools screen activity
package app.aaps.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import app.aaps.R
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.databinding.ActivityToolsBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import javax.inject.Inject

class ToolsActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var activePlugin: ActivePlugin
    
    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Tools"

        // Setup button click listeners
        setupButtons()
    }

    private fun setupButtons() {
        // Actions button
        binding.actionButton.setOnClickListener {
            navigateToPlugin("ActionsPlugin")
        }

        // Rapid-Acting Oref button
        binding.rapidActingButton.setOnClickListener {
            //InsulinOrefRapidActingPlugin
            // navigateToPlugin("OpenAPSSMBPlugin")
            navigateToPlugin("InsulinOrefRapidActingPlugin")
        }

        // Profile button
        binding.profileButton.setOnClickListener {
            // navigateToPlugin("LocalProfilePlugin")

            navigateToPlugin("ProfilePlugin")
        }

        // Automation button
        binding.automationButton.setOnClickListener {
            navigateToPlugin("AutomationPlugin")
        }

        // NSClient button
        binding.nsclientButton.setOnClickListener {
            navigateToPlugin("NSClientPlugin")
        }

        // Tidepool button
        binding.tidepoolButton.setOnClickListener {
            navigateToPlugin("TidepoolPlugin")
        }

        // Xdrip+ button
        binding.xdripButton.setOnClickListener {
            navigateToPlugin("XdripPlugin")
        }

        // Maintenance button
        binding.maintenanceButton.setOnClickListener {
            navigateToPlugin("MaintenancePlugin")
        }

        // Xdrip+ BG button (same as Xdrip+)
        binding.xdripBgButton.setOnClickListener {
            // XdripSourcePlugin
            // navigateToPlugin("XdripPlugin")

            navigateToPlugin("XdripSourcePlugin")
        }
    }

    private fun navigateToPlugin(pluginSimpleName: String) {
        val pluginIndex = activePlugin.getPluginsList().indexOfFirst { 
            it.javaClass.simpleName == pluginSimpleName 
        }
        
        if (pluginIndex != -1) {
            startActivity(
                Intent(this, SingleFragmentActivity::class.java)
                    .setAction("info.nightscout.androidaps.ToolsActivity")
                    .putExtra("plugin", pluginIndex)
            )
        } else {
            // Plugin not found - try alternative names or show message
            when (pluginSimpleName) {
                "OpenAPSSMBPlugin" -> {
                    // Try alternative names for OpenAPS
                    val altIndex = activePlugin.getPluginsList().indexOfFirst { 
                        it.javaClass.simpleName.contains("OpenAPS") || 
                        it.javaClass.simpleName.contains("SMB")
                    }
                    if (altIndex != -1) {
                        startActivity(
                            Intent(this, SingleFragmentActivity::class.java)
                                .setAction("info.nightscout.androidaps.ToolsActivity")
                                .putExtra("plugin", altIndex)
                        )
                    }
                }
                "XdripPlugin" -> {
                    // Try alternative names for Xdrip
                    val altIndex = activePlugin.getPluginsList().indexOfFirst { 
                        it.javaClass.simpleName.contains("Xdrip") || 
                        it.javaClass.simpleName.contains("xDrip")
                    }
                    if (altIndex != -1) {
                        startActivity(
                            Intent(this, SingleFragmentActivity::class.java)
                                .setAction("info.nightscout.androidaps.ToolsActivity")
                                .putExtra("plugin", altIndex)
                        )
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
