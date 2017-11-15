package nfirdaus.andrew.cmu.edu.digitalizer;

/*
 * @author Nanda Firdaus
 * Last Modified: November 8, 2017
 *
 * This class is used for main activity of the app.
 * It will show and control the function and interaction of
 * the main page of the app. The main functions are select image from
 * gallery, show camera intent to capture photo, send request to
 * the web service, parse the response, and show it to the user.
 *
 */


import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    Bitmap selectedImage;
    ImageView imageViewSelected;
    EditText textResult;
    ByteArrayOutputStream byteArrayOutputStream;
    byte[] byteArray;
    ProgressDialog progressDialog;
    String convertedImage;
    private int GALLERY = 1, CAMERA = 2;
    String mCurrentPhotoPath;

    static final String UPLOAD_PATH ="http://digitalizer-service.herokuapp.com/recognize";

    /**
     * Called when the user open the app.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set layout to activity_main
        setContentView(R.layout.activity_main);

        // get elements from layout
        Button getFromGallery = (Button) findViewById(R.id.buttonFromGallery);
        Button getFromCamera = (Button) findViewById(R.id.buttonFromCamera);
        Button submit = (Button) findViewById(R.id.buttonSubmit);
        textResult = (EditText) findViewById(R.id.textResult);
        imageViewSelected = (ImageView) findViewById(R.id.imageSelected);

        byteArrayOutputStream = new ByteArrayOutputStream();

        // disable edit feature in the edit text
        textResult.setTextIsSelectable(true);
        textResult.setKeyListener(null);

        // set onClickListener of the getFromGallery button to open a new gallery intent
        getFromGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();

                // set file type that will be shown to the gallery
                intent.setType("image/*");

                intent.setAction(Intent.ACTION_GET_CONTENT);

                // start the gallery intent with request code 1 as a flag
                startActivityForResult(Intent.createChooser(intent, "Select Image From Gallery"), GALLERY);
            }
        });

        // set onClickListener for getFromCamera button
        getFromCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // check if the android version is greater or equal than Marshmallow
                // Android needs additional permission to use camera start from Android M
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {

                        // Ask the user for permission to access camera because it has not been granted
                        requestPermissions(new String[]{Manifest.permission.CAMERA},
                                4455);
                    } else {
                        // the permission has been granted.
                        // example taken from https://developer.android.com/training/camera/photobasics.html
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        // Ensure that there's a camera activity to handle the intent
                        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                            // Create the File where the photo should go
                            File photoFile = null;
                            try {
                                photoFile = createImageFile();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            // Continue only if the File was successfully created
                            if (photoFile != null) {
                                // get the image from storage and set it as the path to save the result
                                Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                                        "edu.cmu.andrew.nfirdaus.fileprovider",
                                        photoFile);
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                                // start the camera intent
                                startActivityForResult(takePictureIntent, CAMERA);
                            }
                        }
                    }
                }

            }
        });

        // set listener for submit button
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioType);
                int selectedIndex = radioGroup.getCheckedRadioButtonId();

                boolean isHandwriting = true;

                // check if the image type is print or handwritten text
                if (selectedIndex == R.id.radioPrint) {
                    isHandwriting = false;
                }

                // send the request to server
                uploadToServer(isHandwriting);
            }
        });
    }

    /**
     * Callback method after the app get the result of asking permission to the user.
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // check if it is the result of our app's request
        if (requestCode == 4455) {
            // if permission is granted
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, CAMERA);
            }
            // if permission is not granted by the user
            else {
                Toast.makeText(MainActivity.this, "You didn't give permission to access camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Callback method that is called when the user finish selecting data from gallery intent
     * or taking photo from camera intent.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // if the user cancel selecting a picture
        if (resultCode == this.RESULT_CANCELED) {
            return;
        }

        // If the user select the picture from gallery
        if (requestCode == GALLERY) {
            if (data != null) {
                Uri contentURI = data.getData();
                try {
                    // get the media from uri
                    selectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), contentURI);

                    // resize the image so it fulfil the requirement of the API
                    selectedImage = resizeImage(selectedImage);

                    // set the imageView into the selected image
                    imageViewSelected.setImageBitmap(selectedImage);

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Failed!", Toast.LENGTH_SHORT).show();
                }
            }
        // if user get the picture from camera
        } else if (requestCode == CAMERA) {
            // call setPicture method
            setPicture();
            // resize the image to meet with the API requirement
            selectedImage = resizeImage(selectedImage);
        }

        // set imageView to visible
        imageViewSelected.setVisibility(View.VISIBLE);

    }

    /**
     * This method is used to write the image into a new file
     * @return File the created file from the storage
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        // example taken from https://developer.android.com/training/camera/photobasics.html
        // Create an image file name with current timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        // create the temporary file
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    /**
     * Set the picture from camera to ImageView
     * Example taken from https://developer.android.com/training/camera/photobasics.html
     */
    private void setPicture() {
        // Get the dimensions of the View
        int targetW = imageViewSelected.getWidth();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;

        // Determine how much to scale down the image
        int scaleFactor = photoW/targetW;

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        // set image to imageView
        selectedImage = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        imageViewSelected.setImageBitmap(selectedImage);
    }

    /**
     * Resize image to meet with the API requirement
     *
     * @param image the image that will be resized
     * @return resized image
     */
    private Bitmap resizeImage(Bitmap image) {
        Bitmap output = image;
        // if one of the dimension is greater than 3200px, resize to max 3200px
        if (image.getHeight() > 3200 || image.getWidth() > 3200) {
            if (image.getHeight() > image.getWidth()) {
                output = Bitmap.createScaledBitmap(image,
                        (image.getHeight()/3200)*image.getWidth(),
                        3200, false);
            } else {
                output = Bitmap.createScaledBitmap(image,3200,
                        (image.getWidth()/3200)*image.getHeight(), false);
            }
        }
        return output;
    }

    /**
     * This class is used to send request to the web service
     * @param isHandwriting
     */
    private void uploadToServer(final boolean isHandwriting) {

        selectedImage.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);

        byteArray = byteArrayOutputStream.toByteArray();

        convertedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

        class AsyncTaskUpload extends AsyncTask<Void,Void,String> {

            @Override
            protected void onPreExecute() {

                super.onPreExecute();

                progressDialog = ProgressDialog.show(MainActivity.this,"Image is Uploading","Please Wait",false,false);
            }

            @Override
            protected void onPostExecute(String serverResponse) {

                super.onPostExecute(serverResponse);

                try {
                    JSONObject response = new JSONObject(serverResponse);

                    String output = response.getString("data");

                    textResult.setText(output);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                progressDialog.dismiss();

            }


            @Override
            protected String doInBackground(Void... params) {
                // modified from https://stackoverflow.com/questions/12796579/how-to-send-image-bitmap-to-server-in-android-with-multipart-form-data-json/12796727
                byte[] data = null;
                try {
                    HttpClient httpclient = new DefaultHttpClient();
                    HttpPost httppost = new HttpPost(UPLOAD_PATH);
                    MultipartEntity entity = new MultipartEntity();

                    if(selectedImage!=null){
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        selectedImage.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                        data = bos.toByteArray();
                        entity.addPart("image", new ByteArrayBody(data,"image/jpeg", "test2.jpg"));
                    }
                    entity.addPart("handwriting", new StringBody(isHandwriting + "","text/plain", Charset.forName("UTF-8")));

                    enrichWithTrackingData(entity);

                    httppost.setEntity(entity);
                    HttpResponse resp = httpclient.execute(httppost);
                    HttpEntity resEntity = resp.getEntity();
                    String string = EntityUtils.toString(resEntity);

                    return string;
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return "";
            }
        }

        // upload the file
        AsyncTaskUpload asyncTaskUpload = new AsyncTaskUpload();
        asyncTaskUpload.execute();
    }

    /**
     * Get additional tracking data for analytics
     *
     * @return list of tracking data
     */
    private HashMap<String, String> getTrackingData() {
        HashMap<String, String> trackingData = new HashMap<>();

        trackingData.put("manufacturer", Build.MANUFACTURER);
        trackingData.put("deviceModel", Build.MODEL);
        trackingData.put("osVersion", Build.VERSION.RELEASE);
        trackingData.put("connectionType", getNetworkClass(MainActivity.this));

        return trackingData;
    }

    /**
     * Get used network type of the user.
     *
     * @param context
     * @return
     */
    private static String getNetworkClass(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if(info==null || !info.isConnected())
            return "-"; //not connected
        if(info.getType() == ConnectivityManager.TYPE_WIFI)
            return "WIFI";
        if(info.getType() == ConnectivityManager.TYPE_MOBILE){
            return "Mobile Broadband";
        }
        return "Unknown";
    }

    /**
     * Enrich the request parameter with tracking data
     * @param entity
     */
    private void enrichWithTrackingData(MultipartEntity entity) {
        HashMap<String, String> trackingData = getTrackingData();

        for (String key: trackingData.keySet()) {
            try {
                entity.addPart(key,
                        new StringBody(trackingData.get(key),"text/plain", Charset.forName("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }
}
