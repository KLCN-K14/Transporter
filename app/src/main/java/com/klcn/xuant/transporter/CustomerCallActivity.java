package com.klcn.xuant.transporter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.github.lzyzsd.circleprogress.ArcProgress;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.klcn.xuant.transporter.common.Common;
import com.klcn.xuant.transporter.helper.ArcProgressAnimation;
import com.klcn.xuant.transporter.model.Driver;
import com.klcn.xuant.transporter.model.FCMResponse;
import com.klcn.xuant.transporter.model.Notification;
import com.klcn.xuant.transporter.model.PickupRequest;
import com.klcn.xuant.transporter.model.RideInfo;
import com.klcn.xuant.transporter.model.Sender;
import com.klcn.xuant.transporter.model.Token;
import com.klcn.xuant.transporter.remote.IFCMService;
import com.klcn.xuant.transporter.remote.IGoogleAPI;
import com.skyfishjy.library.RippleBackground;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class CustomerCallActivity extends AppCompatActivity implements View.OnClickListener{

    @BindView(R.id.txt_your_destination)
    TextView mTxtYourDestination;

    @BindView(R.id.txt_price)
    TextView mTxtPrice;

    @BindView(R.id.txt_distance)
    TextView mTxtDistance;

    @BindView(R.id.txt_time)
    TextView mTxtTime;

    @BindView(R.id.btn_accept_pickup_request)
    Button mBtnAccept;

    @BindView(R.id.btn_cancel_find_driver)
    Button mBtnCancel;

    @BindView(R.id.arc_progress)
    ArcProgress mArcProgress;

    IGoogleAPI mService;

    MediaPlayer mediaPlayer;
    double lat,lng;
    String destination;
    String customerId;

    IFCMService mFCMService;
    private BroadcastReceiver mReceiver;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/Arkhip_font.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build());
        setContentView(R.layout.activity_customer_call);
        ButterKnife.bind(this);
        mFCMService = Common.getFCMService();

        mediaPlayer = MediaPlayer.create(getApplicationContext(),R.raw.notification);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();

        mArcProgress.setMax(15);
        ArcProgressAnimation anim = new ArcProgressAnimation(mArcProgress, 0, 15);
        anim.setDuration(15000);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                addDriverAvailable();
                finish();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mArcProgress.startAnimation(anim);

        removeDriverAvailable();
        mService = Common.getGoogleAPI();
        if(getIntent()!=null){
             lat = Double.parseDouble(getIntent().getStringExtra("lat").toString());
             lng = Double.parseDouble(getIntent().getStringExtra("lng").toString());
             destination = getIntent().getStringExtra("destination");
             customerId = getIntent().getStringExtra("customerId");
            getDirection(lat,lng);
        }

        mBtnAccept.setOnClickListener(this);
        mBtnCancel.setOnClickListener(this);
    }

    public void removeDriverAvailable() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(Common.driver_available_tbl);

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId, new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {
            }
        });
    }

    public void addDriverAvailable() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(Common.driver_available_tbl);

        GeoFire geoFire = new GeoFire(ref);
        geoFire.setLocation(userId,
                new GeoLocation( Common.mLastLocationDriver.getLatitude(),  Common.mLastLocationDriver.getLongitude()), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                        //Add marker
                    }
                });
    }

    private void getDirection(double lat, double lng) {
        String requestApi = null;
        try{
            requestApi = "https://maps.googleapis.com/maps/api/directions/json?"+
                    "mode=driving&"+
                    "transit_routing_preference=less_driving&"+
                    "origin="+ Common.mLastLocationDriver.getLatitude()+","+Common.mLastLocationDriver.getLongitude()+"&"+
                    "destination="+lat+","+lng+"&"+
                    "key="+getResources().getString(R.string.google_direction_api);
            Log.d("TRANSPORT",requestApi);
            mService.getPath(requestApi)
                    .enqueue(new Callback<String>() {
                        @Override
                        public void onResponse(Call<String> call, Response<String> response) {
                            try {
                                JSONObject jsonObject = new JSONObject(response.body().toString());
                                JSONArray routes = jsonObject.getJSONArray("routes");

                                JSONObject object = routes.getJSONObject(0);

                                JSONArray legs = object.getJSONArray("legs");

                                JSONObject legsObject = legs.getJSONObject(0);

                                JSONObject distace = legsObject.getJSONObject("distance");
                                mTxtDistance.setText(distace.getString("text"));

                                Double price = (Double.valueOf(mTxtDistance.getText().toString().replaceAll("[^0-9.,]+",""))*8);
                                mTxtPrice.setText("VND "+String.valueOf(price.intValue())+"K");

                                JSONObject time = legsObject.getJSONObject("duration");
                                mTxtTime.setText(time.getString("text"));

                                String address = legsObject.getString("end_address");
                                mTxtYourDestination.setText(address);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(Call<String> call, Throwable t) {

                        }
                    });

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_cancel_find_driver:
                sendMessageCancelRequest();
                addDriverAvailable();
                finish();
            break;

            case R.id.btn_accept_pickup_request:
                DatabaseReference pickupRequest = FirebaseDatabase.getInstance().getReference(Common.pickup_request_tbl);
                PickupRequest pickupRequest1 = new PickupRequest();
                pickupRequest1.setLatPickup(String.valueOf(lat));
                pickupRequest1.setLngPickup(String.valueOf(lng));
                pickupRequest1.setDestination(destination);
                pickupRequest1.setCustomerId(customerId);
                pickupRequest.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                        .setValue(pickupRequest1)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                finish();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull final Exception e) {
                                e.printStackTrace();
                                // send cancel to customer
                            }
                        });

                break;
        }
    }

    private void sendMessageCancelRequest() {
        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference(Common.tokens_tbl);

        tokens.orderByKey().equalTo(customerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for(DataSnapshot postData: dataSnapshot.getChildren()){
                            Token token = postData.getValue(Token.class);
                            Notification notification = new Notification("Cancel","Your driver denied your request!");
                            Sender sender = new Sender(token.getToken(),notification);
                            mFCMService.sendMessage(sender).enqueue(new Callback<FCMResponse>() {
                                @Override
                                public void onResponse(Call<FCMResponse> call, Response<FCMResponse> response) {
                                    if(response.body().success == 1){
                                        Log.e("MessageCancelRequest","Success");
                                    }
                                    else{
                                        Toast.makeText(getApplicationContext(),"Failed",Toast.LENGTH_LONG).show();
                                        Log.e("MessageCancelRequest",response.message());
                                        Log.e("MessageCancelRequest",response.errorBody().toString());
                                    }
                                }

                                @Override
                                public void onFailure(Call<FCMResponse> call, Throwable t) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }


    @Override
    protected void onResume() {
        mediaPlayer.start();
        IntentFilter intentFilter = new IntentFilter(
                "android.intent.action.MAIN");

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //extract our message from intent
                if(intent.getStringExtra("CancelTrip")!=null){
                    mBtnAccept.setEnabled(false);
                    mBtnCancel.setEnabled(false);
                    Toast.makeText(getApplicationContext(),"Customer cancel request!!!",Toast.LENGTH_LONG).show();

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    },2000);
                }
            }
        };
        //registering our receiver
        this.registerReceiver(mReceiver, intentFilter);
        super.onResume();
    }

    @Override
    protected void onStop() {
        mediaPlayer.release();
        this.unregisterReceiver(mReceiver);
        super.onStop();
    }

    @Override
    protected void onPause() {
        mediaPlayer.release();
        super.onPause();
    }
}
