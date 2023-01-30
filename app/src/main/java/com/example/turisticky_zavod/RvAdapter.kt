package com.example.turisticky_zavod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.turisticky_zavod.databinding.ListRunnersBinding

class RvAdapter(
    private var peopleList: List<Person>,
    private var optionsMenuLongClickListener: OptionsMenuLongClickListener
) : RecyclerView.Adapter<RvAdapter.ViewHolder>() {

    interface OptionsMenuLongClickListener {
        fun onOptionsMenuLongClicked(position: Int): Boolean
    }

    inner class ViewHolder(val binding: ListRunnersBinding, val parent: ViewGroup) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListRunnersBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with (holder) {
            with (peopleList[position]) {
                binding.textViewRunnerId.text = this.runnerId.toString()
                binding.textViewRunnerName.text = this.name
                binding.textViewRunnerTeam.text = this.team

                binding.cardView.setOnLongClickListener {
                    optionsMenuLongClickListener.onOptionsMenuLongClicked(position)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return peopleList.size
    }
}
