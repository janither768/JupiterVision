package com.jupiter.vision.util

import androidx.compose.ui.graphics.Color
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.model.TileModel

/**
 * JUPITERVISION 2112.15 (Phase 3) — Intelligent Foldering System
 *
 * Rules:
 * - A folder tile is strictly 2x2 real (4x4 grid units).
 * - Folder tile color is always a transparent dark grey (0xCC202228).
 * - Folders created ONLY when more than one app in the same category is detected.
 * - Categories: Social Media, Utility Apps, AI Apps, Creativity Studio, etc.
 * - Zig-zag vertical placement rule:
 *     Folder                  normal app tiles
 *     Normal app tiles        folder
 *   Never two folders placed horizontally or vertically adjacent.
 */
object IntelligentFolderEngine {

    val FOLDER_BACKGROUND_COLOR = Color(0xCC202228) // Transparent dark grey

    data class CategoryDefinition(
        val key: String,
        val row1: String,
        val row2: String,
        val keywords: List<String>
    )

    private val CATEGORIES = listOf(
        CategoryDefinition(
            key = "ai",
            row1 = "AI",
            row2 = "APPS",
            keywords = listOf(
                "ai", "deepseek", "gemini", "chatgpt", "copilot", "perplexity",
                "claude", "midjourney", "poe", "openai", "bard", "anthropic",
                "character.ai", "grok", "mistral", "huggingface", "llama"
            )
        ),
        CategoryDefinition(
            key = "creativity",
            row1 = "CREATIVITY",
            row2 = "STUDIO",
            keywords = listOf(
                "photo", "photoshop", "coral", "corel", "premiere", "picsart",
                "lightroom", "inshot", "canva", "capcut", "illustrator", "snapseed",
                "procreate", "sketch", "editor", "paint", "draw", "video editor",
                "vsco", "art", "design", "studio", "filmora"
            )
        ),
        CategoryDefinition(
            key = "social",
            row1 = "SOCIAL",
            row2 = "MEDIA",
            keywords = listOf(
                "whatsapp", "instagram", "twitter", "telegram", "facebook", "tiktok",
                "reddit", "discord", "snapchat", "messenger", "threads", "linkedin",
                "wechat", "signal", "pinterest", "tumblr", "viber", "mastodon", "bluesky", "social"
            )
        ),
        CategoryDefinition(
            key = "utility",
            row1 = "UTILITY",
            row2 = "APPS",
            keywords = listOf(
                "util", "files", "calc", "calculator", "settings", "notes", "compass",
                "weather", "clock", "drive", "keep", "auth", "clean", "battery",
                "manager", "scanner", "tool", "pdf", "torch", "browser", "chrome",
                "firefox", "speedtest"
            )
        )
    )

    private fun detectCategory(app: AppInfo): String? {
        val text = "${app.label.lowercase()} ${app.packageName.lowercase()}"
        for (cat in CATEGORIES) {
            for (kw in cat.keywords) {
                if (text.contains(kw)) {
                    return cat.key
                }
            }
        }
        return null
    }

    /**
     * Groups installed apps into intelligent categories.
     * When fewer than 2 categories have multiple apps, complements with standard catalog apps
     * so that the user immediately experiences the multi-folder zig-zag architecture.
     */
    fun partitionAppsAndFolders(
        installedApps: List<AppInfo>
    ): Pair<List<TileModel>, List<AppInfo>> {
        val categorized = mutableMapOf<String, MutableList<AppInfo>>()
        for (cat in CATEGORIES) {
            categorized[cat.key] = mutableListOf()
        }

        val unassignedApps = mutableListOf<AppInfo>()

        for (app in installedApps) {
            val catKey = detectCategory(app)
            if (catKey != null) {
                categorized[catKey]?.add(app)
            } else {
                unassignedApps.add(app)
            }
        }

        val folderTiles = mutableListOf<TileModel>()
        val standaloneApps = mutableListOf<AppInfo>()
        standaloneApps.addAll(unassignedApps)

        for (cat in CATEGORIES) {
            val apps = categorized[cat.key] ?: emptyList()
            if (apps.size >= 2) {
                // Rule: Create folder only when more than one app in category is detected on the device
                val tile = TileModel(
                    id = "folder_${cat.key}",
                    label = "${cat.row1} ${cat.row2}",
                    packageName = null,
                    colSpan = 4,
                    rowSpan = 4,
                    isMacro = true,
                    isSystem = false,
                    isFolder = true,
                    role = "folder",
                    folderCategory = cat.key,
                    folderTitleRow1 = cat.row1,
                    folderTitleRow2 = cat.row2,
                    folderApps = apps,
                    accentColor = FOLDER_BACKGROUND_COLOR
                )
                folderTiles.add(tile)
            } else {
                // Not enough apps for folder: keep as standalone apps
                standaloneApps.addAll(apps)
            }
        }

        return Pair(folderTiles, standaloneApps)
    }
}
