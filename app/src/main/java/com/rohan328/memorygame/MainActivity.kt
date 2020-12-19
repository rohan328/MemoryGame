package com.rohan328.memorygame

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.rohan328.memorygame.models.BoardSize
import com.rohan328.memorygame.models.MemoryGame
import com.rohan328.memorygame.models.UserImageList
import com.rohan328.memorygame.utils.EXTRA_BOARD_SIZE
import com.rohan328.memorygame.utils.EXTRA_GAME_NAME
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 328
    }

    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var layoutRoot: CoordinatorLayout
    private lateinit var tvNumPairs: TextView

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var adapter: MemoryBoardAdapter
    private lateinit var memoryGame: MemoryGame

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
            R.id.miPlayCustom -> {
                showPlayCustomDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    //result of create activity(game name)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME) ?: return

            downloadGame(customGameName)
        }
    }

    //download custom game from firestore
    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                //error
                Snackbar.make(
                    layoutRoot,
                    "Sorry, we couldnt find game '$customGameName'",
                    Snackbar.LENGTH_LONG
                ).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            gameName = customGameName
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(layoutRoot, "You're playing $customGameName", Snackbar.LENGTH_LONG).show()
            setupBoard()

        }.addOnFailureListener {
            //error
        }
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
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
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
        memoryGame = MemoryGame(boardSize, customGameImages)

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
                CommonConfetti.rainingConfetti(
                    layoutRoot,
                    intArrayOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED, Color.MAGENTA)
                ).oneShot()
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
            gameName = null
            customGameImages = null
            setupBoard()
        }
    }

    //show dialog to get custom game name from the user
    private fun showPlayCustomDialog() {
        val playCustomGameView =
            LayoutInflater.from(this).inflate(R.layout.dialog_play_custom, null)
        showAlertDialog("Fetch memory game", playCustomGameView, View.OnClickListener {
            val etPlayCustomGame = playCustomGameView.findViewById<EditText>(R.id.etPlayCustomGame)
            val gameToDownload = etPlayCustomGame.text.toString().trim()
            if (gameToDownload.isBlank()) {
                return@OnClickListener
            }
            downloadGame(gameToDownload)
        })
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