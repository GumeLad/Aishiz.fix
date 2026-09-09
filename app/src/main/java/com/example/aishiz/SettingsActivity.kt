package com.example.aishiz

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aishiz.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.switchAutoScroll.isChecked = settings.autoScroll
        binding.switchKeepAwake.isChecked = settings.keepScreenAwake
        binding.streamInterval.value = settings.streamIntervalMs.toFloat()
        updateIntervalLabel(binding.streamInterval.value)

        binding.switchAutoScroll.setOnCheckedChangeListener { _, checked ->
            settings.autoScroll = checked
        }

        binding.switchKeepAwake.setOnCheckedChangeListener { _, checked ->
            settings.keepScreenAwake = checked
        }

        binding.streamInterval.addOnChangeListener { _, value, fromUser ->
            updateIntervalLabel(value)
            if (fromUser) settings.streamIntervalMs = value.toLong()
        }

        binding.clearModelCache.setOnClickListener {
            lifecycleScope.launch {
                binding.clearModelCache.isEnabled = false
                val cleared = withContext(Dispatchers.IO) {
                    ModelStorage.clearCache(this@SettingsActivity)
                }
                binding.clearModelCache.isEnabled = true
                Toast.makeText(
                    this@SettingsActivity,
                    if (cleared) R.string.model_cache_cleared else R.string.model_cache_clear_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateIntervalLabel(value: Float) {
        binding.streamIntervalLabel.text =
            getString(R.string.stream_interval_value, value.toInt())
    }
}
