package com.jfrashu.taskchat.groupactivities

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.jfrashu.taskchat.R

class GroupActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var invitationsAdapter: GroupInvitationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_drawer)

        drawerLayout = findViewById(R.id.drawerLayout)
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener {
            drawerLayout.open()
        }

        setupInvitations()
    }

    private fun setupInvitations() {
        invitationsAdapter = GroupInvitationsAdapter(
            invitations = listOf(), // Populate from your data source
            onAccept = { invitation -> /* Handle acceptance */ },
            onReject = { invitation -> /* Handle rejection */ }
        )
    }
}