package com.example.turisticky_zavod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.turisticky_zavod.databinding.ListItemBasicBinding

class RvAdapterBasic(
    private var runnersList: List<Runner>,
    private var optionsMenuLongClickListener: OptionsMenuLongClickListener
) : RecyclerView.Adapter<RvAdapterBasic.ViewHolder>() {

    interface OptionsMenuLongClickListener {
        fun onOptionsMenuLongClicked(position: Int): Boolean
    }

    inner class ViewHolder(val binding: ListItemBasicBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBasicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with (holder) {
            with (runnersList[position]) {
                binding.textViewRunnerIdListBasic.text = this.runnerId.toString()
                binding.textViewRunnerNameListBasic.text = this.name
                binding.textViewRunnerTeamListBasic.text = this.team

                binding.imageViewDisqualifiedListBasic.visibility = if (disqualified) View.VISIBLE else View.GONE

                binding.cardViewListBasic.setOnLongClickListener {
                    optionsMenuLongClickListener.onOptionsMenuLongClicked(position)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return runnersList.size
    }
}