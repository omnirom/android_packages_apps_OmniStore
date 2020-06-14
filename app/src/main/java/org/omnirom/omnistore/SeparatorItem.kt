package org.omnirom.omnistore

class SeparatorItem(val separatorTitle: String) : ListItem {
    private val TAG = "OmniStore:SeparatorItem"

    override fun title(): String {
        return separatorTitle
    }

    override fun sortOrder(): Int {
        return -1
    }
}