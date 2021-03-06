package com.klcn.xuant.transporter;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class DriverInvitesActivity extends AppCompatActivity{

    @BindView(R.id.txt_invite_code)
    TextView mTxtInviteCode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_invites);
        ButterKnife.bind(this);

        String driverID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mTxtInviteCode.setText(driverID.substring(0,10));

    }


}
