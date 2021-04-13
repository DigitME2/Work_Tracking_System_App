package com.admt.barcodereader;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link numberPadFragment.OnNumpadInteractionListener} interface
 * to handle interaction events.
 */
public class numberPadFragment extends Fragment {
    private OnNumpadInteractionListener mOnNumpadInteractionListener;
    private String mBarcodeValue =  "";

    public interface OnNumpadInteractionListener {
        void onBarcodeEntered(String barcodeValue);
    }

    public numberPadFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_number_pad, container, false);

        Button btn0 = (Button)view.findViewById(R.id.numped0);
        btn0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("0");
            }
        });

        Button btn1 = (Button)view.findViewById(R.id.numped1);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("1");
            }
        });

        Button btn2 = (Button)view.findViewById(R.id.numped2);
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("2");
            }
        });

        Button btn3 = (Button)view.findViewById(R.id.numped3);
        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("3");
            }
        });

        Button btn4 = (Button)view.findViewById(R.id.numped4);
        btn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("4");
            }
        });

        Button btn5 = (Button)view.findViewById(R.id.numped5);
        btn5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("5");
            }
        });

        Button btn6 = (Button)view.findViewById(R.id.numped6);
        btn6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("6");
            }
        });

        Button btn7 = (Button)view.findViewById(R.id.numped7);
        btn7.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("7");
            }
        });

        Button btn8 = (Button)view.findViewById(R.id.numped8);
        btn8.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("8");
            }
        });

        Button btn9 = (Button)view.findViewById(R.id.numped9);
        btn9.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToBarcodeValue("9");
            }
        });

        Button btnClear = (Button)view.findViewById(R.id.numpedClear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBarcodeValue = "";
                TextView tvBarcodeValue = getActivity().findViewById(R.id.numpadValue);
                tvBarcodeValue.setText("");

            }
        });

        Button btnEnter = (Button)view.findViewById(R.id.numpedEnter);
        btnEnter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBarcodeValue != "")
                    mOnNumpadInteractionListener.onBarcodeEntered(mBarcodeValue);
                else
                {
                    Toast.makeText(getContext(),
                            "Please enter the barcode number",
                            Toast.LENGTH_LONG)
                            .show();

                }
            }
        });

        return view;
    }

    private void appendToBarcodeValue(String newDigit)
    {
        mBarcodeValue = mBarcodeValue + newDigit;
        TextView tvBarcodeValue = getActivity().findViewById(R.id.numpadValue);
        tvBarcodeValue.setText(mBarcodeValue);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnNumpadInteractionListener) {
            mOnNumpadInteractionListener = (OnNumpadInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mOnNumpadInteractionListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
