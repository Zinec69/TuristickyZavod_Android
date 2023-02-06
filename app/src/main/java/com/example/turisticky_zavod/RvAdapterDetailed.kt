package com.example.turisticky_zavod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.turisticky_zavod.databinding.ListItemDetailedBinding
import java.text.SimpleDateFormat
import java.util.*

class RvAdapterDetailed(
    private var runnersList: List<Runner>,
    private var optionsMenuLongClickListener: OptionsMenuLongClickListener
) : RecyclerView.Adapter<RvAdapterDetailed.ViewHolder>() {

    interface OptionsMenuLongClickListener {
        fun onOptionsMenuLongClicked(position: Int): Boolean
    }

    inner class ViewHolder(val binding: ListItemDetailedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemDetailedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with (holder) {
            with (runnersList[position]) {
                binding.textViewRunnerIdListDetailed.text = this.runnerId.toString()
                binding.textViewRunnerNameListDetailed.text = this.name
                binding.textViewRunnerTeamListDetailed.text = this.team
                binding.textViewStartTimeVar.text = SimpleDateFormat("HH:mm:ss", Locale("cze")).format(this.startTime)
                binding.textViewFinishTimeVar.text = if (this.finishTime != null) SimpleDateFormat("HH:mm:ss", Locale("cze")).format(this.finishTime) else " - "
                binding.textViewTimeWaitedVar.text = SimpleDateFormat("mm:ss", Locale("cze")).format(this.timeWaited)
                binding.textViewPenaltyMinutesVarListDetailed.text = SimpleDateFormat("mm:ss", Locale("cze")).format(this.penaltySeconds * 1000)

                if (this.disqualified) {
                    binding.imageViewDisqualifiedListDetailed.visibility = View.VISIBLE
                    binding.linearLayoutTimeContent.orientation = LinearLayout.VERTICAL
                } else {
                    binding.imageViewDisqualifiedListDetailed.visibility = View.GONE
                    binding.linearLayoutTimeContent.orientation = LinearLayout.HORIZONTAL
                }

                binding.cardViewListDetailed.setOnLongClickListener {
                    optionsMenuLongClickListener.onOptionsMenuLongClicked(position)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return runnersList.size
    }
}