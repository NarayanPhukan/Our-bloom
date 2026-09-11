package com.ourbloom.app.games

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ourbloom.app.R
import com.ourbloom.app.touch.ThumbKissActivity

class CoupleArcadeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_couple_arcade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btn_back_arcade).setOnClickListener {
            findNavController().navigateUp()
        }

        // Game 1: Tic-Tac-Toe
        view.findViewById<MaterialCardView>(R.id.card_game_tictactoe).setOnClickListener {
            findNavController().navigate(R.id.loveTicTacToeFragment)
        }
        view.findViewById<MaterialButton>(R.id.btn_play_tictactoe).setOnClickListener {
            findNavController().navigate(R.id.loveTicTacToeFragment)
        }

        // Game 2: Truth or Dare
        view.findViewById<MaterialCardView>(R.id.card_game_truth_dare).setOnClickListener {
            findNavController().navigate(R.id.truthOrDareFragment)
        }
        view.findViewById<MaterialButton>(R.id.btn_play_truth_dare).setOnClickListener {
            findNavController().navigate(R.id.truthOrDareFragment)
        }

        // Game 3: Would You Rather
        view.findViewById<MaterialCardView>(R.id.card_game_wyr).setOnClickListener {
            findNavController().navigate(R.id.wouldYouRatherFragment)
        }
        view.findViewById<MaterialButton>(R.id.btn_play_wyr).setOnClickListener {
            findNavController().navigate(R.id.wouldYouRatherFragment)
        }

        // Game 4: ThumbKiss
        view.findViewById<MaterialCardView>(R.id.card_game_thumbkiss).setOnClickListener {
            val intent = Intent(requireContext(), ThumbKissActivity::class.java)
            startActivity(intent)
        }
    }
}
