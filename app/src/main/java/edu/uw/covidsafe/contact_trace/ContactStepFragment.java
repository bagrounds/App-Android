package edu.uw.covidsafe.contact_trace;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.covidsafe.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Date;
import java.util.List;

import edu.uw.covidsafe.gps.GpsDbModel;
import edu.uw.covidsafe.gps.GpsRecord;
import edu.uw.covidsafe.symptoms.SymptomDbModel;
import edu.uw.covidsafe.symptoms.SymptomUtils;
import edu.uw.covidsafe.symptoms.SymptomsRecord;
import edu.uw.covidsafe.utils.Constants;
import edu.uw.covidsafe.utils.TimeUtils;

public class ContactStepFragment extends Fragment {

    boolean gpsDbChanged = false;
    static List<GpsRecord> changedGpsRecords;

    boolean sympDbChanged = false;
    static List<SymptomsRecord> changedSympRecords;

    boolean humanDbChanged = false;
    static List<HumanRecord> changedHumanRecords;

    GpsHistoryRecyclerViewAdapter2 adapter;
    SymptomSummaryRecyclerViewAdapter2 symptomAdapter;
    HumanSummaryRecyclerViewAdapter humanAdapter;

    View view;

    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.e("state","contact step fragment");
        Log.e("state","null? "+(getArguments()==null));
        for(String k : getArguments().keySet()) {
            Log.e("state", "key "+k);
        }

        Constants.menu.findItem(R.id.mybutton).setVisible(false);

        int pgnum = getArguments().getInt("pgnum");
        Constants.ContactPageNumber = pgnum-1;
        if (pgnum == 0) {
            view = inflater.inflate(R.layout.contract_trace_start, container, false);
            Button bb = (Button)view.findViewById(R.id.submitForm);
            bb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Constants.contactViewPager.setCurrentItem(1);
                }
            });
        }
        else {
            view = inflater.inflate(R.layout.contact_trace_step, container, false);
            ImageView header = view.findViewById(R.id.header);
            header.setImageDrawable(getActivity().getDrawable(getArguments().getInt("image")));

            TextView tt = view.findViewById(R.id.title);
            TextView desc = view.findViewById(R.id.desc);
            TextView header2 = view.findViewById(R.id.header2);

            ImageView pg1 = view.findViewById(R.id.page1);
            ImageView pg2 = view.findViewById(R.id.page2);
            ImageView pg3 = view.findViewById(R.id.page3);
            ImageView pg4 = view.findViewById(R.id.page4);

            Button nextButton = (Button)view.findViewById(R.id.button6);
            Button prevButton = (Button)view.findViewById(R.id.button7);
            FloatingActionButton actionButton = (FloatingActionButton)view.findViewById(R.id.fabButton);

            if (pgnum >= 1||pgnum<=4) {
                prevButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Constants.contactViewPager.setCurrentItem(pgnum-1);
                    }
                });
                if (pgnum!=4) {
                    nextButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Constants.contactViewPager.setCurrentItem(pgnum + 1);
                        }
                    });
                }
                else {
                    nextButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            FragmentTransaction tx = getActivity().getSupportFragmentManager().beginTransaction();
                            tx.setCustomAnimations(
                                    R.anim.exit_right_to_left,R.anim.exit_right_to_left,
                                    R.anim.exit_right_to_left,R.anim.exit_right_to_left);
                            Constants.contactViewPager.setCurrentItem(1);
                            tx.replace(R.id.fragment_container, Constants.HealthFragment).commit();
                        }
                    });
                    prevButton.setVisibility(View.GONE);
                }
            }
            if (pgnum != 3) {
                actionButton.setVisibility(View.GONE);
            }

            if (pgnum == 1) {
                pg1.setImageDrawable(getContext().getDrawable(R.drawable.current_1));
                pg2.setImageDrawable(getContext().getDrawable(R.drawable.todo_2));
                pg3.setImageDrawable(getContext().getDrawable(R.drawable.todo_3));
                pg4.setImageDrawable(getContext().getDrawable(R.drawable.todo_4));
                tt.setText(getContext().getString(R.string.contact_title_1));
                desc.setText(getContext().getString(R.string.contact_desc_1));
                header2.setText("Symptoms");

                RecyclerView tipView = view.findViewById(R.id.recyclerView);
                symptomAdapter = new SymptomSummaryRecyclerViewAdapter2(getActivity(),getActivity(), view);
                tipView.setAdapter(symptomAdapter);
                tipView.setLayoutManager(new LinearLayoutManager(getActivity()));

                SymptomDbModel smodel = ViewModelProviders.of(getActivity()).get(SymptomDbModel.class);
                smodel.getAllSorted().observe(getActivity(), new Observer<List<SymptomsRecord>>() {
                    @Override
                    public void onChanged(List<SymptomsRecord> symptomRecords) {
                        //something in db has changed, update
                        sympDbChanged = true;
                        changedSympRecords = symptomRecords;
                        Constants.symptomRecords = symptomRecords;
                        Log.e("symptom","mainfragment - symptom list changed");
                        if (Constants.CurrentFragment.toString().toLowerCase().contains("mainfragment")) {
                            Log.e("symptom","mainfragment - symptom list changing");
                            symptomAdapter.setRecords(symptomRecords);
                            sympDbChanged = false;
                        }
                    }
                });
            }
            else if (pgnum == 2) {
                pg1.setImageDrawable(getContext().getDrawable(R.drawable.done_1));
                pg2.setImageDrawable(getContext().getDrawable(R.drawable.current_2));
                pg3.setImageDrawable(getContext().getDrawable(R.drawable.todo_3));
                pg4.setImageDrawable(getContext().getDrawable(R.drawable.todo_4));
                tt.setText(getContext().getString(R.string.contact_title_2));
                desc.setText(getContext().getString(R.string.contact_desc_2));
                header2.setText("General Locations");

                adapter = new GpsHistoryRecyclerViewAdapter2(getContext(), getActivity(), view);
                RecyclerView tipView = view.findViewById(R.id.recyclerView);
                tipView.setAdapter(adapter);
                tipView.setLayoutManager(new LinearLayoutManager(getActivity()));

                GpsDbModel smodel = ViewModelProviders.of(getActivity()).get(GpsDbModel.class);
                smodel.getAllSorted().observe(getActivity(), new Observer<List<GpsRecord>>() {
                    @Override
                    public void onChanged(List<GpsRecord> gpsRecords) {
                        //something in db has changed, update
                        gpsDbChanged = true;
                        changedGpsRecords = gpsRecords;
                        Log.e("contact","db on changed "+(changedGpsRecords.size()));
                        if (Constants.CurrentFragment.toString().toLowerCase().contains("contactstep")) {
                            Log.e("contact","db on changing");
                            adapter.setRecords(changedGpsRecords, getContext());
                            gpsDbChanged = false;
                        }
                    }
                });
            }
            else if (pgnum == 3) {
                pg1.setImageDrawable(getContext().getDrawable(R.drawable.done_1));
                pg2.setImageDrawable(getContext().getDrawable(R.drawable.done_2));
                pg3.setImageDrawable(getContext().getDrawable(R.drawable.current_3));
                pg4.setImageDrawable(getContext().getDrawable(R.drawable.todo_4));
                tt.setText(getContext().getString(R.string.contact_title_3));
                desc.setText(getContext().getString(R.string.contact_desc_3));
                header2.setText("People");
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
                            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 0);
                        }
                        else {
                            Intent pickContact = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                            pickContact.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                            getActivity().startActivityForResult(pickContact, 2);
                        }
                    }
                });

                humanAdapter = new HumanSummaryRecyclerViewAdapter(getContext(), getActivity(), view);
                RecyclerView tipView = view.findViewById(R.id.recyclerView);
                tipView.setAdapter(humanAdapter);
                tipView.setLayoutManager(new LinearLayoutManager(getActivity()));

                HumanDbModel smodel = ViewModelProviders.of(getActivity()).get(HumanDbModel.class);
                smodel.getAllSorted().observe(getActivity(), new Observer<List<HumanRecord>>() {
                    @Override
                    public void onChanged(List<HumanRecord> humanRecords) {
                        Log.e("human","onchanged");
                        humanDbChanged = true;
                        changedHumanRecords = humanRecords;
                        if (Constants.CurrentFragment.toString().toLowerCase().contains("contactstep")) {
                            humanAdapter.setRecords(humanRecords);
                            humanDbChanged = false;
                        }
                    }
                });
            }
            else if (pgnum == 4) {
                pg1.setImageDrawable(getContext().getDrawable(R.drawable.done_1));
                pg2.setImageDrawable(getContext().getDrawable(R.drawable.done_2));
                pg3.setImageDrawable(getContext().getDrawable(R.drawable.done_3));
                pg4.setImageDrawable(getContext().getDrawable(R.drawable.current_4));
                tt.setText(getContext().getString(R.string.contact_title_4));
                desc.setText(getContext().getString(R.string.contact_desc_4));
                nextButton.setText("Save");
                header2.setText("");
            }
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Constants.CurrentFragment = this;

        if (gpsDbChanged) {
            adapter.setRecords(changedGpsRecords, getContext());
            gpsDbChanged = false;
        }
        if (sympDbChanged) {
            symptomAdapter.setRecords(changedSympRecords);
            sympDbChanged = false;
        }
        if (humanDbChanged) {
            humanAdapter.setRecords(changedHumanRecords);
            humanDbChanged = false;
        }
    }
}
