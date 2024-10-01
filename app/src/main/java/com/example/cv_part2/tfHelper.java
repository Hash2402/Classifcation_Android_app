package com.example.cv_part2;



import android.content.Context;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class tfHelper {

    public static MappedByteBuffer loadModelFile(Context context) throws IOException {
        String modelFileName = "DogCatClassifier-2.tflite";


        try (FileInputStream fileInputStream = new FileInputStream(context.getAssets().openFd(modelFileName).getFileDescriptor());
             FileChannel fileChannel = fileInputStream.getChannel()) {

            long startOffset = context.getAssets().openFd(modelFileName).getStartOffset();
            long declaredLength = context.getAssets().openFd(modelFileName).getDeclaredLength();

            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } // FileInputStream and FileChannel are automatically closed here
    }
}
