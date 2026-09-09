package com.example.aishiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aishiz.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNewChat.setOnClickListener {
            openMain(Intent().putExtra(EXTRA_NEW_CHAT, true))
        }

        binding.btnPreviousChats.setOnClickListener {
            openMain(Intent().putExtra(EXTRA_OPEN_LEFT_TAB, TAB_CHATS))
        }

        binding.btnModels.setOnClickListener {
            openMain(Intent().putExtra(EXTRA_OPEN_LEFT_TAB, TAB_MODELS))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun openMain(extras: Intent) {
        extras.setClass(this, MainActivity::class.java)
        startActivity(extras)
    }
}
