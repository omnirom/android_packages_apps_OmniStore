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
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import org.omnirom.omnistore.Constants.TYPE_APP_ITEM
import org.omnirom.omnistore.Constants.TYPE_SEPARATOR_ITEM


class AppAdapter(val items: ArrayList<ListItem>, val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val mPrefs =
        PreferenceManager.getDefaultSharedPreferences(context)

    inner class AppItemViewHolder : RecyclerView.ViewHolder {
        lateinit var app: AppItem
        var title: TextView
        var logo: ImageView
        var image: ImageView
        var pkg: TextView
        var progress: ProgressBar
        var note: ImageView
        var version: TextView

        constructor(view: View) : super(view) {
            title = view.findViewById(R.id.app_name)
            logo = view.findViewById(R.id.app_logo)
            image = view.findViewById(R.id.app_image)
            pkg = view.findViewById(R.id.app_pkg)
            progress = view.findViewById(R.id.app_progress)
            note = view.findViewById(R.id.app_note)
            version = view.findViewById(R.id.app_version)
            view.setOnClickListener {
                val themeContext = ContextThemeWrapper(context, R.style.Theme_AlertDialog)
                val builder = AlertDialog.Builder(themeContext)
                builder.setTitle(context.getString(R.string.dialog_app_info_title))

                val v =
                    LayoutInflater.from(themeContext).inflate(R.layout.app_info_dialog, null, false)
                v.findViewById<TextView>(R.id.app_title).text = app.title()
                v.findViewById<TextView>(R.id.app_pkg).text = app.packageName

                var version = ""
                if (app.updateAvailable()) {
                    version = app.versionName?:""
                    v.findViewById<View>(R.id.app_update_title).visibility = View.VISIBLE
                    val appUpdate = v.findViewById<TextView>(R.id.app_update)
                    appUpdate.visibility = View.VISIBLE
                    appUpdate.text = version
                } else if (app.appNotInstaled()) {
                    version = app.versionName?:""
                } else if (app.appInstalled() or app.appDisabled()) {
                    version = app.mVersionNameInstalled
                }
                v.findViewById<TextView>(R.id.app_version).text = version
                v.findViewById<TextView>(R.id.app_status).text = getStatusString(app)

                val appDescription = v.findViewById<TextView>(R.id.app_description)
                if (app.description().isNotEmpty()) {
                    appDescription.visibility = View.VISIBLE
                    appDescription.text = app.description()
                } else if (app.note().isNotEmpty()) {
                    appDescription.visibility = View.VISIBLE
                    appDescription.text = app.note()
                }
                builder.setView(v)
                builder.setPositiveButton(android.R.string.ok, null)
                if (app.appSettingsEnabled()) {
                    builder.setNeutralButton(
                        R.string.menu_item_settings,
                        DialogInterface.OnClickListener { _, _ ->
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:" + app.packageName)
                            it?.context?.startActivity(intent)
                        })
                }
                builder.create().show()
            }
            note.setOnClickListener {
                val themeContext = ContextThemeWrapper(context, R.style.Theme_AlertDialog)
                val builder = AlertDialog.Builder(themeContext)
                builder.setTitle(app.title())
                builder.setMessage(app.note());
                builder.setPositiveButton(android.R.string.ok, null)
                builder.create().show()
            }

            image.visibility = View.VISIBLE
            image.setOnClickListener {
                if (app.mDownloadId == -1L) {
                    (context as MainActivity).downloadApp(app)
                } else {
                    (context as MainActivity).cancelDownloadApp(app)
                }
            }
        }
    }

    class SeparatorItemViewHolder : RecyclerView.ViewHolder {
        var title: TextView

        constructor(view: View) : super(view) {
            title = view.findViewById(R.id.separator_title)
        }
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
                LayoutInflater.from(context).inflate(R.layout.separator_list_item, parent, false)
            )
        }
        return AppItemViewHolder(
            LayoutInflater.from(context).inflate(R.layout.app_list_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppItemViewHolder) {
            val app: AppItem = items[position] as AppItem
            holder.app = app
            holder.title.text = app.title()
            Picasso.with(context).load(app.iconUrl(context))
                .error(R.drawable.ic_warning).into(holder.logo)
            holder.pkg.text = app.packageName
            holder.note.visibility = View.GONE

            if (app.mDownloadId != -1L) {
                holder.image.setImageResource(R.drawable.ic_cancel)
                holder.image.visibility = View.VISIBLE
                holder.progress.visibility = View.VISIBLE
            } else {
                if (app.installEnabled()) {
                    holder.image.setImageResource(R.drawable.ic_download_outline)
                    holder.image.visibility = View.VISIBLE
                } else {
                    holder.image.visibility = View.GONE
                }
                holder.progress.visibility = View.GONE
            }
            if (app.updateAvailable()) {
                context.getString(R.string.status_update_available)
                holder.version.text =
                    context.resources.getString(R.string.app_item_version) + " " + app.versionName
            } else if (app.appInstalled()) {
                holder.version.text =
                    context.resources.getString(R.string.app_item_version) + " " + app.versionName
            } else if (app.appNotInstaled()) {
                holder.version.text =
                    context.resources.getString(R.string.app_item_version) + " " + app.versionName
            } else if (app.appDisabled()) {
                holder.version.text =
                    context.resources.getString(R.string.app_item_version) + " " + app.versionName
            }

            if (app.note().isNotEmpty()) {
                holder.note.visibility = View.VISIBLE
            }
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