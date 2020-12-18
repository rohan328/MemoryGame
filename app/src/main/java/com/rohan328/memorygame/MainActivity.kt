package com.rohan328.memorygame

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.rohan328.memorygame.models.BoardSize
import com.rohan328.memorygame.models.MemoryGame

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: MemoryBoardAdapter
    private lateinit var memoryGame: MemoryGame
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private lateinit var layoutRoot: ConstraintLayout

    private var boardSize: BoardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //find views
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
        layoutRoot = findViewById(R.id.layoutRoot)

        //set zero progress
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))

        //init game
        memoryGame = MemoryGame(boardSize)

        //init adapter
        adapter = MemoryBoardAdapter(
            this,
            boardSize,
            memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClicked(position: Int) {
                    updateGame(position)
                }
            })

        //set adapter
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true) //set fixed size
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    @SuppressLint("SetTextI18n")
    private fun updateGame(position: Int) {
        //if already won show message and return
        if (memoryGame.haveWonGame()) {
            Snackbar.make(layoutRoot, "You've already won!", Snackbar.LENGTH_LONG).show()
            return
        }
        //invalid move show message and return
        if (memoryGame.isCardFaceUp(position)) {
            Snackbar.make(layoutRoot, "Invalid Move", Snackbar.LENGTH_SHORT).show()
            return
        }

        //flip card
        if (memoryGame.flipCard(position)) {

            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)

            //update pairs found
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound}/${boardSize.getNumPairs()}"

            //if won show message
            if (memoryGame.haveWonGame()) {
                Snackbar.make(
                    layoutRoot,
                    "Congratulations!! You've won the game.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        //update number of moves
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged() //notify adapter
    }
}