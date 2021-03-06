/*
 * Copyright (C) 2016 Actinarium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actinarium.kinetic.ui;


import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.actinarium.kinetic.R;
import com.actinarium.kinetic.pipeline.DataRecorder;
import com.actinarium.kinetic.pipeline.DataTransformer;
import com.actinarium.kinetic.util.DataSet3;
import com.actinarium.kinetic.util.DataSet4;

/**
 * A fragment for welcome screen with record button. Since it's the only button on the screen, we can avoid anonymous
 * classes and make the fragment a listener for the button itself
 */
public class RecordFragment extends Fragment implements View.OnClickListener, DataRecorder.Callback {

    public static final String TAG = "RecordFragment";

    private Host mHost;
    private DataRecorder mRecorder;
    private boolean mIsRecording;

    private FloatingActionButton mRecordButton;
    private Drawable mProgress;
    private ObjectAnimator mAnimator;

    public RecordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mHost = (Host) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_welcome, container, false);

        mRecordButton = (FloatingActionButton) view.findViewById(R.id.record);
        mRecordButton.setOnClickListener(this);
        FrameLayout fabHolder = (FrameLayout) view.findViewById(R.id.fab_holder);
        mProgress = fabHolder.getForeground();
        mProgress.setLevel(0);

        mRecorder = new DataRecorder(getContext(), this, DataRecorder.DEFAULT_RECORDING_TIME_MILLIS, DataRecorder.DEFAULT_SAMPLING_MICROS);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mRecorder.startListening();
    }

    @Override
    public void onStop() {
        super.onPause();
        mRecorder.stop();
    }

    @Override
    public void onClick(View v) {
        if (!mIsRecording) {
            // Start recording
            mIsRecording = true;
            mRecordButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            mRecorder.startRecording();

            mAnimator = ObjectAnimator.ofInt(mProgress, "level", 0, 10000)
                    .setDuration(DataRecorder.DEFAULT_RECORDING_TIME_MILLIS);
            mAnimator.setInterpolator(new LinearInterpolator());
            mAnimator.start();
        } else {
            // End animation quickly
            mAnimator.end();

            // Stop recording - the drawable and the boolean will be updated in a callback method
            mRecorder.stop();
        }
    }

    @Override
    public void onDataRecordedResult(@DataRecorder.Status int status, DataSet3 accelData, DataSet3 gyroData,
                                     DataSet4 rotVectorData, float[] gravity) {
        mIsRecording = false;
        mRecordButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_record));

        if (status == DataRecorder.STATUS_FAILURE_NO_SENSOR) {
            Toast.makeText(getContext(), R.string.sensor_error, Toast.LENGTH_LONG).show();
        } else if (status < 0) {
            Toast.makeText(getContext(), R.string.app_error, Toast.LENGTH_LONG).show();
        } else {
            // Try to remove gravity from raw readings
            DataTransformer.removeGravityFromRaw(accelData, rotVectorData, accelData, gravity);

            // Integrate raw readings:

            // acceleration -> velocity
            DataTransformer.integrate(accelData);
            // velocity -> offset
            DataTransformer.integrate(accelData);

            // angular velocity -> phase
            DataTransformer.integrate(gyroData);

            mHost.onDataRecorded(accelData, gyroData, rotVectorData);
        }
    }

    public interface Host {
        void onDataRecorded(DataSet3 accelData, DataSet3 gyroData, DataSet4 rotVectorData);
    }
}
