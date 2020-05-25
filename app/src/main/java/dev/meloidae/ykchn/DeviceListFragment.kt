package dev.meloidae.ykchn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_device_list.*

class DeviceListFragment(
    deviceMap: IndexedHashMap<String, BleDeviceHolder>
) : Fragment() {

    companion object {
        private val TAG = DeviceListFragment::class.java.simpleName
    }

    private val deviceListAdapter = DeviceListAdapter(deviceMap)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_device_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val manager = LinearLayoutManager(this.activity, LinearLayoutManager.VERTICAL, false)
        device_list_view.adapter = deviceListAdapter
        device_list_view.layoutManager = manager
    }

    fun insertItem(position: Int) {
        this.activity?.runOnUiThread {
            deviceListAdapter.notifyItemInserted(position)
        }
    }

    fun changeItem(position: Int) {
        this.activity?.runOnUiThread {
            deviceListAdapter.notifyItemChanged(position)
        }
    }

    fun removeItem(position: Int) {
        this.activity?.runOnUiThread {
            deviceListAdapter.notifyItemRemoved(position)
        }
    }

    fun changeDataSet() {
        this.activity?.runOnUiThread {
            deviceListAdapter.notifyDataSetChanged()
        }
    }

    fun setDeviceListClickListener(callback: DeviceListAdapter.DeviceListClickListener) {
        deviceListAdapter.setClickListener(callback)
    }


    init {
    }

}