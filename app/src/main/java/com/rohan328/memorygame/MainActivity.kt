package com.rohan328.memorygame

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener
import com.google.android.material.snackbar.Snackbar
import com.rohan328.memorygame.models.BoardSize
import com.rohan328.memorygame.models.MemoryGame
import com.rohan328.memorygame.utils.EXTRA_BOARD_SIZE

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 328
    }

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

        setupBoard()
    }

    //inflate menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    //menu item click listener
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.miRefresh -> {
                //reset game
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit your current game?", null) {
                        setupBoard()
                    }
                } else {
                    setupBoard()
                }
                return true
            }
            R.id.miNewSize -> {
                showNewSizeDialog()
                return true
            }
            R.id.miCreate -> {
                showCreationDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    //show dialog to choose size for new game
    @SuppressLint("InflateParams")
    private fun showCreationDialog() {
        //inflate the radiobutton view
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        //show dialog to choose desired board size
        showAlertDialog("Create you own memory board", boardSizeView) {
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            //navigate to new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        }

    }

    //setup the game
    @SuppressLint("SetTextI18n")
    private fun setupBoard() {
        //set initial textview titles
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = "EASY: 4 x 2"
                tvNumPairs.text = "Pairs: 0/${boardSize.getNumPairs()}"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = "MEDIUM: 6 x 3"
                tvNumPairs.text = "Pairs: 0/${boardSize.getNumPairs()}"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "HARD: 6 x 6"
                tvNumPairs.text = "Pairs: 0/${boardSize.getNumPairs()}"
            }

        }

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

    //update game(flip images)
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

    //show size selector alert dialog
    @SuppressLint("InflateParams")
    private fun showNewSizeDialog() {
        //inflate the radiobutton view
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        //set the radiobutton to selected game mode
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }

        //show dialog and reload game accordingly
        showAlertDialog("Choose New Size", boardSizeView) {
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            setupBoard()
        }
    }

    //show alert dialog
    private fun showAlertDialog(
        title: String,
        view: View?,
        positiveButtonClickListener: View.OnClickListener
    ) {

        AlertDialog.Builder(this)
            .setTitle(title).setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Okay") { _, _ ->
                positiveButtonClickListener.onClick(null)
            }.show()
    }

}