package com.example.wordtrainer.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wordtrainer.R
import com.example.wordtrainer.databinding.FragmentStatsBinding
import com.example.wordtrainer.ui.app
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels {
        viewModelFactory {
            initializer { StatsViewModel(app.repository, app.settings) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.statTotal.cellLabel.text = getString(R.string.stat_total)
        binding.statLearned.cellLabel.text = getString(R.string.stat_learned)
        binding.statDue.cellLabel.text = getString(R.string.stat_due)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.streakValue.text = getString(R.string.stat_streak_value, s.streak)
                    binding.statTotal.cellValue.text = s.total.toString()
                    binding.statLearned.cellValue.text = s.learned.toString()
                    binding.statDue.cellValue.text = s.due.toString()
                    binding.goalText.text = getString(R.string.stat_goal_progress, s.reviewedToday, s.dailyGoal)
                    binding.goalProgress.progress = s.goalProgress
                    binding.accuracyText.text = getString(R.string.stat_accuracy, s.accuracyToday)
                    binding.activityChart.setData(s.activity)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
