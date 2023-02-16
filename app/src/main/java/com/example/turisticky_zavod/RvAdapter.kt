package com.example.turisticky_zavod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.turisticky_zavod.databinding.ListItemBinding
import java.text.SimpleDateFormat
import java.util.*

class RvAdapter(
    private var runnersList: List<Runner>,
    private var optionsMenuLongClickListener: OptionsMenuLongClickListener
) : RecyclerView.Adapter<RvAdapter.ViewHolder>() {

    val BASIC = 0
    val DETAILED = 1
    var state = BASIC

    interface OptionsMenuLongClickListener {
        fun onOptionsMenuLongClicked(position: Int): Boolean
    }

    inner class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with (holder) {
            with (runnersList[position]) {
                binding.textViewRunnerIdRv.text = this.runnerId.toString()
                binding.textViewRunnerNameRv.text = this.name
                binding.textViewRunnerTeamRv.text = this.team

                if (state == DETAILED) {
                    binding.linearLayoutDetailedListContent.visibility = View.VISIBLE
                    binding.textViewStartTimeVar.text = SimpleDateFormat("HH:mm:ss", Locale("cze")).format(this.startTime)
                    binding.textViewFinishTimeVar.text = if (this.finishTime != null) SimpleDateFormat("HH:mm:ss", Locale("cze")).format(this.finishTime) else " - "
                    binding.textViewTimeWaitedVar.text = SimpleDateFormat("mm:ss", Locale("cze")).format(this.timeWaitedSeconds * 1000)
                    binding.textViewPenaltyMinutesVarListDetailed.text = SimpleDateFormat("mm:ss", Locale("cze")).format(this.penaltySeconds * 1000)
                } else {
                    binding.linearLayoutDetailedListContent.visibility = View.GONE
                }

                if (this.disqualified) {
                    binding.imageViewDisqualifiedListDetailed.visibility = View.VISIBLE
                    binding.linearLayoutTimeContent.orientation = LinearLayout.VERTICAL
                } else {
                    binding.imageViewDisqualifiedListDetailed.visibility = View.GONE
                    binding.linearLayoutTimeContent.orientation = LinearLayout.HORIZONTAL
                }

                binding.cardViewRv.setOnLongClickListener {
                    optionsMenuLongClickListener.onOptionsMenuLongClicked(position)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return runnersList.size
    }
}