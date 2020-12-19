package com.rohan328.memorygame.models

import com.rohan328.memorygame.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize, customImages: List<String>?) {

    val cards: List<MemoryCard>
    var numPairsFound: Int = 0

    private var numCardsFlipped = 0
    private var indexOfSingleSelectedCard: Int? = null

    init {
        if (customImages == null) {
            //get unique icons
            val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            //double the icons and randomize
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            //wrap into card data model
            cards = randomizedImages.map { MemoryCard(it) }
        } else {
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it.hashCode(), it) }
        }

    }

    fun flipCard(position: Int): Boolean {
        //increment number of flipped cards
        numCardsFlipped++

        val card = cards[position]

        var foundMatch = false
        if (indexOfSingleSelectedCard == null) {
            //0 or 2 cards previously flipped over

            //restore previously flipped cards if any
            restoreCards()
            //set current flipped card position
            indexOfSingleSelectedCard = position
        } else {
            //1 card flipped over
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position)
            //if match is not found cards will be flipped back on next move
            indexOfSingleSelectedCard = null
        }
        //actually flip card
        card.isFaceUp = !card.isFaceUp


        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        return if (cards[position1].identifier == cards[position2].identifier) {
            cards[position1].isMatched = true
            cards[position2].isMatched = true
            numPairsFound++
            true
        } else false
    }

    private fun restoreCards() {
        for (card in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numCardsFlipped / 2
    }
}
