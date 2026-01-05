package com.logseq.kmp.ui.i18n

import androidx.compose.runtime.*

enum class Language(val code: String, val label: String) {
    ENGLISH("en", "English"),
    CHINESE("zh", "简体中文")
}

private val translations = mapOf(
    Language.ENGLISH to mapOf(
        "welcome" to "Welcome to Logseq",
        "onboarding.welcome.title" to "Welcome to Logseq",
        "onboarding.welcome.desc" to "A privacy-first knowledge management platform.",
        "onboarding.graph.title" to "Select Your Graph",
        "onboarding.graph.desc" to "Choose where your data will be stored.",
        "onboarding.keymap.title" to "Keymap Introduction",
        "onboarding.keymap.desc" to "Learn the essential shortcuts.",
        "onboarding.finish" to "Get Started",
        "onboarding.next" to "Next",
        "onboarding.back" to "Back",
        "settings.language" to "Language",
        "common.cancel" to "Cancel",
        "common.save" to "Save",
        "common.theme" to "Theme",
        "common.settings" to "Settings",
        "common.namespace" to "Namespace",
        "common.content_placeholder" to "This is the content of the",
        "common.graph_location" to "Graph Location",
        "common.getting_started" to "Getting Started",
        "menu.file" to "File",
        "menu.switch_graph" to "Switch Graph",
        "menu.edit" to "Edit",
        "menu.view" to "View",
        "menu.help" to "Help",
        "help.sidebar_click" to "Click a page in the sidebar to open it",
        "help.search_shortcut" to "Press Ctrl+K to search or create pages",
        "help.new_page_shortcut" to "Press Ctrl+N to create a new page",
        "help.toggle_sidebar_shortcut" to "Press Ctrl+B to toggle the sidebar",
        "status.encrypted" to "Encrypted",
        "status.not_encrypted" to "Not Encrypted",
        "status.plugins_active" to "plugins active"
    ),
    Language.CHINESE to mapOf(
        "welcome" to "欢迎使用 Logseq",
        "onboarding.welcome.title" to "欢迎使用 Logseq",
        "onboarding.welcome.desc" to "隐私优先的知识管理平台。",
        "onboarding.graph.title" to "选择您的图谱",
        "onboarding.graph.desc" to "选择您的数据存储位置。",
        "onboarding.keymap.title" to "快捷键介绍",
        "onboarding.keymap.desc" to "学习常用快捷键。",
        "onboarding.finish" to "开始使用",
        "onboarding.next" to "下一步",
        "onboarding.back" to "上一步",
        "settings.language" to "语言",
        "common.cancel" to "取消",
        "common.save" to "保存",
        "common.theme" to "主题",
        "common.settings" to "设置",
        "common.namespace" to "命名空间",
        "common.content_placeholder" to "这是页面的内容：",
        "common.graph_location" to "图谱位置",
        "common.getting_started" to "开始使用",
        "menu.file" to "文件",
        "menu.switch_graph" to "切换图谱",
        "menu.edit" to "编辑",
        "menu.view" to "视图",
        "menu.help" to "帮助",
        "help.sidebar_click" to "点击侧边栏中的页面以打开它",
        "help.search_shortcut" to "按 Ctrl+K 搜索或创建页面",
        "help.new_page_shortcut" to "按 Ctrl+N 创建新页面",
        "help.toggle_sidebar_shortcut" to "按 Ctrl+B 切换侧边栏",
        "status.encrypted" to "已加密",
        "status.not_encrypted" to "未加密",
        "status.plugins_active" to "个插件已激活"
    )
)

class I18n(val language: Language) {
    fun t(key: String): String {
        return translations[language]?.get(key) ?: key
    }
}

val LocalI18n = staticCompositionLocalOf { I18n(Language.ENGLISH) }

@Composable
fun t(key: String): String {
    return LocalI18n.current.t(key)
}
