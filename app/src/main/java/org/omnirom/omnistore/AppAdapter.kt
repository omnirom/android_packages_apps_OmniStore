package org.omnirom.omnistore

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.app_list_item.view.*


class AppAdapter(val items: ArrayList<MainActivity.AppItem>, val context: Context) :
    RecyclerView.Adapter<AppAdapter.ViewHolder>() {
    var mActivity: MainActivity? = null

    inner class ViewHolder : RecyclerView.ViewHolder {
        var app: MainActivity.AppItem? = null
        var title: TextView
        var logo: ImageView
        var status: TextView
        var image: ImageView
        var pkg: TextView

        constructor(view: View) : super(view) {
            title = view.app_name
            logo = view.app_logo
            status = view.app_status
            image = view.app_image
            pkg = view.app_pkg

            view.setOnClickListener(View.OnClickListener {
                if (app!!.mInstalled) {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:" + app!!.pkg())
                    it?.context!!.startActivity(intent)
                } else {
                    val builder = AlertDialog.Builder(it?.context)
                    builder.setTitle("Title")
                    builder.setMessage("Selected " + title.text);
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.create().show()
                }
            })
            if (mActivity!!.mInstallEnabled) {
                view.app_image.visibility = View.VISIBLE
                view.app_image.setOnClickListener(View.OnClickListener {
                    if (app!!.mDownloadId == -1L)
                        mActivity!!.downloadApp(app!!)
                    else mActivity!!.cancelDownloadApp(app!!)


                })
            } else {
                view.app_image.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(context).inflate(R.layout.app_list_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app: MainActivity.AppItem = items.get(position)
        holder?.app = app
        holder?.title?.text = app.title()
        if (app.iconUrl() != null) {
            Picasso.get().load(app.iconUrl()).error(R.mipmap.ic_launcher).into(holder?.logo)
        } else {
            holder?.logo.setImageResource(R.mipmap.ic_launcher)
        }
        holder?.pkg.text = app.pkg()

        if (app.mDownloadId != -1L) {
            holder.image.setImageResource(R.drawable.ic_cancel)
            holder?.status.text = "Installing..."
        } else {
            holder.image.setImageResource(R.drawable.ic_download)
            if (app.mInstalled) {
                holder?.status.text = "Installed"
            } else {
                holder?.status.text = "Not installed"
            }
        }
    }
}