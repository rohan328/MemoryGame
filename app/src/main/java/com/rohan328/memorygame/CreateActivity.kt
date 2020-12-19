package com.rohan328.memorygame

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.rohan328.memorygame.models.BoardSize
import com.rohan328.memorygame.utils.*
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object {
        private const val PICK_PHOTO_CODE = 78
        private const val READ_EXTERNAL_PHOTOS_CODE = 123
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 10
        private const val TAG = "CreateActivity"
    }

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var etGameName: EditText
    private lateinit var pbSave: ProgressBar

    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private val chosenImageUris = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        //show back button on action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //init variable
        rvImagePicker = findViewById(R.id.rvImagePicker)
        btnSave = findViewById(R.id.btnSave)
        etGameName = findViewById(R.id.etPlayCustomGame)
        pbSave = findViewById(R.id.pbSave)


        //get board size from intent and set action bar title
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0/$numImagesRequired)"

        //set adapter
        adapter = ImagePickerAdapter(
            this,
            chosenImageUris,
            boardSize,
            object : ImagePickerAdapter.ImageClickListener {
                override fun onPlaceholderClicked() {
                    if (isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)) {
                        launchPhotoPicker()
                    } else {
                        requestPermission(
                            this@CreateActivity,
                            READ_PHOTOS_PERMISSION,
                            READ_EXTERNAL_PHOTOS_CODE
                        )
                    }
                }
            })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())

        //game name max length and text change listener to enable save button
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }
        })

        //save data to firebase
        btnSave.setOnClickListener {
            saveDataToFirebase()
        }

    }

    //check if user granted permission
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_EXTERNAL_PHOTOS_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPhotoPicker()
            } else {
                Toast.makeText(
                    this,
                    "In order to make a custom game, you need to provide access to you photos",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    //options menu listener
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    //add photo uris to list on activity result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get any data from launched activity")
            return
        }

        val selectedUri = data.data
        val clipData = data.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUris.size < numImagesRequired) {
                    chosenImageUris.add(clipItem.uri)
                }
            }
        } else if (selectedUri != null) {
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics (${chosenImageUris.size}/$numImagesRequired)"
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    //save data to firebase
    private fun saveDataToFirebase() {
        btnSave.isEnabled = false
        val customGameName = etGameName.text.toString()
        //check if game name already exists
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if (document != null && document.data != null) {
                AlertDialog.Builder(this)
                    .setTitle("Choose another name")
                    .setMessage("A game with this name already exists. Choose another name")
                    .setPositiveButton("OK", null)
                    .show()
                btnSave.isEnabled = true
            } else {
                handleImageUpload(customGameName)
            }
        }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Error occured while saving. Try again later",
                    Toast.LENGTH_LONG
                ).show()
                btnSave.isEnabled = true
            }

    }

    private fun handleImageUpload(gameName: String) {
        pbSave.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for ((index, photoUri) in chosenImageUris.withIndex()) {
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-$index.jpg"
            val photoReference = storage.reference.child(filePath)

            photoReference.putBytes(imageByteArray).continueWithTask { photoUploadTask ->
                photoReference.downloadUrl
            }.addOnCompleteListener { downloadUrlTask ->
                if (!downloadUrlTask.isSuccessful) {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_LONG).show()
                    didEncounterError = true
                    return@addOnCompleteListener
                }
                if (didEncounterError) {
                    pbSave.visibility = View.GONE
                    return@addOnCompleteListener
                }
                //success
                pbSave.progress = uploadedImageUrls.size * 100 / chosenImageUris.size
                val downloadUrl = downloadUrlTask.result.toString()
                uploadedImageUrls.add(downloadUrl)

                if (uploadedImageUrls.size == chosenImageUris.size) {
                    handleAllImagesUploaded(gameName, uploadedImageUrls)
                }
            }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener { gameCreationTask ->
                pbSave.visibility = View.GONE
                if (!gameCreationTask.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Failed to create new game. Try again later",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnCompleteListener
                }
                AlertDialog.Builder(this)
                    .setTitle("Game created. Let's play your new game")
                    .setPositiveButton("OK") { _, _ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }
    }

    //returns scaled image byte array
    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    //checks if save button should be enabled
    private fun shouldEnableSaveButton(): Boolean {
        if (chosenImageUris.size != numImagesRequired || etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true
    }

    //launch photo chooser
    private fun launchPhotoPicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(
            Intent.createChooser(intent, "Choose pics for custom game"),
            PICK_PHOTO_CODE
        )
    }
}