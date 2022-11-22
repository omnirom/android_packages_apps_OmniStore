/*
 *  Copyright (C) 2020 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnistore

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import org.omnirom.omnistore.databinding.AppInfoDialogBinding
import org.omnirom.omnistore.databinding.AppListItemBinding
import org.omnirom.omnistore.databinding.SeparatorListItemBinding


class AppAdapter(val items: List<ListItem>, val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val TYPE_APP_ITEM = 0
    val TYPE_SEPARATOR_ITEM = 1
    val bgShape: Drawable

    init {
        bgShape = context.resources.getDrawable(R.drawable.app_list_item_bg_shape, null)
        bgShape.alpha = 120
    }

    inner class AppItemViewHolder(mBinding: AppListItemBinding) :
        RecyclerView.ViewHolder(mBinding.root) {
        val title = mBinding.appName
        val logo = mBinding.appLogo
        val download = mBinding.appDownload
        val pkg = mBinding.appPkg
        val progress = mBinding.appProgress
        val note = mBinding.appNote
        val version = mBinding.appVersion
        val container = mBinding.appListItemContainer

        fun bind(app: AppItem) {
            itemView.setOnClickListener {
                val builder = AlertDialog.Builder(context)
                builder.setTitle(context.getString(R.string.dialog_app_info_title))

                val dialogBinding = AppInfoDialogBinding.inflate(LayoutInflater.from(context))
                dialogBinding.appTitle.text = app.title()
                dialogBinding.appPkg.text = app.packageName

                var version = app.versionNameCurrent()
                if (app.updateAvailable()) {
                    version = app.versionNameInstalled
                    dialogBinding.appUpdateTitle.visibility = View.VISIBLE
                    val appUpdate = dialogBinding.appUpdate
                    appUpdate.visibility = View.VISIBLE
                    appUpdate.text = app.versionName
                }
                dialogBinding.appVersion.text = version
                dialogBinding.appStatus.text = getStatusString(app)

                val appDescription = dialogBinding.appDescription
                if (app.description().isNotEmpty()) {
                    appDescription.visibility = View.VISIBLE
                    appDescription.text = app.description()
                } else if (app.note().isNotEmpty()) {
                    appDescription.visibility = View.VISIBLE
                    appDescription.text = app.note()
                }
                builder.setView(dialogBinding.root)
                builder.setPositiveButton(android.R.string.ok, null)
                if (app.appSettingsEnabled()) {
                    builder.setNeutralButton(
                        R.string.menu_item_settings
                    ) { _, _ ->
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:" + app.packageName)
                        it?.context?.startActivity(intent)
                    }
                }
                builder.create().show()
            }
            note.setOnClickListener {
                val builder = AlertDialog.Builder(context)
                builder.setTitle(app.title())
                builder.setMessage(app.note())
                builder.setPositiveButton(android.R.string.ok, null)
                builder.create().show()
            }
            download.setOnClickListener {
                if (app.mDownloadId == -1L) {
                    (context as MainActivity).downloadApp(app)
                } else {
                    (context as MainActivity).cancelDownloadApp(app)
                }
            }
        }
    }


    inner class SeparatorItemViewHolder(mBinding: SeparatorListItemBinding) :
        RecyclerView.ViewHolder(mBinding.root) {
        val title = mBinding.separatorTitle
    }


    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        if (items[position] is SeparatorItem) {
            return TYPE_SEPARATOR_ITEM
        }
        return TYPE_APP_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_SEPARATOR_ITEM) {
            return SeparatorItemViewHolder(
                SeparatorListItemBinding.inflate(
                    LayoutInflater.from(
                        context
                    ), parent, false
                )
            )
        }
        return AppItemViewHolder(
            AppListItemBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppItemViewHolder) {
            val app: AppItem = items[position] as AppItem
            holder.bind(app)
            holder.title.text = app.title()
            Picasso.with(context).load(app.iconUrl())
                .error(R.drawable.ic_warning).into(holder.logo)
            holder.pkg.text = app.packageName
            holder.note.visibility = View.GONE

            if (app.mDownloadId != -1L) {
                holder.download.setImageResource(R.drawable.ic_cancel)
                holder.download.visibility = View.VISIBLE
                holder.progress.visibility = View.VISIBLE
            } else {
                if (app.installEnabled()) {
                    holder.download.setImageResource(R.drawable.ic_download_outline)
                    holder.download.visibility = View.VISIBLE
                } else {
                    holder.download.visibility = View.GONE
                }
                holder.progress.visibility = View.GONE
            }
            holder.version.text =
                context.resources.getString(R.string.app_item_version) + " " + app.versionNameCurrent()

            if (app.note().isNotEmpty()) {
                holder.note.visibility = View.VISIBLE
            }
            holder.container.background = bgShape
        } else if (holder is SeparatorItemViewHolder) {
            val separator: SeparatorItem = items[position] as SeparatorItem
            holder.title.text = separator.title()
        }
    }

    private fun getStatusString(app: AppItem): String {
        return when {
            app.updateAvailable() -> {
                context.getString(R.string.status_update_available)
            }
            app.appInstalled() -> {
                context.getString(R.string.status_installed)
            }
            app.appNotInstaled() -> {
                context.getString(R.string.status_not_installed)
            }
            app.appDisabled() -> {
                context.getString(R.string.status_disabled)
            }
            else -> ""
        }
    }
}