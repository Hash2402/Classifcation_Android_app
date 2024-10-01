package com.example.cv_part2;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import android.graphics.ColorSpace;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.graphics.Typeface;
import android.graphics.Color;

import java.text.DecimalFormat;



public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView textViewResult;
    private Bitmap imageBitmap;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView appNameTextView = findViewById(R.id.appNameTextView);
        appNameTextView.setText("Dog-Cat Classifier");
        imageView = findViewById(R.id.imageView);
        textViewResult = findViewById(R.id.textViewResult);
        Button buttonGallery = findViewById(R.id.buttonGallery);
        Button buttonCamera = findViewById(R.id.buttonCamera);

        // Initialize gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            try {
                                // For API 28 and above, use ImageDecoder
                                ImageDecoder.Source imageSource = ImageDecoder.createSource(this.getContentResolver(), imageUri);
                                // Decode bitmap with ARGB_8888 configuration
                                imageBitmap = ImageDecoder.decodeBitmap(imageSource, (decoder, info, source) -> {
                                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                                });

                                // Ensure bitmap is ARGB_8888
                                if (imageBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                                    imageBitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true);
                                }

                                imageView.setImageBitmap(imageBitmap);
                                classifyImage(imageBitmap);
                            } catch (IOException e) {
                                String errorMessage = getString(R.string.image_loading_error, e.getMessage());
                                Log.e("MainActivity", errorMessage);
                                textViewResult.setText(errorMessage);
                            }
                        } else {
                            Log.e("MainActivity", "ImageUri is null");
                        }
                    }
                });

        // Initialize camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            imageBitmap = (Bitmap) extras.get("data");
                            if (imageBitmap != null) {
                                imageView.setImageBitmap(imageBitmap);
                                classifyImage(imageBitmap);
                            } else {
                                Log.e("MainActivity", "Bitmap is null");
                            }
                        } else {
                            Log.e("MainActivity", "Extras are null");
                        }
                    }
                });

        // Set up buttons to trigger gallery and camera
        buttonGallery.setOnClickListener(view -> openGallery());
        buttonCamera.setOnClickListener(view -> openCamera());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void classifyImage(Bitmap image) {
        // Check the model input size, for example 256x256
        int modelInputWidth = 150;
        int modelInputHeight = 150;
        int numChannels = 3; // For RGB

        // Resize the image to model input dimensions
        Bitmap resizedImage = Bitmap.createScaledBitmap(image, modelInputWidth, modelInputHeight, true);

        // Create an array to store raw RGB pixel values
        int[] intValues = new int[modelInputWidth * modelInputHeight];
        resizedImage.getPixels(intValues, 0, resizedImage.getWidth(), 0, 0, resizedImage.getWidth(), resizedImage.getHeight());

        // Create a float buffer to hold the model input (4D tensor with batch size = 1)
        float[][][][] inputBuffer = new float[1][modelInputWidth][modelInputHeight][numChannels];

        // Populate the input buffer with the pixel values
        for (int i = 0; i < intValues.length; i++) {
            int pixelValue = intValues[i];

            // Extract RGB values and convert them to floats (without normalization)
            inputBuffer[0][i / modelInputWidth][i % modelInputHeight][0] = ((pixelValue >> 16) & 0xFF) / 255.0f; // Red
            inputBuffer[0][i / modelInputWidth][i % modelInputHeight][1] = ((pixelValue >> 8) & 0xFF) / 255.0f;  // Green
            inputBuffer[0][i / modelInputWidth][i % modelInputHeight][2] = (pixelValue & 0xFF) / 255.0f;         // Blue     // Blue
        }

        // Adjust output buffer size (assuming the model is returning two float values)
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 2}, DataType.FLOAT32); // Adjust to expected output shape

        try (Interpreter interpreter = new Interpreter(tfHelper.loadModelFile(getApplicationContext()))) {
            // Run inference
            interpreter.run(inputBuffer, outputBuffer.getBuffer());

            // Retrieve results from the output buffer
            float[] results = outputBuffer.getFloatArray(); // Now we expect an array with 2 values
            float result1 = results[0];
            float result2 = results[1]; // If the model has multiple outputs


            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            String resultText = "Classification results: " +
                    "\n\n\nCat " + decimalFormat.format(result1 * 100) + "%" +
                    "\n\nDog " + decimalFormat.format(result2 * 100) + "%";

// Create a SpannableString
            SpannableString spannable = new SpannableString(resultText);

// Style "Classification results:" (Bold + Increase size)
            spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, "Classification results:".length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new RelativeSizeSpan(2.25f), 0, "Classification results:".length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

// Find the index of "Cat" and "Dog"
            int catStart = resultText.indexOf("Cat");
            int dogStart = resultText.indexOf("Dog");

            spannable.setSpan(new ForegroundColorSpan(Color.GREEN), catStart, catStart + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Cat text
            spannable.setSpan(new ForegroundColorSpan(Color.GREEN), catStart + 4, resultText.indexOf("%", catStart) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Cat percentage

// Increase size for "Cat" and its percentage using RelativeSizeSpan
            spannable.setSpan(new RelativeSizeSpan(1.5f), catStart, catStart + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Cat text size
            spannable.setSpan(new RelativeSizeSpan(1.5f), catStart + 4, resultText.indexOf("%", catStart) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Cat percentage size

// Add color to "Dog" and its result
            spannable.setSpan(new ForegroundColorSpan(Color.RED), dogStart, dogStart + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Dog text
            spannable.setSpan(new ForegroundColorSpan(Color.RED), dogStart + 4, resultText.indexOf("%", dogStart) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Dog percentage

// Increase size for "Dog" and its percentage using RelativeSizeSpan
            spannable.setSpan(new RelativeSizeSpan(1.5f), dogStart, dogStart + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Dog text size
            spannable.setSpan(new RelativeSizeSpan(1.5f), dogStart + 4, resultText.indexOf("%", dogStart) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Dog percentage size


// Set the formatted text to the TextView
            textViewResult.setText(spannable);

        } catch (IOException e) {
            String errorMessage = getString(R.string.error_loading_model, e.getMessage());
            Log.e("MainActivity", errorMessage);
            textViewResult.setText(errorMessage);
        } catch (Exception e) {
            String errorMessage = getString(R.string.error_during_classification, e.getMessage());
            Log.e("MainActivity", errorMessage);
            textViewResult.setText(errorMessage);
        }
    }

}
