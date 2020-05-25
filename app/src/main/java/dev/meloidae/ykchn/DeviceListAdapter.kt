package dev.meloidae.ykchn

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.device_list_item.view.*

class DeviceListAdapter(
    private val deviceMap: IndexedHashMap<String, BleDeviceHolder>
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    companion object {
        private  val TAG = DeviceListAdapter::class.java.simpleName
    }

    private var onClickCallback: DeviceListClickListener? = null

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val deviceHolder = deviceMap.getByIndex(position)
        val context = holder.itemView.context
        holder.deviceNameText.text = deviceHolder?.name
        holder.deviceMacText.text = context.getString(R.string.mac_address, deviceHolder?.address)
        if (deviceHolder?.isConnected == true) {
            holder.deviceConnectButton.setImageDrawable(context.getDrawable(R.drawable.ic_bluetooth_connected_round_24px))
            holder.deviceConnectButton.setColorFilter(ContextCompat.getColor(context, R.color.colorBluetoothConnected))
        } else {
            holder.deviceConnectButton.setImageDrawable(context.getDrawable(R.drawable.ic_bluetooth_round_24px))
            holder.deviceConnectButton.setColorFilter(ContextCompat.getColor(context, R.color.colorBluetoothDisconnected))
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val deviceItemLayout = LayoutInflater.from(parent.context).inflate(
            R.layout.device_list_item, parent, false)
        return DeviceViewHolder(deviceItemLayout)
    }

    override fun getItemCount(): Int {
        return deviceMap.size
    }

    fun setClickListener(callback: DeviceListClickListener) {
        onClickCallback = callback
    }

    inner class DeviceViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val deviceNameText: TextView = itemView.device_name_text
        val deviceMacText: TextView = itemView.device_mac_text
        val deviceConnectButton: ImageButton = itemView.device_connect_button

        override fun onClick(view: View) {
            onClickCallback?.onClick(view, adapterPosition)
        }

        init {
            itemView.device_connect_button.setOnClickListener(this)
            itemView.device_item_layout.setOnClickListener(this)
        }
    }

    interface DeviceListClickListener {
        fun onClick(view: View, position: Int)
    }
}